-- Las sugerencias son calculadas; solo las confirmaciones humanas se persisten.
CREATE TABLE duplicate_links (
    id uuid PRIMARY KEY,
    incident_id uuid NOT NULL REFERENCES incidents(id) ON DELETE RESTRICT,
    candidate_incident_id uuid NOT NULL REFERENCES incidents(id) ON DELETE RESTRICT,
    linked_by_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    linked_at timestamptz NOT NULL,
    reason varchar(2000),
    CONSTRAINT uk_duplicate_links_pair UNIQUE (incident_id, candidate_incident_id),
    CONSTRAINT duplicate_links_distinct CHECK (incident_id <> candidate_incident_id),
    CONSTRAINT duplicate_links_canonical CHECK (incident_id < candidate_incident_id),
    CONSTRAINT duplicate_links_reason_length CHECK (reason IS NULL OR char_length(reason) <= 2000)
);
CREATE INDEX duplicate_links_candidate_idx ON duplicate_links (candidate_incident_id, linked_at DESC);
