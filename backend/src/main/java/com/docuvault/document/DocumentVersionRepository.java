package com.docuvault.document;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Returns storage keys only, never entities.
     *
     * <p>Loading DocumentVersion entities here is what broke deletion: with them
     * managed in the persistence context, flushing the parent's removal made
     * Hibernate see live children pointing at a vanishing Document and abort
     * with TransientPropertyValueException. A scalar projection keeps the
     * persistence context empty, so there is nothing to check.
     */
    @Query("SELECT v.storageKey FROM DocumentVersion v WHERE v.document.id = :documentId")
    List<String> findStorageKeysByDocumentId(@Param("documentId") Long documentId);

    /**
     * Bulk-deletes a document's version rows.
     *
     * <p>The schema already cascades this delete, but doing it explicitly keeps
     * the behaviour visible in the code and independent of the database's
     * referential actions.
     */
    @Modifying
    @Query("DELETE FROM DocumentVersion v WHERE v.document.id = :documentId")
    int deleteAllByDocumentId(@Param("documentId") Long documentId);

    long countByDocumentId(Long documentId);
}
