package io.github.azhardev77.tenderengine.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.azhardev77.tenderengine.domain.entity.TenderDocumentEntity;
import io.github.azhardev77.tenderengine.domain.entity.TenderDocumentEntity.DocType;
import io.github.azhardev77.tenderengine.domain.entity.TenderDocumentEntity.ProcessingStatus;
import io.github.azhardev77.tenderengine.domain.entity.TenderPageEntity;
import io.github.azhardev77.tenderengine.domain.repository.TenderDocumentRepository;
import io.github.azhardev77.tenderengine.domain.repository.TenderPageRepository;
import io.github.azhardev77.tenderengine.service.hash.ContentFingerprintService.FileFingerprint;
import io.github.azhardev77.tenderengine.service.pdf.PdfBoxPageExtractor.ExtractedPage;

/**
 * Owns every short transactional write for ingestion.
 * Extracted from TenderIngestionService so that Spring's proxy
 * actually intercepts the @Transactional boundaries.
 */
@Service
public class TenderPersistenceService {

    private final TenderDocumentRepository documentRepo;
    private final TenderPageRepository pageRepo;

    public TenderPersistenceService(TenderDocumentRepository documentRepo,
                                    TenderPageRepository pageRepo) {
        this.documentRepo = documentRepo;
        this.pageRepo = pageRepo;
    }

    @Transactional
    public TenderDocumentEntity createDocument(String fileName, DocType docType,
                                               UUID parentTenderId, FileFingerprint fp) {
        TenderDocumentEntity doc = new TenderDocumentEntity();
        doc.setId(UUID.randomUUID());
        doc.setFileName(fileName != null ? fileName : "unnamed.pdf");
        doc.setDocType(docType);
        doc.setParentTenderId(parentTenderId);
        doc.setFileSha256(fp.sha256());
        doc.setDocumentMetadata(Map.of("sizeBytes", fp.sizeBytes()));

        String metadataJson = "{\"sizeBytes\":" + fp.sizeBytes() + "}";
        documentRepo.insertOnConflictDoNothing(doc, metadataJson);

        return documentRepo.findByFileSha256(fp.sha256()).orElseThrow();
    }

    @Transactional
    public void updateStatus(UUID documentId, ProcessingStatus status) {
        TenderDocumentEntity doc = documentRepo.findById(documentId).orElseThrow();
        doc.setProcessingStatus(status);
        documentRepo.save(doc);
    }

    @Transactional
    public void savePages(UUID documentId, List<ExtractedPage> pages) {
        List<TenderPageEntity> entities = pages.stream().map(page -> {
            TenderPageEntity entity = new TenderPageEntity();
            entity.setTenderId(documentId);
            entity.setPageNumber(page.pageNumber());
            entity.setRawText(page.text());
            entity.setScanned(page.scanned());
            entity.setHasDenseTables(page.hasDenseTables());
            return entity;
        }).toList();
        pageRepo.saveAll(entities);
    }

    @Transactional
    public void completeProcessing(UUID documentId, int pageCount,
                                   String normalizedHash, long durationMs) {
        TenderDocumentEntity doc = documentRepo.findById(documentId).orElseThrow();
        doc.setPageCount(pageCount);
        doc.setNormalizedTextSha256(normalizedHash);
        doc.setProcessingDurationMs(durationMs);
        doc.setProcessingStatus(ProcessingStatus.COMPLETED);
        documentRepo.save(doc);
    }

    @Transactional
    public void markFailed(UUID documentId, String errorMessage) {
        TenderDocumentEntity doc = documentRepo.findById(documentId).orElseThrow();
        doc.setProcessingStatus(ProcessingStatus.FAILED);
        Map<String, Object> metadata = new LinkedHashMap<>(doc.getDocumentMetadata());
        metadata.put("error", errorMessage != null ? errorMessage : "unknown");
        doc.setDocumentMetadata(metadata);
        documentRepo.save(doc);
    }
}
