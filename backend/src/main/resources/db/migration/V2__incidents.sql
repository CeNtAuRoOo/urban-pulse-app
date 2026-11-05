-- Núcleo de incidencias; las otras tres tablas preparan tramitación sin ejecutarla.
CREATE TABLE incidents (
    id uuid PRIMARY KEY,
    reporter_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    category_id uuid REFERENCES categories(id) ON DELETE RESTRICT,
    assigned_department_id uuid REFERENCES departments(id) ON DELETE RESTRICT,
    title varchar(150) NOT NULL,
    description varchar(5000) NOT NULL,
    latitude double precision NOT NULL,
    longitude double precision NOT NULL,
    accuracy_meters double precision,
    status varchar(32) NOT NULL,
    priority varchar(16),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    resolved_at timestamptz,
    closed_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT incidents_title_length CHECK (char_length(title) BETWEEN 5 AND 150),
    CONSTRAINT incidents_description_length CHECK (char_length(description) BETWEEN 10 AND 5000),
    CONSTRAINT incidents_text_normalized CHECK (
        title !~ U&'^[\0001-\0020]|[\0001-\0020]$' AND
        description !~ U&'^[\0001-\0020]|[\0001-\0020]$'),
    CONSTRAINT incidents_text_not_blank CHECK (
        title !~ U&'^[\0009-\000D\001C-\0020\1680\2000-\2006\2008-\200A\2028\2029\205F\3000]+$' AND
        description !~ U&'^[\0009-\000D\001C-\0020\1680\2000-\2006\2008-\200A\2028\2029\205F\3000]+$'),
    CONSTRAINT incidents_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT incidents_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT incidents_accuracy CHECK (accuracy_meters >= 0 AND accuracy_meters < 'Infinity'::float8),
    CONSTRAINT incidents_status CHECK (status IN ('REPORTED','VALIDATED','REJECTED',
        'ASSIGNED_TO_DEPARTMENT','WORK_ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED')),
    CONSTRAINT incidents_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT incidents_version CHECK (version >= 0),
    CONSTRAINT incidents_dates CHECK (updated_at >= created_at
        AND (resolved_at IS NULL OR resolved_at >= created_at)
        AND (closed_at IS NULL OR closed_at >= created_at))
);

-- Orden y filtros implementados, con desempate UUID estable.
CREATE INDEX incidents_created_idx ON incidents (created_at DESC, id DESC);
CREATE INDEX incidents_reporter_idx ON incidents (reporter_user_id, created_at DESC, id DESC);
CREATE INDEX incidents_status_idx ON incidents (status, created_at DESC, id DESC);
CREATE INDEX incidents_category_idx ON incidents (category_id, created_at DESC, id DESC);

CREATE TABLE status_changes (
    id uuid PRIMARY KEY,
    incident_id uuid NOT NULL REFERENCES incidents(id) ON DELETE RESTRICT,
    changed_by_user_id uuid REFERENCES users(id) ON DELETE RESTRICT,
    from_status varchar(32) NOT NULL,
    to_status varchar(32) NOT NULL,
    reason varchar(2000),
    resolution_notes varchar(5000),
    changed_at timestamptz NOT NULL,
    CONSTRAINT status_changes_from CHECK (from_status IN ('REPORTED','VALIDATED','REJECTED',
        'ASSIGNED_TO_DEPARTMENT','WORK_ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED')),
    CONSTRAINT status_changes_to CHECK (to_status IN ('REPORTED','VALIDATED','REJECTED',
        'ASSIGNED_TO_DEPARTMENT','WORK_ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED')),
    CONSTRAINT status_changes_distinct CHECK (from_status <> to_status AND from_status <> 'CLOSED')
);
CREATE INDEX status_changes_incident_idx ON status_changes (incident_id, changed_at, id);

CREATE TABLE incident_reviews (
    id uuid PRIMARY KEY,
    incident_id uuid NOT NULL REFERENCES incidents(id) ON DELETE RESTRICT,
    reviewed_by_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    decision varchar(32) NOT NULL,
    reason varchar(2000),
    reviewed_at timestamptz NOT NULL,
    CONSTRAINT incident_reviews_decision CHECK (decision IN ('VALIDATE','REJECT','REQUEST_INFORMATION')),
    CONSTRAINT incident_reviews_rejection_reason CHECK (decision <> 'REJECT' OR
        (reason IS NOT NULL AND char_length(btrim(reason)) >= 3))
);
CREATE INDEX incident_reviews_incident_idx ON incident_reviews (incident_id, reviewed_at, id);

CREATE TABLE priority_decisions (
    id uuid PRIMARY KEY,
    incident_id uuid NOT NULL REFERENCES incidents(id) ON DELETE RESTRICT,
    decided_by_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    priority varchar(16) NOT NULL,
    source varchar(16) NOT NULL,
    justification varchar(2000) NOT NULL,
    decided_at timestamptz NOT NULL,
    CONSTRAINT priority_decisions_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT priority_decisions_source CHECK (source IN ('MANUAL','ASSISTED')),
    CONSTRAINT priority_decisions_justification CHECK (char_length(btrim(justification)) >= 3)
);
CREATE INDEX priority_decisions_incident_idx ON priority_decisions (incident_id, decided_at, id);
-- El historial público reutiliza audit_events_resource_idx de V1.
