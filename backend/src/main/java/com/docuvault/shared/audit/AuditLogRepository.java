package com.docuvault.shared.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * The entity graph fetches the actor in the same query. Without it this list
     * is a textbook N+1: one query for the page, then one per row for the name.
     */
    @EntityGraph(attributePaths = "actor")
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:action IS NULL OR a.action = :action)
              AND (:actorId IS NULL OR a.actor.id = :actorId)
            """)
    Page<AuditLog> search(@Param("action") AuditAction action,
                          @Param("actorId") Long actorId,
                          Pageable pageable);

    @EntityGraph(attributePaths = "actor")
    Page<AuditLog> findByDocumentId(Long documentId, Pageable pageable);
}
