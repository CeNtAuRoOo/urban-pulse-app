-- Conserva las adscripciones departamentales activas e históricas de cada usuario.
CREATE TABLE department_memberships (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id UUID NOT NULL REFERENCES users(id),
    department_id UUID NOT NULL REFERENCES departments(id),
    role_code VARCHAR(32) NOT NULL REFERENCES roles(code),
    joined_at TIMESTAMPTZ NOT NULL,
    left_at TIMESTAMPTZ,
    CONSTRAINT chk_department_memberships_department_role CHECK (
        role_code IN ('DEPARTMENT_COORDINATOR', 'TECHNICIAN')
    ),
    CONSTRAINT chk_department_memberships_dates CHECK (
        left_at IS NULL OR left_at >= joined_at
    )
);

-- Impide dos adscripciones activas con la misma combinación usuario/departamento/rol.
-- Las adscripciones finalizadas se conservan y permiten nuevas altas históricas.
CREATE UNIQUE INDEX uk_department_memberships_active
    ON department_memberships (user_id, department_id, role_code)
    WHERE left_at IS NULL;

-- Facilita la consulta cronológica de las pertenencias de cada usuario.
CREATE INDEX department_memberships_user_history_idx
    ON department_memberships (user_id, joined_at DESC, id ASC);
