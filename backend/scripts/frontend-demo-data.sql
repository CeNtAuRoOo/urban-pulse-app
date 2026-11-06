-- =============================================================================
-- UrbanPulse | Datos de demostración del frontend
-- =============================================================================
-- USO EXCLUSIVO EN UNA BASE DE DATOS LOCAL Y DESECHABLE.
-- Requiere PostgreSQL con las migraciones Flyway V1, V2 y V3 aplicadas.
-- No forma parte de Flyway ni se ejecuta automáticamente al arrancar la API.
--
-- Ejecutar con psql y un hash BCrypt propio (60 caracteres, coste >= 04):
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 \
--     -v demo_password_hash="$BCRYPT_HASH" \
--     -f backend/scripts/frontend-demo-data.sql
--
-- Conservar los correos/UUID originales: frontend/scripts/integration.cjs utiliza
-- las seis cuentas históricas y presupone una única pertenencia para TECHNICIAN.
-- Todas las cuentas nuevas comparten el hash recibido; no hay claves por defecto.
--
-- Propiedades:
--   * Transaccional: cualquier error revierte TODAS las inserciones.
--   * Reejecutable: ON CONFLICT no modifica las filas existentes.
--   * Seguro ante colisiones de identidad: nunca promociona otro UUID por email.
--   * Solo datos implementados: las incidencias permanecen en REPORTED; no se
--     inventan revisiones, asignaciones, prioridades ni códigos de invitación.
--   * Las fechas son relativas al primer momento de ejecución y permanecen
--     intactas al volver a ejecutar el script.
-- =============================================================================

\set ON_ERROR_STOP on
BEGIN;

-- 00. Credenciales y protección de identidad ---------------------------------
-- SET LOCAL no imprime el hash y lo descarta al terminar la transacción.
SET LOCAL urbanpulse.demo_password_hash = :'demo_password_hash';

DO $guard$
BEGIN
    IF current_setting('urbanpulse.demo_password_hash') !~
       '^\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}$' THEN
        RAISE EXCEPTION 'demo_password_hash debe ser un hash BCrypt válido de 60 caracteres (coste 04-31)';
    END IF;

    -- Si un correo sintético tiene otro ID, o un ID reservado otro correo,
    -- cancelar la operación: no se deben conceder roles a cuentas ajenas.
    IF EXISTS (
        SELECT 1
        FROM (VALUES
            ('019a0000-0000-7000-8000-000000000001'::uuid, 'demo.citizen@urbanpulse.test'),
            ('019a0000-0000-7000-8000-000000000002'::uuid, 'demo.admin@urbanpulse.test'),
            ('019a0000-0000-7000-8000-000000000003'::uuid, 'demo.operator@urbanpulse.test'),
            ('019a0000-0000-7000-8000-000000000004'::uuid, 'demo.technician@urbanpulse.test'),
            ('019a0000-0000-7000-8000-000000000005'::uuid, 'demo.pending@urbanpulse.test'),
            ('019a0000-0000-7000-8000-000000000006'::uuid, 'demo.other@urbanpulse.test'),
            ('019a0000-0000-7000-8000-000000000007'::uuid, 'demo.coordinator@urbanpulse.test'),
            ('019a0000-0000-7000-8000-000000000008'::uuid, 'demo.parks@urbanpulse.test'),
            ('019a0000-0000-7000-8000-000000000009'::uuid, 'demo.inactive@urbanpulse.test')
        ) AS seed(id, email)
        JOIN users u ON u.id = seed.id OR u.email = seed.email
        WHERE u.id IS DISTINCT FROM seed.id
           OR u.email IS DISTINCT FROM seed.email
    ) THEN
        RAISE EXCEPTION 'Colisión entre UUID y correo de una cuenta demo: no se ha modificado ningún usuario';
    END IF;
END;
$guard$;

