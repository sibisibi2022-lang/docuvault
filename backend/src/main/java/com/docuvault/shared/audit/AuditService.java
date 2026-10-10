package com.docuvault.shared.audit;

import com.docuvault.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Writes an audit entry in the caller's transaction.
     *
     * <p>Joining the caller's transaction rather than opening a new one is
     * deliberate. A separate transaction cannot see rows the caller has not
     * committed yet, so auditing a just-created document would fail its foreign
     * key; and an entry claiming "deleted document 7" is worse than no entry at
     * all when the delete itself rolled back. The trail records what happened,
     * not what was attempted.
     */
    @Transactional
    public void record(AuditAction action, User actor, Long documentId, String description) {
        auditLogRepository.save(AuditLog.builder()
                .action(action)
                .actor(actor)
                .documentId(documentId)
                .description(description)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> search(AuditAction action, Long actorId, Pageable pageable) {
        return auditLogRepository.search(action, actorId, pageable).map(AuditLogDto::from);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> forDocument(Long documentId, Pageable pageable) {
        return auditLogRepository.findByDocumentId(documentId, pageable).map(AuditLogDto::from);
    }
}
