-- Company-side thresholds. One row per (code, version) so a standards update never overwrites history.
CREATE TABLE internal_compliance_standards (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    standard_code     VARCHAR(64) NOT NULL,
    category          VARCHAR(48) NOT NULL CHECK (category IN (
                          'FINANCIAL_TURNOVER', 'EARNEST_MONEY_DEPOSIT', 'ISO_CERTIFICATION',
                          'PAST_EXPERIENCE', 'LIQUIDATED_DAMAGES', 'NET_WORTH')),
    comparator        VARCHAR(16) NOT NULL CHECK (comparator IN (
                          'MIN_REQUIRED', 'MAX_ALLOWED', 'EXACT_MATCH', 'REQUIRED_SET')),
    numeric_threshold NUMERIC(18, 2),
    unit              VARCHAR(24) NOT NULL,   -- INR_CRORE, INR_LAKH, YEARS, PERCENT, BOOLEAN
    allowed_values    JSONB NOT NULL DEFAULT '[]'::jsonb,
    standards_version INT NOT NULL DEFAULT 1,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (standard_code, standards_version)
);

-- Main tenders and corrigenda share one table; a corrigendum points at its parent.
CREATE TABLE tender_documents (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name              VARCHAR(255) NOT NULL,
    doc_type               VARCHAR(24) NOT NULL DEFAULT 'MAIN_TENDER'
                               CHECK (doc_type IN ('MAIN_TENDER', 'CORRIGENDUM')),
    parent_tender_id       UUID REFERENCES tender_documents(id) ON DELETE CASCADE,
    file_sha256            VARCHAR(64) NOT NULL UNIQUE,  -- UNIQUE already creates the B-tree index
    normalized_text_sha256 VARCHAR(64),
    page_count             INT NOT NULL DEFAULT 0,
    processing_status      VARCHAR(24) NOT NULL DEFAULT 'UPLOADED'
                               CHECK (processing_status IN ('UPLOADED', 'EXTRACTING_PAGES', 'SEGMENTING_LAYOUT',
                                                            'EVALUATING_CLAUSES', 'COMPLETED', 'FAILED')),
    document_metadata      JSONB NOT NULL DEFAULT '{}'::jsonb,
    processing_duration_ms BIGINT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tender_norm_text_sha256 ON tender_documents (normalized_text_sha256);
CREATE INDEX idx_tender_parent_id ON tender_documents (parent_tender_id);

-- Page text lives here, not in JSONB, to keep GIN indexes small.
CREATE TABLE tender_pages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tender_id        UUID NOT NULL REFERENCES tender_documents(id) ON DELETE CASCADE,
    page_number      INT NOT NULL,
    raw_text         TEXT NOT NULL,
    is_scanned       BOOLEAN NOT NULL DEFAULT FALSE,
    has_dense_tables BOOLEAN NOT NULL DEFAULT FALSE,
    sam_regions      JSONB NOT NULL DEFAULT '[]'::jsonb,
    UNIQUE (tender_id, page_number)
);

-- A cached verdict is valid only for the exact standards/model/prompt versions it was computed with.
CREATE TABLE tender_compliance_assessments (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tender_id                UUID NOT NULL REFERENCES tender_documents(id) ON DELETE CASCADE,
    standards_version        INT NOT NULL,
    model_version            VARCHAR(64) NOT NULL,
    prompt_version           VARCHAR(32) NOT NULL,
    overall_eligibility_score NUMERIC(5, 2) NOT NULL,
    bid_recommendation       VARCHAR(24) NOT NULL CHECK (bid_recommendation IN ('GO', 'CONDITIONAL_GO', 'NO_GO')),
    clause_matrix            JSONB NOT NULL DEFAULT '[]'::jsonb,
    corrigendum_overrides    JSONB NOT NULL DEFAULT '[]'::jsonb,
    pre_bid_queries          JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tender_id, standards_version, model_version, prompt_version)
);

CREATE INDEX idx_assessment_clause_matrix_gin
    ON tender_compliance_assessments USING GIN (clause_matrix jsonb_path_ops);
