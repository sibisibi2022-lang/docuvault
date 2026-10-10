package com.docuvault.document;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @EntityGraph(attributePaths = {"owner", "category"})
    Optional<Document> findWithRelationsById(Long id);

    /**
     * One query handles search, category filter, content-type filter and the
     * ownership rule.
     *
     * <p>{@code ownerId} is the access control: callers who may only see their
     * own documents pass their id and the predicate applies; ADMIN and MANAGER
     * pass {@code null} and see everything. Filtering here rather than in Java
     * means an EMPLOYEE's page 2 is genuinely their second page, not a
     * post-filtered slice of someone else's results.
     */
    @EntityGraph(attributePaths = {"owner", "category"})
    @Query("""
            SELECT d FROM Document d
            WHERE (:ownerId IS NULL OR d.owner.id = :ownerId)
              AND (:categoryId IS NULL OR d.category.id = :categoryId)
              AND (:contentType IS NULL OR d.contentType = :contentType)
              AND (CAST(:search AS String) IS NULL
                   OR LOWER(d.title)       LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                   OR LOWER(d.description) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                   OR LOWER(d.fileName)    LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))
            """)
    Page<Document> search(@Param("ownerId") Long ownerId,
                          @Param("categoryId") Long categoryId,
                          @Param("contentType") String contentType,
                          @Param("search") String search,
                          Pageable pageable);

    long countByOwnerId(Long ownerId);

    boolean existsByCategoryId(Long categoryId);

    @Query("SELECT COALESCE(SUM(d.sizeBytes), 0) FROM Document d WHERE (:ownerId IS NULL OR d.owner.id = :ownerId)")
    long totalBytesStored(@Param("ownerId") Long ownerId);

    @Query("""
            SELECT d.contentType AS contentType, COUNT(d) AS count
            FROM Document d
            WHERE (:ownerId IS NULL OR d.owner.id = :ownerId)
            GROUP BY d.contentType
            """)
    java.util.List<ContentTypeCount> countByContentType(@Param("ownerId") Long ownerId);

    /** Projection for the dashboard's file-type breakdown. */
    interface ContentTypeCount {
        String getContentType();

        long getCount();
    }
}