-- 01. Usuarios ---------------------------------------------------------------
-- Conservamos 0001-0006 (compatibilidad con integración).
-- 0005: email pendiente; 0009: cuenta desactivada para filtros de administración.
-- Las preferencias distintas permiten comprobar la representación del perfil.
INSERT INTO users (
    id, display_name, email, password_hash, active, email_verified,
    notify_in_app, notify_email, notify_push, created_at, updated_at
) VALUES
    ('019a0000-0000-7000-8000-000000000001', 'Ciudadana Demo',
     'demo.citizen@urbanpulse.test', current_setting('urbanpulse.demo_password_hash'),
     true, true, true, true, true, now(), now()),
    ('019a0000-0000-7000-8000-000000000002', 'Administración Demo',
     'demo.admin@urbanpulse.test', current_setting('urbanpulse.demo_password_hash'),
     true, true, true, true, true, now(), now()),
    ('019a0000-0000-7000-8000-000000000003', 'Operación Demo',
     'demo.operator@urbanpulse.test', current_setting('urbanpulse.demo_password_hash'),
     true, true, true, true, true, now(), now()),
    ('019a0000-0000-7000-8000-000000000004', 'Personal Departamental Demo',
     'demo.technician@urbanpulse.test', current_setting('urbanpulse.demo_password_hash'),
     true, true, true, true, true, now(), now()),
    ('019a0000-0000-7000-8000-000000000005', 'Cuenta Pendiente Demo',
     'demo.pending@urbanpulse.test', current_setting('urbanpulse.demo_password_hash'),
     true, false, true, true, true, now(), now()),
    ('019a0000-0000-7000-8000-000000000006', 'Otra Ciudadana Demo',
     'demo.other@urbanpulse.test', current_setting('urbanpulse.demo_password_hash'),
     true, true, true, true, true, now(), now()),
    ('019a0000-0000-7000-8000-000000000007', 'Coordinación Demo',
     'demo.coordinator@urbanpulse.test', current_setting('urbanpulse.demo_password_hash'),
     true, true, true, true, false, now(), now()),
    ('019a0000-0000-7000-8000-000000000008', 'Técnico de Parques Demo',
     'demo.parks@urbanpulse.test', current_setting('urbanpulse.demo_password_hash'),
     true, true, true, true, true, now(), now()),
    ('019a0000-0000-7000-8000-000000000009', 'Cuenta Inactiva Demo',
     'demo.inactive@urbanpulse.test', current_setting('urbanpulse.demo_password_hash'),
     false, true, false, false, false, now(), now())
ON CONFLICT (id) DO NOTHING;

-- 02. Roles globales ---------------------------------------------------------
-- TECHNICIAN/DEPARTMENT_COORDINATOR no son roles globales; se atribuyen
-- exclusivamente mediante department_memberships.
INSERT INTO user_roles (user_id, role_code)
SELECT u.id, 'CITIZEN'
FROM users u
WHERE u.id IN (
    '019a0000-0000-7000-8000-000000000001',
    '019a0000-0000-7000-8000-000000000002',
    '019a0000-0000-7000-8000-000000000003',
    '019a0000-0000-7000-8000-000000000004',
    '019a0000-0000-7000-8000-000000000005',
    '019a0000-0000-7000-8000-000000000006',
    '019a0000-0000-7000-8000-000000000007',
    '019a0000-0000-7000-8000-000000000008',
    '019a0000-0000-7000-8000-000000000009'
)
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_code) VALUES
    ('019a0000-0000-7000-8000-000000000002', 'ADMIN'),
    ('019a0000-0000-7000-8000-000000000003', 'OPERATOR')
ON CONFLICT DO NOTHING;

