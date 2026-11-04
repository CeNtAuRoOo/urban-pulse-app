
-- 01. IDENTIDAD Y CUENTAS --

-- Usuarios y preferencias de notificación

CREATE TABLE users (
    id uuid PRIMARY KEY,
    display_name varchar(120) NOT NULL CHECK (char_length(display_name) BETWEEN 2 AND 120
        AND display_name = btrim(display_name, U&' \00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\202F\205F\3000')
        AND display_name !~ '[[:cntrl:]]'),
    email varchar(254) NOT NULL CHECK (email = lower(btrim(email)) AND char_length(email) > 0),
    password_hash varchar(60) NOT NULL CHECK (char_length(password_hash) = 60),
    active boolean NOT NULL DEFAULT true,
    email_verified boolean NOT NULL DEFAULT false,
    token_version bigint NOT NULL DEFAULT 0 CHECK (token_version >= 0),
    notify_in_app boolean NOT NULL DEFAULT true,
    notify_email boolean NOT NULL DEFAULT true,
    notify_push boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT users_email_unique UNIQUE (email)
);

CREATE INDEX users_unverified_created_idx ON users (created_at) WHERE email_verified = false;

-- 02. ROLES Y AUTORIZACIÓN GLOBAL --

-- Definición de roles

CREATE TABLE roles (
    code varchar(32) PRIMARY KEY CHECK (code IN
        ('CITIZEN', 'OPERATOR', 'ADMIN', 'DEPARTMENT_COORDINATOR', 'TECHNICIAN')),
    description text NOT NULL
);

INSERT INTO roles (code, description) VALUES
    ('CITIZEN', 'Ciudadanía: rol base de todas las cuentas'),
    ('OPERATOR', 'Operación municipal transversal'),
    ('ADMIN', 'Administración global'),
    ('DEPARTMENT_COORDINATOR', 'Coordinación departamental'),
    ('TECHNICIAN', 'Personal técnico departamental');

-- Asignación de roles globales a usuarios

CREATE TABLE user_roles (
    user_id uuid NOT NULL REFERENCES users(id),
    role_code varchar(32) NOT NULL REFERENCES roles(code),
    PRIMARY KEY (user_id, role_code),
    CONSTRAINT user_roles_global_only CHECK (role_code IN ('CITIZEN', 'OPERATOR', 'ADMIN'))
);

CREATE INDEX user_roles_role_idx ON user_roles (role_code);

-- 03. VERIFICACIÓN DEL CORREO ELECTRÓNICO --

-- Verificación de cuentas

CREATE TABLE email_verifications (
    user_id uuid PRIMARY KEY REFERENCES users(id),
    code_hash bytea NOT NULL CHECK (octet_length(code_hash) = 32),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CHECK (expires_at > created_at AND expires_at <= created_at + interval '10 minutes')
);

CREATE INDEX email_verifications_expiry_idx ON email_verifications (expires_at);

-- 04. DEPARTAMENTOS Y PERSONAL --

-- Catálogo de departamentos

CREATE TABLE departments (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(120) NOT NULL,
    code VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_departments_name_not_blank CHECK (length(btrim(name)) BETWEEN 2 AND 120),
    CONSTRAINT chk_departments_code_format CHECK (code ~ '^[A-Z0-9_-]{2,20}$')
);

CREATE UNIQUE INDEX uk_departments_name_normalized ON departments (lower(name));

CREATE UNIQUE INDEX uk_departments_code_normalized ON departments (lower(code));

-- Pertenencias y roles departamentales

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

-- 05. INVITACIONES A PERSONAL --

-- Invitaciones, canje y revocación

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

-- 06. AUDITORÍA --

-- Registro de eventos

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

-- 07. CATEGORÍAS --

-- Catálogo de categorías

CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_categories_name_length CHECK (char_length(name) BETWEEN 2 AND 100),
    CONSTRAINT chk_categories_name_not_blank CHECK (
        name !~ U&'^[\0009-\000D\001C-\0020\1680\2000-\2006\2008-\200A\2028\2029\205F\3000]+$'
    ),

CREATE UNIQUE INDEX uk_categories_name_normalized ON categories (lower(name));
