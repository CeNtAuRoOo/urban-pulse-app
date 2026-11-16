-- Tramitación comparte el agregado y las tablas históricas de V1.
ALTER TABLE incidents DROP CONSTRAINT incidents_status;
ALTER TABLE incidents ADD CONSTRAINT incidents_status CHECK (status IN
    ('REPORTED','AWAITING_INFORMATION','VALIDATED','REJECTED','ASSIGNED_TO_DEPARTMENT',
     'WORK_ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED'));
ALTER TABLE status_changes DROP CONSTRAINT status_changes_from;
ALTER TABLE status_changes DROP CONSTRAINT status_changes_to;
ALTER TABLE status_changes ADD CONSTRAINT status_changes_from CHECK (from_status IN
    ('REPORTED','AWAITING_INFORMATION','VALIDATED','REJECTED','ASSIGNED_TO_DEPARTMENT',
     'WORK_ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED'));
ALTER TABLE status_changes ADD CONSTRAINT status_changes_to CHECK (to_status IN
    ('REPORTED','AWAITING_INFORMATION','VALIDATED','REJECTED','ASSIGNED_TO_DEPARTMENT',
     'WORK_ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED'));

-- V1 no prueba qué transición acompañó a una revisión. No inventar snapshots,
-- ni borrar motivos de VALIDATE históricos. NOT VALID conserva esas filas y
-- aplica el contrato nuevo a toda inserción/actualización posterior a V2.
ALTER TABLE incident_reviews ADD COLUMN resulting_status varchar(32);
ALTER TABLE incident_reviews ADD CONSTRAINT incident_reviews_result CHECK (
    resulting_status IS NOT NULL AND
    ((decision = 'VALIDATE' AND resulting_status = 'VALIDATED' AND reason IS NULL)
    OR (decision = 'REJECT' AND resulting_status = 'REJECTED')
    OR (decision = 'REQUEST_INFORMATION' AND resulting_status = 'AWAITING_INFORMATION'))
) NOT VALID;
ALTER TABLE incident_reviews ADD CONSTRAINT incident_reviews_request_key UNIQUE (id, incident_id, decision);

CREATE TABLE incident_information_responses (
    id uuid PRIMARY KEY,
    incident_id uuid NOT NULL REFERENCES incidents(id) ON DELETE RESTRICT,
    review_id uuid NOT NULL UNIQUE,
    request_decision varchar(32) NOT NULL DEFAULT 'REQUEST_INFORMATION'
        CHECK (request_decision = 'REQUEST_INFORMATION'),
    responded_by_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    message varchar(2000) NOT NULL,
    responded_at timestamptz NOT NULL,
    CONSTRAINT information_response_request FOREIGN KEY (review_id, incident_id, request_decision)
        REFERENCES incident_reviews(id, incident_id, decision) ON DELETE RESTRICT,
    CONSTRAINT information_response_message CHECK (
        char_length(message) BETWEEN 3 AND 2000
        AND message !~ U&'^[\0001-\0020]|[\0001-\0020]$'
        AND message !~ U&'^[\0009-\000D\001C-\0020\1680\2000-\2006\2008-\200A\2028\2029\205F\3000]+$')
);
CREATE INDEX information_responses_incident_idx
    ON incident_information_responses (incident_id, responded_at DESC, id DESC);