-- 03. Catálogo de categorías -------------------------------------------------
-- La categoría 0102 está inactiva: no debe utilizarse para crear avisos nuevos.
INSERT INTO categories (id, name, description, active) VALUES
    ('019a0000-0000-7000-8000-000000000101', 'Alumbrado · Demo',
     'Averías de farolas e iluminación pública.', true),
    ('019a0000-0000-7000-8000-000000000102', 'Categoría inactiva · Demo',
     'Registro inactivo para probar la consulta y el filtrado del catálogo.', false),
    ('019a0000-0000-7000-8000-000000000103', 'Vía pública · Demo',
     'Desperfectos de aceras, pavimento y mobiliario vial.', true),
    ('019a0000-0000-7000-8000-000000000104', 'Parques y jardines · Demo',
     'Arbolado, zonas verdes y equipamientos de parques.', true),
    ('019a0000-0000-7000-8000-000000000105', 'Limpieza · Demo',
     'Residuos y limpieza de espacios públicos.', true),
    ('019a0000-0000-7000-8000-000000000106', 'Movilidad · Demo',
     'Semáforos, señales y pasos peatonales.', true)
ON CONFLICT (id) DO NOTHING;

-- 04. Departamentos ----------------------------------------------------------
-- Los códigos se ajustan a ^[A-Z0-9_-]{2,20}$ y son únicos.
INSERT INTO departments (id, name, code, description, active) VALUES
    ('019a0000-0000-7000-8000-000000000201', 'Mantenimiento · Demo',
     'DEMO_MANT', 'Departamento activo de mantenimiento urbano.', true),
    ('019a0000-0000-7000-8000-000000000202', 'Departamento inactivo · Demo',
     'DEMO_INACT', 'Departamento desactivado: no admite nuevas invitaciones.', false),
    ('019a0000-0000-7000-8000-000000000203', 'Movilidad · Demo',
     'DEMO_MOV', 'Señalización y movilidad urbana.', true),
    ('019a0000-0000-7000-8000-000000000204', 'Zonas verdes · Demo',
     'DEMO_PARQ', 'Conservación de jardines y arbolado.', true),
    ('019a0000-0000-7000-8000-000000000205', 'Limpieza municipal · Demo',
     'DEMO_LIMP', 'Mantenimiento de la limpieza pública.', true)
ON CONFLICT (id) DO NOTHING;

-- 05. Pertenencias departamentales ------------------------------------------
-- 0301 conserva la adscripción del técnico usada por el test de integración.
-- 0304 conserva una adscripción HISTÓRICA (left_at no nulo).
INSERT INTO department_memberships (
    id, user_id, department_id, role_code, joined_at, left_at
) VALUES
    ('019a0000-0000-7000-8000-000000000301',
     '019a0000-0000-7000-8000-000000000004',
     '019a0000-0000-7000-8000-000000000201', 'TECHNICIAN',
     now() - interval '21 days', NULL),
    ('019a0000-0000-7000-8000-000000000302',
     '019a0000-0000-7000-8000-000000000007',
     '019a0000-0000-7000-8000-000000000201', 'DEPARTMENT_COORDINATOR',
     now() - interval '20 days', NULL),
    ('019a0000-0000-7000-8000-000000000303',
     '019a0000-0000-7000-8000-000000000008',
     '019a0000-0000-7000-8000-000000000204', 'TECHNICIAN',
     now() - interval '10 days', NULL),
    ('019a0000-0000-7000-8000-000000000304',
     '019a0000-0000-7000-8000-000000000008',
     '019a0000-0000-7000-8000-000000000201', 'TECHNICIAN',
     now() - interval '60 days', now() - interval '25 days')
ON CONFLICT DO NOTHING;

