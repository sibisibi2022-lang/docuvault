package com.docuvault.document;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    @EntityGraph(attributePaths = "uploadedBy")
    Page<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(Long documentId, Pageable pageable);

    @EntityGraph(attributePaths = {"uploadedBy", "document", "document.owner"})
    Optional<DocumentVersion> findWithDocumentById(Long id);

    /**
     * Replaces the original implementation, which downloaded and re-hashed every
     * stored version on every upload. The checksum is already persisted, so this
     * is an index lookup instead of O(versions) file reads.
     */
    boolean existsByDocumentIdAndChecksum(Long documentId, String checksum);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM DocumentVersion v WHERE v.document.id = :documentId")
    int highestVersionNumber(@Param("documentId") Long documentId);

    List<DocumentVersion> findByDocumentId(Long documentId);

    long countByDocumentId(Long documentId);
}
