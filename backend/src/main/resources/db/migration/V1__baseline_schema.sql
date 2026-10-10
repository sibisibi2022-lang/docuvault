-- DocuVault baseline schema.
-- Every column the application relies on is declared here, so the database
-- shape is reviewable in a pull request instead of inferred by Hibernate.

CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120)  NOT NULL,
    email       VARCHAR(255)  NOT NULL,
    password    VARCHAR(100)  NOT NULL,
    role        VARCHAR(20)   NOT NULL,
    enabled     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'MANAGER', 'EMPLOYEE'))
);

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_categories_name UNIQUE (name)
);

CREATE TABLE documents (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    description   VARCHAR(2000),
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(150) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    storage_key   VARCHAR(500) NOT NULL,
    checksum      VARCHAR(64)  NOT NULL,
    owner_id      BIGINT       NOT NULL,
    category_id   BIGINT,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_documents_owner    FOREIGN KEY (owner_id)    REFERENCES users (id),
    CONSTRAINT fk_documents_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL
);

CREATE INDEX idx_documents_owner    ON documents (owner_id);
CREATE INDEX idx_documents_category ON documents (category_id);
CREATE INDEX idx_documents_created  ON documents (created_at DESC);

CREATE TABLE document_versions (
    id             BIGSERIAL PRIMARY KEY,
    document_id    BIGINT       NOT NULL,
    version_number INTEGER      NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    content_type   VARCHAR(150) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    storage_key    VARCHAR(500) NOT NULL,
    checksum       VARCHAR(64)  NOT NULL,
    uploaded_by_id BIGINT       NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_versions_document FOREIGN KEY (document_id)    REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_versions_user     FOREIGN KEY (uploaded_by_id) REFERENCES users (id),
    -- Enforces "no two versions share a number" in the database, so two
    -- concurrent uploads can never both claim v3.
    CONSTRAINT uq_versions_document_number UNIQUE (document_id, version_number),
    -- Enforces the de-duplication rule the service layer checks, so an
    -- identical re-upload is rejected even under a race.
    CONSTRAINT uq_versions_document_checksum UNIQUE (document_id, checksum)
);

CREATE INDEX idx_versions_document ON document_versions (document_id, version_number DESC);

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    action      VARCHAR(40)  NOT NULL,
    description VARCHAR(1000) NOT NULL,
    actor_id    BIGINT,
    -- Deliberately NOT a foreign key. An audit entry must survive the deletion
    -- of the document it describes, otherwise the record of that deletion
    -- destroys its own subject.
    document_id BIGINT,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_created  ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_actor    ON audit_logs (actor_id);
CREATE INDEX idx_audit_document ON audit_logs (document_id);
