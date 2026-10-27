# Autonomous Tender & Compliance Processing Engine (TCE)

[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.16-6DB33F?style=flat&logo=spring&logoColor=white)](https://spring.io/)
[![PostgreSQL 17](https://img.shields.io/badge/PostgreSQL-17_JSONB-4169E1?style=flat&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Meta Muse Spark](https://img.shields.io/badge/Meta_AI-Muse_Spark_1.3-0668E1?style=flat&logo=meta&logoColor=white)](https://ai.meta.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

> An enterprise compliance engine pairing **Meta Muse Spark** for structured criteria extraction with a **deterministic JVM rules engine** for audit-proof tender eligibility verification, page-level clause citations, and automated corrigendum resolution.

---

## Executive Summary

Manual screening of government and enterprise procurement documents (RFPs, RFQs, Tenders) is slow, expensive, and prone to oversight. A single overlooked disqualification clause (turnover thresholds, EMD caps, ISO certifications) leads to forfeited bid securities and lost revenue.

Conversely, using pure LLM prompts to decide bid eligibility introduces severe compliance risks: generative models hallucinate numbers, fail at strict boundary math, and cannot produce legally defensible audit trails.

**TCE solves this through a Hybrid Deterministic Architecture:**
1. **LLM Extraction Layer (Meta Muse Spark 1.3):** Ingests complex tender text in a single pass to extract atomic eligibility criteria into structured JSON schema.
2. **Deterministic Java 21 Rule Engine:** Normalizes Indian financial/temporal units (`Crore`, `Lakh`, `INR`, `Years`) and runs strict mathematical comparisons against version-pinned database standards.
3. **Programmatic Citation Verifier:** Confirms verbatim extracted quotes physically exist in source PDF pages before assigning a verified badge.

---

## System Architecture

```text
                                [ Tender PDF / Corrigendum ]
                                             |
                                             v
                             +-------------------------------+
                             |  Tender Ingestion Controller  |
                             |  - Magic Byte Validation      |
                             |  - Single-Pass SHA-256 Stream |
                             +---------------+---------------+
                                             |
                     +-----------------------+-----------------------+
                     | Duplicate Check                               | New Upload
                     v                                               v
        +-------------------------+                     +-------------------------+
        | Return Cached JSONB     |                     | Stream to Temp Storage  |
        | Assessment (<50ms)      |                     | Async Job Dispatch      |
        +-------------------------+                     +------------+------------+
                                                                     |
                         +-------------------------------------------+
                         |
                         v
+-------------------------------------------------+     +-----------------------------------+
| Bounded PDF Worker Pool (Semaphore 4)           |     | Concurrency Model                 |
| - RandomAccessReadBufferedFile                  |     | - Platform Threads: CPU PDFBox    |
| - PdfBoxPageExtractor (Text & Density Scoring)  |     | - Virtual Threads: I/O (DB / API) |
| - tender_pages Table Isolation                  |     +-----------------------------------+
+------------------------+------------------------+
                         |
                         v
+-------------------------------------------------+
| Meta Model API Integration Layer                |
| - MODEL_PROVIDER=mock (Replay Fixture Cache)    |
| - MODEL_PROVIDER=meta (Live Muse Spark 1.3)     |
+------------------------+------------------------+
                         |
                         v  [ Extracted Requirements JSON ]
+-------------------------------------------------+
| Hybrid Compliance Engine                        |
| 1. IndianUnitNormalizer (Lakh/Crore -> Base)    |
| 2. DeterministicRuleEvaluator (vs PG17 Baseline)|
| 3. CitationQuoteVerifier (Verbatim String Match)|
+------------------------+------------------------+
                         |
                         v
+-------------------------------------------------+     +-----------------------------------+
| PostgreSQL 17 Persistence Store                 |     | Output & Export Delivery          |
| - Version-Pinned Compliance Assessments         | ==> | - Apache POI 3-Tab Excel Workbook |
| - GIN Indexing (jsonb_path_ops)                 |     | - SSE Live Evaluation Dashboard   |
| - Native JSON_TABLE Aggregations                |     | - Bid Recommendation (GO/NO_GO)   |
+-------------------------------------------------+     +-----------------------------------+
```

---

## Key Features

- **Streaming Ingestion & Instant Deduplication:** Validates `%PDF-` magic bytes, computes SHA-256 in a single streaming pass using `DigestInputStream`, and leverages PostgreSQL `INSERT ... ON CONFLICT DO NOTHING` for race-free deduplication.
- **Bounded CPU Isolation vs. Virtual Threads:** Uses a dedicated bounded platform-thread pool (`pdfExecutor`, 4 threads) for CPU/memory-intensive Apache PDFBox operations, keeping web request handling and DB queries on lightweight Java 21 virtual threads.
- **Indian Procurement Unit Normalization:** Automatically resolves `Lakh`, `Crore`, `INR`, and duration variants into standard SI base values for deterministic mathematical threshold checks.
- **Audit-Ready Verbatim Citation Verification:** Every extracted clause is programmatically checked against source page text (`tender_pages.raw_text`), ensuring zero hallucinations in compliance decisions.
- **Zero-Cost Mock & Live Meta Provider Switch:** Seamlessly toggle between local test fixture replays (`MODEL_PROVIDER=mock`) and live Meta Model API (`MODEL_PROVIDER=meta`, Muse Spark 1.3).

---

## Prerequisites & Getting Started

### Prerequisites
- **Java 21** (e.g. OpenJDK 21)
- **Apache Maven 3.9+**
- **Docker Desktop** (for PostgreSQL 17 container & Testcontainers)

### 1. Start Infrastructure
```bash
docker compose up -d
```

### 2. Configure Environment
Copy `.env.example` to `.env` and configure your settings:
```bash
cp .env.example .env
```
Key configuration properties (`application.yml`):
```yaml
tender:
  engine:
    pdf-worker-threads: 4
    standards-version: 1
    prompt-version: "v1.0"
  ai:
    provider: mock                    # 'mock' for fixture replay, 'meta' for live API
    base-url: https://api.meta.ai/v1
    muse-spark-model: muse-spark-1.3
    sam-model: sam-3-1
```

### 3. Build & Run
```bash
# Run all tests
mvn clean verify

# Start Spring Boot application
mvn spring-boot:run
```

---

## REST API Endpoints

### 1. Ingest Tender Document
```bash
curl -X POST http://localhost:8080/api/tenders \
  -F "file=@/path/to/tender.pdf" \
  -F "docType=MAIN_TENDER"
```
* **Response (202 Accepted on new upload, 200 OK on duplicate):**
```json
{
  "id": "e8a2a893-b295-46b5-9ec4-3dafe3c64c23",
  "status": "UPLOADED",
  "duplicate": false
}
```

### 2. Ingest Corrigendum
```bash
curl -X POST http://localhost:8080/api/tenders \
  -F "file=@/path/to/corrigendum.pdf" \
  -F "docType=CORRIGENDUM" \
  -F "parentTenderId=e8a2a893-b295-46b5-9ec4-3dafe3c64c23"
```

### 3. Check Ingestion & Processing Status
```bash
curl http://localhost:8080/api/tenders/e8a2a893-b295-46b5-9ec4-3dafe3c64c23/status
```
* **Response (200 OK):**
```json
{
  "id": "e8a2a893-b295-46b5-9ec4-3dafe3c64c23",
  "fileName": "tender.pdf",
  "docType": "MAIN_TENDER",
  "status": "COMPLETED",
  "pageCount": 42,
  "processingDurationMs": 1420
}
```

---

## Verification & Testing

The project uses JUnit 5, AssertJ, MockMvc, and Testcontainers (PostgreSQL 17):
```bash
mvn clean verify
```
Test suites:
- `TenderIngestionControllerTest`: MockMvc integration tests for upload, deduplication, corrigendum validation, 400 bad request handling, and async status polling.
- `SchemaMigrationTest`: Validates Flyway V1 & V2 migrations and internal compliance standards seeding on PostgreSQL 17.
- `ContentFingerprintServiceTest`: Validates SHA-256 digest computation and normalized whitespace hash deduplication.
- `PdfBoxPageExtractorTest`: Validates page text extraction, dense table detection, and scanned page heuristics.
