package io.github.azhardev77.tenderengine.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import io.github.azhardev77.tenderengine.domain.entity.TenderDocumentEntity;
import io.github.azhardev77.tenderengine.domain.entity.TenderDocumentEntity.DocType;
import io.github.azhardev77.tenderengine.domain.entity.TenderDocumentEntity.ProcessingStatus;
import io.github.azhardev77.tenderengine.domain.repository.TenderDocumentRepository;
import io.github.azhardev77.tenderengine.service.hash.ContentFingerprintService;
import io.github.azhardev77.tenderengine.service.hash.ContentFingerprintService.FileFingerprint;
import io.github.azhardev77.tenderengine.service.pdf.PdfBoxPageExtractor;
import io.github.azhardev77.tenderengine.service.pdf.PdfBoxPageExtractor.ExtractedPage;

@Service
public class TenderIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TenderIngestionService.class);

    private final ContentFingerprintService fingerprintService;
    private final PdfBoxPageExtractor pageExtractor;
    private final TenderDocumentRepository documentRepo;
    private final TenderPersistenceService persistence;
    private final ExecutorService pdfExecutor;

    public TenderIngestionService(ContentFingerprintService fingerprintService,
                                  PdfBoxPageExtractor pageExtractor,
                                  TenderDocumentRepository documentRepo,
                                  TenderPersistenceService persistence,
                                  @Qualifier("pdfExecutor") ExecutorService pdfExecutor) {
        this.fingerprintService = fingerprintService;
        this.pageExtractor = pageExtractor;
        this.documentRepo = documentRepo;
        this.persistence = persistence;
        this.pdfExecutor = pdfExecutor;
    }

    public record IngestionResult(UUID id, ProcessingStatus status, boolean duplicate) {}

    public IngestionResult ingest(MultipartFile file, DocType docType, UUID parentTenderId) throws Exception {
        FileFingerprint fp = fingerprintService.fingerprint(file);
        Path tempPath = fp.tempPath();

        try {
            TenderDocumentEntity existing = documentRepo.findByFileSha256(fp.sha256()).orElse(null);
            if (existing != null) {
                Files.deleteIfExists(tempPath);
                log.info("Duplicate upload detected: sha256={}", fp.sha256());
                return new IngestionResult(existing.getId(), existing.getProcessingStatus(), true);
            }

            TenderDocumentEntity doc = persistence.createDocument(
                    file.getOriginalFilename(), docType, parentTenderId, fp);
            scheduleExtraction(doc.getId(), tempPath);
            return new IngestionResult(doc.getId(), ProcessingStatus.UPLOADED, false);
        } catch (Exception e) {
            Files.deleteIfExists(tempPath);
            throw e;
        }
    }

    private void scheduleExtraction(UUID documentId, Path tempPath) {
        pdfExecutor.submit(() -> {
            try {
                extractAndPersist(documentId, tempPath);
            } catch (Exception e) {
                log.error("PDF extraction failed for document {}", documentId, e);
                persistence.markFailed(documentId, e.getMessage());
            } finally {
                try { Files.deleteIfExists(tempPath); } catch (Exception ignored) {}
            }
        });
    }

    private void extractAndPersist(UUID documentId, Path tempPath) throws Exception {
        long start = System.currentTimeMillis();
        persistence.updateStatus(documentId, ProcessingStatus.EXTRACTING_PAGES);

        List<ExtractedPage> pages = pageExtractor.extract(tempPath);
        persistence.savePages(documentId, pages);

        List<String> pageTexts = pages.stream().map(ExtractedPage::text).toList();
        String normalizedHash = fingerprintService.normalizedTextHash(pageTexts);
        long durationMs = System.currentTimeMillis() - start;

        persistence.completeProcessing(documentId, pages.size(), normalizedHash, durationMs);
        log.info("Ingestion completed for document {}: {} pages in {}ms",
                documentId, pages.size(), durationMs);
    }
}
