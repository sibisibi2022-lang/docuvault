package com.docuvault.shared.audit;

/** The closed set of events the audit trail records. */
public enum AuditAction {
    USER_REGISTERED,
    USER_LOGGED_IN,
    USER_ROLE_CHANGED,
    USER_DISABLED,
    DOCUMENT_UPLOADED,
    DOCUMENT_UPDATED,
    DOCUMENT_DOWNLOADED,
    DOCUMENT_DELETED,
    VERSION_CREATED,
    VERSION_RESTORED,
    CATEGORY_CREATED,
    CATEGORY_UPDATED,
    CATEGORY_DELETED
}