-- 06. Incidencias del núcleo actualmente implementado -----------------------
-- Todas son REPORTED, sin prioridad ni departamento asignado: los servicios
-- de revisión, tramitación y prioridad todavía no existen.
-- Un reporte (0401) tiene una corrección previa y version=1; los demás version=0.
-- Las coordenadas WGS84 son ficticias y meramente ilustrativas de Málaga.
-- La incidencia 0409 no tiene categoría, caso admitido por el contrato.
INSERT INTO incidents (
    id, reporter_user_id, category_id, title, description,
    latitude, longitude, accuracy_meters, status, created_at, updated_at, version
) VALUES
    ('019a0000-0000-7000-8000-000000000401',
     '019a0000-0000-7000-8000-000000000001', '019a0000-0000-7000-8000-000000000101',
     'Farola apagada en el paseo marítimo',
     'Varias luminarias llevan dos noches sin encenderse. La zona queda poco iluminada.',
     36.7173, -4.4146, 5.0, 'REPORTED',
     now() - interval '14 days', now() - interval '13 days', 1),
    ('019a0000-0000-7000-8000-000000000402',
     '019a0000-0000-7000-8000-000000000001', '019a0000-0000-7000-8000-000000000103',
     'Baldosa levantada junto al colegio',
     'Hay varias baldosas sueltas en la acera y suponen un riesgo de tropiezo.',
     36.7213, -4.4214, 3.0, 'REPORTED',
     now() - interval '35 days', now() - interval '35 days', 0),
    ('019a0000-0000-7000-8000-000000000403',
     '019a0000-0000-7000-8000-000000000006', '019a0000-0000-7000-8000-000000000105',
     'Contenedor desbordado en una esquina',
     'Los residuos ocupan parte de la acera y dificultan el paso de peatones.',
     36.7225, -4.4133, 8.0, 'REPORTED',
     now() - interval '10 days', now() - interval '10 days', 0),
    ('019a0000-0000-7000-8000-000000000404',
     '019a0000-0000-7000-8000-000000000006', '019a0000-0000-7000-8000-000000000104',
     'Banco del parque con tablones sueltos',
     'El asiento de un banco tiene piezas desprendidas y necesita revisión.',
     36.7206, -4.4373, 6.0, 'REPORTED',
     now() - interval '8 days', now() - interval '8 days', 0),
    ('019a0000-0000-7000-8000-000000000405',
     '019a0000-0000-7000-8000-000000000001', '019a0000-0000-7000-8000-000000000106',
     'Paso de peatones con pintura desgastada',
     'Las marcas del paso de peatones apenas se distinguen desde la calzada.',
     36.7195, -4.4281, 4.0, 'REPORTED',
     now() - interval '26 days', now() - interval '26 days', 0),
    ('019a0000-0000-7000-8000-000000000406',
     '019a0000-0000-7000-8000-000000000008', '019a0000-0000-7000-8000-000000000104',
     'Rama caída sobre la acera',
     'Una rama grande ocupa el itinerario peatonal y obliga a rodear el obstáculo.',
     36.7137, -4.4342, 7.0, 'REPORTED',
     now() - interval '6 days', now() - interval '6 days', 0),
    ('019a0000-0000-7000-8000-000000000407',
     '019a0000-0000-7000-8000-000000000003', '019a0000-0000-7000-8000-000000000106',
     'Semáforo con funcionamiento intermitente',
     'El semáforo deja de iluminarse durante algunos ciclos y requiere inspección.',
     36.7151, -4.4280, 2.0, 'REPORTED',
     now() - interval '5 days', now() - interval '5 days', 0),
    ('019a0000-0000-7000-8000-000000000408',
     '019a0000-0000-7000-8000-000000000002', '019a0000-0000-7000-8000-000000000103',
     'Socavón pequeño en la calzada',
     'Se ha formado un hundimiento junto a la zona de aparcamiento que puede empeorar.',
     36.7238, -4.4198, 5.0, 'REPORTED',
     now() - interval '3 days', now() - interval '3 days', 0),
    ('019a0000-0000-7000-8000-000000000409',
     '019a0000-0000-7000-8000-000000000001', NULL,
     'Incidencia pendiente de clasificación',
     'Se observa un desperfecto urbano, pero todavía no se ha identificado la categoría.',
     36.7202, -4.4204, NULL, 'REPORTED',
     now() - interval '2 days', now() - interval '2 days', 0),
    ('019a0000-0000-7000-8000-000000000410',
     '019a0000-0000-7000-8000-000000000006', '019a0000-0000-7000-8000-000000000104',
     'Árbol con ramas demasiado bajas',
     'Las ramas de un árbol dificultan el paso por la zona peatonal del parque.',
     36.7250, -4.4300, 10.0, 'REPORTED',
     now() - interval '21 days', now() - interval '21 days', 0),
    ('019a0000-0000-7000-8000-000000000411',
     '019a0000-0000-7000-8000-000000000007', '019a0000-0000-7000-8000-000000000101',
     'Iluminación irregular en una plaza',
     'Dos puntos de luz parpadean por la noche y generan molestias en la plaza.',
     36.7218, -4.4145, 4.0, 'REPORTED',
     now() - interval '1 day', now() - interval '1 day', 0),
    ('019a0000-0000-7000-8000-000000000412',
     '019a0000-0000-7000-8000-000000000008', '019a0000-0000-7000-8000-000000000105',
     'Papeleras llenas junto a la zona verde',
     'Varias papeleras están llenas y hay envoltorios esparcidos alrededor.',
     36.7168, -4.4215, 6.0, 'REPORTED',
     now() - interval '12 hours', now() - interval '12 hours', 0)
