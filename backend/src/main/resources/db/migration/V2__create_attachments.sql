CREATE TABLE attachments (
    id UUID PRIMARY KEY,

    uploaded_by UUID NOT NULL,

    original_filename VARCHAR(128) NOT NULL,

    content_type VARCHAR(20) NOT NULL,

    storage_key VARCHAR(255) NOT NULL UNIQUE,

    size_bytes BIGINT NOT NULL
        CHECK (size_bytes >= 0),

    created_at TIMESTAMPTZ NOT NULL,

    deleted_at TIMESTAMPTZ,

    description VARCHAR(300),

    CONSTRAINT attachments_deleted_after_created
        CHECK (deleted_at IS NULL OR deleted_at >= created_at),

    CONSTRAINT attachments_user_fk
        FOREIGN KEY (uploaded_by)
        REFERENCES users(id)
);

CREATE INDEX attachments_created_at_idx
    ON attachments (created_at);

CREATE INDEX attachments_active_idx
    ON attachments (created_at)
    WHERE deleted_at IS NULL;