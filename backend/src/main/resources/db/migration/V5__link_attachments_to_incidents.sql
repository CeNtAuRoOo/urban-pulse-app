-- V5__link_attachments_to_incidents.sql

ALTER TABLE attachments
    ADD COLUMN incident_id UUID NOT NULL;

ALTER TABLE attachments
    ADD CONSTRAINT attachments_incident_fk
        FOREIGN KEY (incident_id)
        REFERENCES incidents(id)
        ON DELETE RESTRICT;

CREATE INDEX attachments_incident_active_idx
    ON attachments (incident_id)
    WHERE deleted_at IS NULL;
