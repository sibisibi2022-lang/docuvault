package com.docuvault.shared.audit;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only by design. The original exposed POST, PUT and DELETE on audit logs,
 * which let the very users being audited rewrite their own trail.
 */
@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "Audit log")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public Page<AuditLogDto> list(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Long actorId,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditService.search(action, actorId, pageable);
    }

    @GetMapping("/document/{documentId}")
    public Page<AuditLogDto> forDocument(
            @PathVariable Long documentId,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditService.forDocument(documentId, pageable);
    }
}
