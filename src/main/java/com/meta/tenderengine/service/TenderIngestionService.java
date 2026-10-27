package com.meta.tenderengine.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.meta.tenderengine.domain.entity.TenderDocumentEntity;
import com.meta.tenderengine.domain.entity.TenderDocumentEntity.DocType;
import com.meta.tenderengine.domain.entity.TenderDocumentEntity.ProcessingStatus;
import com.meta.tenderengine.domain.entity.TenderPageEntity;
import com.meta.tenderengine.domain.repository.TenderDocumentRepository;
import com.meta.tenderengine.domain.repository.TenderPageRepository;
import com.meta.tenderengine.service.hash.ContentFingerprintService;
import com.meta.tenderengine.service.hash.ContentFingerprintService.FileFingerprint;
import com.meta.tenderengine.service.pdf.PdfBoxPageExtractor;
import com.meta.tenderengine.service.pdf.PdfBoxPageExtractor.ExtractedPage;

@Service
public class TenderIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TenderIngestionService.class);

    private final ContentFingerprintService fingerprintService;
    private final PdfBoxPageExtractor pageExtractor;
    private final TenderDocumentRepository documentRepo;
    private final TenderPageRepository pageRepo;
    private final ExecutorService pdfExecutor;

    public TenderIngestionService(ContentFingerprintService fingerprintService,
                                  PdfBoxPageExtractor pageExtractor,
                                  TenderDocumentRepository documentRepo,
                                  TenderPageRepository pageRepo,
                                  @Qualifier("pdfExecutor") ExecutorService pdfExecutor) {
        this.fingerprintService = fingerprintService;
        this.pageExtractor = pageExtractor;
        this.documentRepo = documentRepo;
        this.pageRepo = pageRepo;
        this.pdfExecutor = pdfExecutor;
    }

    public record IngestionResult(UUID id, ProcessingStatus status, boolean duplicate) {}

    public IngestionResult ingest(MultipartFile file, DocType docType, UUID parentTenderId) throws Exception {
        FileFingerprint fp = fingerprintService.fingerprint(file);
        Path tempPath = fp.tempPath();

        try {
            TenderDocumentEntity existing = documentRepo.findByFileSha256(fp.sha256()).orElse(null);
            if (existing != null) {
                log.info("Duplicate upload detected: sha256={}", fp.sha256());
                return new IngestionResult(existing.getId(), existing.getProcessingStatus(), true);
            }

            TenderDocumentEntity doc = createDocument(file.getOriginalFilename(), docType, parentTenderId, fp);
            scheduleExtraction(doc.getId(), tempPath);
            return new IngestionResult(doc.getId(), ProcessingStatus.UPLOADED, false);
        } catch (Exception e) {
            Files.deleteIfExists(tempPath);
            throw e;
        }
    }

    @Transactional
    protected TenderDocumentEntity createDocument(String fileName, DocType docType,
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

        // Re-fetch: if another thread raced us, we get their row
        TenderDocumentEntity persisted = documentRepo.findByFileSha256(fp.sha256()).orElseThrow();
        return persisted;
    }

    private void scheduleExtraction(UUID documentId, Path tempPath) {
        pdfExecutor.submit(() -> {
            try {
                extractAndPersist(documentId, tempPath);
            } catch (Exception e) {
                log.error("PDF extraction failed for document {}", documentId, e);
                markFailed(documentId, e.getMessage());
            } finally {
                try { Files.deleteIfExists(tempPath); } catch (Exception ignored) {}
            }
        });
    }

    private void extractAndPersist(UUID documentId, Path tempPath) throws Exception {
        long start = System.currentTimeMillis();
        updateStatus(documentId, ProcessingStatus.EXTRACTING_PAGES);

        List<ExtractedPage> pages = pageExtractor.extract(tempPath);
        savePages(documentId, pages);

        List<String> pageTexts = pages.stream().map(ExtractedPage::text).toList();
        String normalizedHash = fingerprintService.normalizedTextHash(pageTexts);

        completeProcessing(documentId, pages.size(), normalizedHash, System.currentTimeMillis() - start);
        log.info("Ingestion completed for document {}: {} pages in {}ms",
                documentId, pages.size(), System.currentTimeMillis() - start);
    }

    @Transactional
    protected void updateStatus(UUID documentId, ProcessingStatus status) {
        TenderDocumentEntity doc = documentRepo.findById(documentId).orElseThrow();
        doc.setProcessingStatus(status);
        documentRepo.save(doc);
    }

    @Transactional
    protected void savePages(UUID documentId, List<ExtractedPage> pages) {
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
    protected void completeProcessing(UUID documentId, int pageCount,
                                      String normalizedHash, long durationMs) {
        TenderDocumentEntity doc = documentRepo.findById(documentId).orElseThrow();
        doc.setPageCount(pageCount);
        doc.setNormalizedTextSha256(normalizedHash);
        doc.setProcessingDurationMs(durationMs);
        doc.setProcessingStatus(ProcessingStatus.COMPLETED);
        documentRepo.save(doc);
    }

    @Transactional
    protected void markFailed(UUID documentId, String errorMessage) {
        TenderDocumentEntity doc = documentRepo.findById(documentId).orElseThrow();
        doc.setProcessingStatus(ProcessingStatus.FAILED);
        Map<String, Object> metadata = new LinkedHashMap<>(doc.getDocumentMetadata());
        metadata.put("error", errorMessage != null ? errorMessage : "unknown");
        doc.setDocumentMetadata(metadata);
        documentRepo.save(doc);
    }
}