ON CONFLICT (id) DO NOTHING;

-- 07. Auditoría visible desde GET /incidents/{id}/history --------------------
-- El servicio solo publica INCIDENT_CREATED e INCIDENT_UPDATED, con el detalle
-- JSON {"version":"<n>"}; no publica revisiones o asignaciones futuras.
INSERT INTO audit_events (
    id, actor_user_id, action, resource_type, resource_id, details, occurred_at
)
SELECT seed.audit_id::uuid, i.reporter_user_id, 'INCIDENT_CREATED',
       'INCIDENT', i.id::text, jsonb_build_object('version', '0'), i.created_at
FROM (VALUES
    ('019a0000-0000-7000-8000-000000000501', '019a0000-0000-7000-8000-000000000401'),
    ('019a0000-0000-7000-8000-000000000502', '019a0000-0000-7000-8000-000000000402'),
    ('019a0000-0000-7000-8000-000000000503', '019a0000-0000-7000-8000-000000000403'),
    ('019a0000-0000-7000-8000-000000000504', '019a0000-0000-7000-8000-000000000404'),
    ('019a0000-0000-7000-8000-000000000505', '019a0000-0000-7000-8000-000000000405'),
    ('019a0000-0000-7000-8000-000000000506', '019a0000-0000-7000-8000-000000000406'),
    ('019a0000-0000-7000-8000-000000000507', '019a0000-0000-7000-8000-000000000407'),
    ('019a0000-0000-7000-8000-000000000508', '019a0000-0000-7000-8000-000000000408'),
    ('019a0000-0000-7000-8000-000000000509', '019a0000-0000-7000-8000-000000000409'),
    ('019a0000-0000-7000-8000-000000000510', '019a0000-0000-7000-8000-000000000410'),
    ('019a0000-0000-7000-8000-000000000511', '019a0000-0000-7000-8000-000000000411'),
    ('019a0000-0000-7000-8000-000000000512', '019a0000-0000-7000-8000-000000000412')
) AS seed(audit_id, incident_id)
JOIN incidents i ON i.id = seed.incident_id::uuid
WHERE TRUE -- desambigua el ON CONFLICT de un JOIN en INSERT ... SELECT
ON CONFLICT (id) DO NOTHING;

-- Corrección sintética de 0401: version=1 y updated_at posterior al alta.
INSERT INTO audit_events (
    id, actor_user_id, action, resource_type, resource_id, details, occurred_at
)
SELECT '019a0000-0000-7000-8000-000000000513', i.reporter_user_id,
       'INCIDENT_UPDATED', 'INCIDENT', i.id::text,
       jsonb_build_object('version', '1'), i.updated_at
FROM incidents i
WHERE i.id = '019a0000-0000-7000-8000-000000000401'
  AND i.version >= 1
ON CONFLICT (id) DO NOTHING;

COMMIT;
