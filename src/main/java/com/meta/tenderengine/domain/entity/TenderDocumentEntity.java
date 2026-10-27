package com.meta.tenderengine.domain.entity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tender_documents")
public class TenderDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false)
    private DocType docType = DocType.MAIN_TENDER;

    @Column(name = "parent_tender_id")
    private UUID parentTenderId;

    @Column(name = "file_sha256", nullable = false, unique = true, length = 64)
    private String fileSha256;

    @Column(name = "normalized_text_sha256", length = 64)
    private String normalizedTextSha256;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false)
    private ProcessingStatus processingStatus = ProcessingStatus.UPLOADED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "document_metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> documentMetadata = Map.of();

    @Column(name = "processing_duration_ms")
    private Long processingDurationMs;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public DocType getDocType() { return docType; }
    public void setDocType(DocType docType) { this.docType = docType; }

    public UUID getParentTenderId() { return parentTenderId; }
    public void setParentTenderId(UUID parentTenderId) { this.parentTenderId = parentTenderId; }

    public String getFileSha256() { return fileSha256; }
    public void setFileSha256(String fileSha256) { this.fileSha256 = fileSha256; }

    public String getNormalizedTextSha256() { return normalizedTextSha256; }
    public void setNormalizedTextSha256(String normalizedTextSha256) { this.normalizedTextSha256 = normalizedTextSha256; }

    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }

    public ProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(ProcessingStatus processingStatus) { this.processingStatus = processingStatus; }

    public Map<String, Object> getDocumentMetadata() { return documentMetadata; }
    public void setDocumentMetadata(Map<String, Object> documentMetadata) { this.documentMetadata = documentMetadata; }

    public Long getProcessingDurationMs() { return processingDurationMs; }
    public void setProcessingDurationMs(Long processingDurationMs) { this.processingDurationMs = processingDurationMs; }

    public Instant getCreatedAt() { return createdAt; }

    public enum DocType { MAIN_TENDER, CORRIGENDUM }

    public enum ProcessingStatus {
        UPLOADED, EXTRACTING_PAGES, SEGMENTING_LAYOUT, EVALUATING_CLAUSES, COMPLETED, FAILED
    }
}
