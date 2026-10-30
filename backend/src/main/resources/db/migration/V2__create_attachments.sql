CREATE TABLE attachments (
    id uuid PRIMARY KEY,
    original_filename varchar(128) NOT NULL,
    storage_key varchar(255) NOT NULL UNIQUE
        CHECK (char_length(storage_key) > 0),
    size_bytes bigint NOT NULL
        CHECK (size_bytes >= 0),
    created_at timestamptz NOT NULL,
    deleted_at timestamptz,
    description varchar(248),
    CONSTRAINT attachments_deleted_after_created
        CHECK (deleted_at IS NULL OR deleted_at >= created_at)
);

CREATE INDEX attachments_created_at_idx
    ON attachments (created_at);
CREATE INDEX attachments_active_idx
    ON attachments (created_at)
    WHERE deleted_at IS NULL;