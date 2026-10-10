package com.docuvault.shared.audit;

import java.time.Instant;

public record AuditLogDto(
        Long id,
        AuditAction action,
        String description,
        Long actorId,
        String actorName,
        Long documentId,
        Instant createdAt) {

    static AuditLogDto from(AuditLog log) {
        return new AuditLogDto(
                log.getId(),
                log.getAction(),
                log.getDescription(),
                log.getActor() == null ? null : log.getActor().getId(),
                log.getActor() == null ? "Deleted user" : log.getActor().getName(),
                log.getDocumentId(),
                log.getCreatedAt());
    }
}
