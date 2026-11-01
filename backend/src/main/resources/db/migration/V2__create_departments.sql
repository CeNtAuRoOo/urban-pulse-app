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

INSERT INTO departments (id, name, code, description, active) VALUES
    ('0199f6de-0001-7000-8000-000000000001', 'Mantenimiento Urbano', 'MANT',
        'Conservación y reparación de la infraestructura urbana.', TRUE),
    ('0199f6de-0002-7000-8000-000000000002', 'Limpieza', 'LIMP',
        'Limpieza viaria y gestión de residuos municipales.', TRUE),
    ('0199f6de-0003-7000-8000-000000000003', 'Parques y Jardines', 'PARQ',
        'Conservación de zonas verdes, parques y arbolado urbano.', TRUE);
