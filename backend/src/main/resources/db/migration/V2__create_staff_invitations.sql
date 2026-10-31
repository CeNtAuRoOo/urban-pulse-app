-- Identidad municipal: no contiene cuentas privilegiadas ni catálogo ficticio.
CREATE TABLE departments (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL CHECK (char_length(btrim(name)) BETWEEN 2 AND 120),
    description varchar(500),
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE department_memberships (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    department_id uuid NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    role_code varchar(32) NOT NULL REFERENCES roles(code) ON DELETE RESTRICT,
    joined_at timestamptz NOT NULL,
    left_at timestamptz,
    CONSTRAINT department_memberships_role CHECK (role_code IN ('TECHNICIAN', 'DEPARTMENT_COORDINATOR')),
    CONSTRAINT department_memberships_dates CHECK (left_at IS NULL OR left_at >= joined_at)
);
CREATE UNIQUE INDEX department_memberships_active_unique
    ON department_memberships (user_id, department_id, role_code) WHERE left_at IS NULL;
CREATE INDEX department_memberships_user_idx ON department_memberships (user_id);
CREATE INDEX department_memberships_department_idx ON department_memberships (department_id);
CREATE INDEX department_memberships_role_idx ON department_memberships (role_code);

CREATE TABLE staff_invitations (
    id uuid PRIMARY KEY,
    code_hash bytea NOT NULL UNIQUE CHECK (octet_length(code_hash) = 32),
    email varchar(254) NOT NULL CHECK (email = lower(btrim(email)) AND char_length(email) > 0),
    role_code varchar(32) NOT NULL REFERENCES roles(code) ON DELETE RESTRICT,
    department_id uuid REFERENCES departments(id) ON DELETE RESTRICT,
    created_by_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    redeemed_by_user_id uuid REFERENCES users(id) ON DELETE RESTRICT,
    revoked_by_user_id uuid REFERENCES users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    redeemed_at timestamptz,
    revoked_at timestamptz,
    CONSTRAINT staff_invitations_scope CHECK (
        (role_code IN ('OPERATOR', 'ADMIN') AND department_id IS NULL) OR
        (role_code IN ('TECHNICIAN', 'DEPARTMENT_COORDINATOR') AND department_id IS NOT NULL)),
    CONSTRAINT staff_invitations_exclusive CHECK (redeemed_at IS NULL OR revoked_at IS NULL),
    CONSTRAINT staff_invitations_redemption CHECK ((redeemed_at IS NULL) = (redeemed_by_user_id IS NULL)),
    CONSTRAINT staff_invitations_revocation CHECK ((revoked_at IS NULL) = (revoked_by_user_id IS NULL)),
    CONSTRAINT staff_invitations_dates CHECK (
        expires_at > created_at AND
        (redeemed_at IS NULL OR (redeemed_at >= created_at AND redeemed_at < expires_at)) AND
        (revoked_at IS NULL OR (revoked_at >= created_at AND revoked_at < expires_at)))
);
CREATE INDEX staff_invitations_order_idx ON staff_invitations (created_at DESC, id DESC);
CREATE INDEX staff_invitations_email_idx ON staff_invitations (email, created_at DESC, id DESC);
CREATE INDEX staff_invitations_role_idx ON staff_invitations (role_code, created_at DESC, id DESC);
CREATE INDEX staff_invitations_department_idx ON staff_invitations (department_id, created_at DESC, id DESC);
CREATE INDEX staff_invitations_active_expiry_idx ON staff_invitations (expires_at)
    WHERE redeemed_at IS NULL AND revoked_at IS NULL;
CREATE INDEX staff_invitations_creator_idx ON staff_invitations (created_by_user_id);
CREATE INDEX staff_invitations_redeemer_idx ON staff_invitations (redeemed_by_user_id)
    WHERE redeemed_by_user_id IS NOT NULL;
CREATE INDEX staff_invitations_revoker_idx ON staff_invitations (revoked_by_user_id)
    WHERE revoked_by_user_id IS NOT NULL;

-- Las referencias históricas se conservan: un futuro borrado exige política explícita.
CREATE TABLE audit_events (
    id uuid PRIMARY KEY,
    actor_user_id uuid REFERENCES users(id) ON DELETE RESTRICT,
    action varchar(80) NOT NULL,
    resource_type varchar(80) NOT NULL,
    resource_id varchar(128) NOT NULL,
    summary varchar(500),
    details jsonb NOT NULL DEFAULT '{}',
    request_id varchar(128),
    occurred_at timestamptz NOT NULL
);
CREATE INDEX audit_events_actor_idx ON audit_events (actor_user_id);
CREATE INDEX audit_events_resource_idx ON audit_events (resource_type, resource_id, occurred_at DESC);
