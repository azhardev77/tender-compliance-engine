package com.meta.tenderengine.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.meta.tenderengine.domain.entity.TenderDocumentEntity;
import com.meta.tenderengine.domain.entity.TenderDocumentEntity.DocType;
import com.meta.tenderengine.domain.repository.TenderDocumentRepository;
import com.meta.tenderengine.service.TenderIngestionService;
import com.meta.tenderengine.service.TenderIngestionService.IngestionResult;

@RestController
@RequestMapping("/api/tenders")
public class TenderIngestionController {

    private static final Logger log = LoggerFactory.getLogger(TenderIngestionController.class);

    /** First 5 bytes of every valid PDF file. */
    private static final byte[] PDF_MAGIC = { '%', 'P', 'D', 'F', '-' };

    private final TenderIngestionService ingestionService;
    private final TenderDocumentRepository documentRepo;

    public TenderIngestionController(TenderIngestionService ingestionService,
                                     TenderDocumentRepository documentRepo) {
        this.ingestionService = ingestionService;
        this.documentRepo = documentRepo;
    }

    public record UploadResponse(UUID id, String status, boolean duplicate) {}

    public record StatusResponse(UUID id, String fileName, String docType, String status,
                                 int pageCount, Long processingDurationMs) {}

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "docType", defaultValue = "MAIN_TENDER") DocType docType,
            @RequestParam(name = "parentTenderId", required = false) UUID parentTenderId) throws Exception {

        validatePdfMagicBytes(file);
        validateCorrigendumParent(docType, parentTenderId);

        IngestionResult result = ingestionService.ingest(file, docType, parentTenderId);

        UploadResponse body = new UploadResponse(result.id(), result.status().name(), result.duplicate());
        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(body);
    }

    @GetMapping("/{id}/status")
    public StatusResponse status(@PathVariable UUID id) {
        TenderDocumentEntity doc = documentRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tender not found"));
        return new StatusResponse(doc.getId(), doc.getFileName(), doc.getDocType().name(),
                doc.getProcessingStatus().name(), doc.getPageCount(), doc.getProcessingDurationMs());
    }

    private void validatePdfMagicBytes(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(PDF_MAGIC.length);
            if (header.length < PDF_MAGIC.length) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File too small to be a PDF");
            }
            for (int i = 0; i < PDF_MAGIC.length; i++) {
                if (header[i] != PDF_MAGIC[i]) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Not a PDF file (invalid magic bytes)");
                }
            }
        }
    }

    private void validateCorrigendumParent(DocType docType, UUID parentTenderId) {
        if (docType == DocType.CORRIGENDUM) {
            if (parentTenderId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "parentTenderId is required for CORRIGENDUM");
            }
            TenderDocumentEntity parent = documentRepo.findById(parentTenderId).orElse(null);
            if (parent == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Parent tender not found: " + parentTenderId);
            }
            if (parent.getDocType() != DocType.MAIN_TENDER) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Parent must be a MAIN_TENDER, not " + parent.getDocType());
            }
        }
    }
}
