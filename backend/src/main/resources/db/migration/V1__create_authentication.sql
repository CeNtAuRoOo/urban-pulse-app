CREATE TABLE users (
    id uuid PRIMARY KEY,
    display_name varchar(120) NOT NULL CHECK (char_length(display_name) BETWEEN 2 AND 120),
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

CREATE TABLE roles (
    code varchar(32) PRIMARY KEY CHECK (code IN
        ('CITIZEN', 'OPERATOR', 'ADMIN', 'DEPARTMENT_COORDINATOR', 'TECHNICIAN')),
    description text NOT NULL
);
CREATE TABLE user_roles (
    user_id uuid NOT NULL REFERENCES users(id),
    role_code varchar(32) NOT NULL REFERENCES roles(code),
    PRIMARY KEY (user_id, role_code),
    CONSTRAINT user_roles_global_only CHECK (role_code IN ('CITIZEN', 'OPERATOR', 'ADMIN'))
);
CREATE INDEX user_roles_role_idx ON user_roles (role_code);

-- V1 ampliada antes del primer despliegue: no aplicar sobre una V1 anterior.
CREATE TABLE email_verifications (
    user_id uuid PRIMARY KEY REFERENCES users(id),
    code_hash bytea NOT NULL CHECK (octet_length(code_hash) = 32),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CHECK (expires_at > created_at AND expires_at <= created_at + interval '10 minutes')
);
CREATE INDEX email_verifications_expiry_idx ON email_verifications (expires_at);
CREATE INDEX users_unverified_created_idx ON users (created_at) WHERE email_verified = false;

INSERT INTO roles (code, description) VALUES
    ('CITIZEN', 'Ciudadanía: rol base de todas las cuentas'),
    ('OPERATOR', 'Operación municipal transversal'),
    ('ADMIN', 'Administración global'),
    ('DEPARTMENT_COORDINATOR', 'Coordinación departamental'),
    ('TECHNICIAN', 'Personal técnico departamental');
