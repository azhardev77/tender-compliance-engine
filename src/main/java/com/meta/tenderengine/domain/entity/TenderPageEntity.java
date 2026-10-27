package com.meta.tenderengine.domain.entity;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tender_pages")
public class TenderPageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tender_id", nullable = false)
    private UUID tenderId;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "is_scanned", nullable = false)
    private boolean scanned;

    @Column(name = "has_dense_tables", nullable = false)
    private boolean hasDenseTables;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sam_regions", nullable = false, columnDefinition = "jsonb")
    private List<Object> samRegions = List.of();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenderId() { return tenderId; }
    public void setTenderId(UUID tenderId) { this.tenderId = tenderId; }

    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public boolean isScanned() { return scanned; }
    public void setScanned(boolean scanned) { this.scanned = scanned; }

    public boolean isHasDenseTables() { return hasDenseTables; }
    public void setHasDenseTables(boolean hasDenseTables) { this.hasDenseTables = hasDenseTables; }

    public List<Object> getSamRegions() { return samRegions; }
    public void setSamRegions(List<Object> samRegions) { this.samRegions = samRegions; }
}
