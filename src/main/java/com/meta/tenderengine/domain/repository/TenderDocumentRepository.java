package com.meta.tenderengine.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.meta.tenderengine.domain.entity.TenderDocumentEntity;

public interface TenderDocumentRepository extends JpaRepository<TenderDocumentEntity, UUID> {

    Optional<TenderDocumentEntity> findByFileSha256(String fileSha256);

    @Modifying
    @Query(value = """
            INSERT INTO tender_documents (id, file_name, doc_type, parent_tender_id, file_sha256, document_metadata)
            VALUES (:#{#doc.id}, :#{#doc.fileName}, :#{#doc.docType.name()}, :#{#doc.parentTenderId}, \
            :#{#doc.fileSha256}, CAST(:metadataJson AS jsonb))
            ON CONFLICT (file_sha256) DO NOTHING
            """, nativeQuery = true)
    void insertOnConflictDoNothing(@org.springframework.data.repository.query.Param("doc") TenderDocumentEntity doc,
                                   @org.springframework.data.repository.query.Param("metadataJson") String metadataJson);
}
