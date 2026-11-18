-- Exclusivo del PostgreSQL desechable de Compose. V1 debe estar aplicada.
\set ON_ERROR_STOP on
BEGIN;

DO $entorno$
BEGIN
    IF current_setting('urbanpulse.environment', true) IS DISTINCT FROM 'compose-dev'
       OR current_database() <> 'urban-pulse' OR current_user <> 'urbanpulse'
       OR current_setting('server_version_num')::int / 10000 <> 18
       OR NOT EXISTS (SELECT FROM public.flyway_schema_history WHERE version='1' AND success) THEN
        RAISE EXCEPTION 'El destino debe ser PostgreSQL 18 del Compose de desarrollo con V1 aplicada';
    END IF;
END
$entorno$;

-- Un segundo «compose up» puede volver a iniciar el servicio ya terminado.
SELECT pg_advisory_xact_lock(39181000);
SELECT to_regclass('public.dev_seed_completed') IS NOT NULL AS cargada \gset
\if :cargada
    \echo 'La carga de este ciclo ya terminó.'
    COMMIT;
    \quit
\endif

CREATE EXTENSION IF NOT EXISTS pgcrypto;
SELECT set_config('urbanpulse.demo_password_hash', crypt('demo-password', gen_salt('bf', 10)), true) AS hash \gset

-- Cuentas de integración del cliente
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
     true, true, true, true, true, now() - interval '1 year', now()),
    ('019a0000-0000-7000-8000-000000000002', 'Administración Demo',
     'demo.admin@urbanpulse.test', current_setting('urbanpulse.demo_password_hash'),
     true, true, true, true, true, now() - interval '1 year', now()),
    ('019a0000-0000-7000-8000-000000000003', 'Operación Demo',
     'demo.operator@urbanpulse.test', current_setting('urbanpulse.demo_password_hash'),
     true, true, true, true, true, now() - interval '1 year', now()),
    ('019a0000-0000-7000-8000-000000000004', 'Personal Departamental Demo',
     'demo.technician@urbanpulse.test', current_setting('urbanpulse.demo_password_hash'),
     true, true, true, true, true, now() - interval '1 year', now()),
    ('019a0000-0000-7000-8000-000000000005', 'Cuenta Pendiente Demo',
     'demo.pending@urbanpulse.test', current_setting('urbanpulse.demo_password_hash'),
     true, false, true, true, true, now(), now()),
    ('019a0000-0000-7000-8000-000000000006', 'Otra Ciudadana Demo',
     'demo.other@urbanpulse.test', current_setting('urbanpulse.demo_password_hash'),
     true, true, true, true, true, now() - interval '1 year', now()),
    ('019a0000-0000-7000-8000-000000000007', 'Coordinación Demo',
     'demo.coordinator@urbanpulse.test', current_setting('urbanpulse.demo_password_hash'),
     true, true, true, true, false, now() - interval '1 year', now()),
    ('019a0000-0000-7000-8000-000000000008', 'Técnico de Parques Demo',
     'demo.parks@urbanpulse.test', current_setting('urbanpulse.demo_password_hash'),
     true, true, true, true, true, now() - interval '1 year', now()),
    ('019a0000-0000-7000-8000-000000000009', 'Cuenta Inactiva Demo',
     'demo.inactive@urbanpulse.test', current_setting('urbanpulse.demo_password_hash'),
     false, true, false, false, false, now() - interval '1 year', now());

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
);

INSERT INTO user_roles (user_id, role_code) VALUES
    ('019a0000-0000-7000-8000-000000000002', 'ADMIN'),
    ('019a0000-0000-7000-8000-000000000003', 'OPERATOR');

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
     'Semáforos, señales y pasos peatonales.', true);

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
     'DEMO_LIMP', 'Mantenimiento de la limpieza pública.', true);

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
     now() - interval '60 days', now() - interval '25 days');

-- 06. Incidencias del núcleo actualmente implementado -----------------------
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
     now() - interval '12 hours', now() - interval '12 hours', 0);

-- 07. Auditoría visible desde GET /incidents/{id}/history --------------------
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
JOIN incidents i ON i.id = seed.incident_id::uuid;

-- Corrección sintética de 0401: version=1 y updated_at posterior al alta.
INSERT INTO audit_events (
    id, actor_user_id, action, resource_type, resource_id, details, occurred_at
)
SELECT '019a0000-0000-7000-8000-000000000513', i.reporter_user_id,
       'INCIDENT_UPDATED', 'INCIDENT', i.id::text,
       jsonb_build_object('version', '1'), i.updated_at
FROM incidents i
WHERE i.id = '019a0000-0000-7000-8000-000000000401'
  AND i.version >= 1;


-- Cuentas y expedientes de la batería k6
-- 01. Cuentas: CITIZEN puro, ADMIN, OPERATOR, otro titular, tecnico y coordinador,
-- mas dos cuentas negativas. Los JWT se obtienen DESPUES mediante /auth/login.
WITH creds AS (
  SELECT current_setting('urbanpulse.demo_password_hash') AS hash
)
INSERT INTO users
  (id, display_name, email, password_hash, active, email_verified,
   notify_in_app, notify_email, notify_push, created_at, updated_at)
SELECT seed.id::uuid, seed.name, seed.email, creds.hash,
       seed.active, seed.verified, true, true, true,
       CASE WHEN seed.verified THEN now() - interval '1 year' ELSE now() END, now()
FROM (VALUES
  ('029b0000-0000-7000-8000-000000000001', 'K6 Ciudadano Principal', 'k6.citizen@urbanpulse.test', true, true),
  ('029b0000-0000-7000-8000-000000000002', 'K6 Administrador', 'k6.admin@urbanpulse.test', true, true),
  ('029b0000-0000-7000-8000-000000000003', 'K6 Operador', 'k6.operator@urbanpulse.test', true, true),
  ('029b0000-0000-7000-8000-000000000004', 'K6 Segundo Ciudadano', 'k6.other@urbanpulse.test', true, true),
  ('029b0000-0000-7000-8000-000000000005', 'K6 Tecnico', 'k6.technician@urbanpulse.test', true, true),
  ('029b0000-0000-7000-8000-000000000006', 'K6 Coordinador', 'k6.coordinator@urbanpulse.test', true, true),
  ('029b0000-0000-7000-8000-000000000007', 'K6 Pendiente', 'k6.pending@urbanpulse.test', true, false),
  ('029b0000-0000-7000-8000-000000000008', 'K6 Inactivo', 'k6.inactive@urbanpulse.test', false, true)
) AS seed(id, name, email, active, verified)
CROSS JOIN creds;

INSERT INTO user_roles (user_id, role_code)
SELECT u.id, 'CITIZEN'
FROM users u
WHERE u.id::text LIKE '029b0000-0000-7000-8000-00000000000%'
  AND u.id IN (
    '029b0000-0000-7000-8000-000000000001', '029b0000-0000-7000-8000-000000000002',
    '029b0000-0000-7000-8000-000000000003', '029b0000-0000-7000-8000-000000000004',
    '029b0000-0000-7000-8000-000000000005', '029b0000-0000-7000-8000-000000000006',
    '029b0000-0000-7000-8000-000000000007', '029b0000-0000-7000-8000-000000000008'
  );
INSERT INTO user_roles (user_id, role_code) VALUES
  ('029b0000-0000-7000-8000-000000000002', 'ADMIN'),
  ('029b0000-0000-7000-8000-000000000003', 'OPERATOR');

-- 02. Catalogos y adscripciones; los roles departamentales no son globales.
INSERT INTO categories (id, name, description, active) VALUES
  ('029b0000-0000-7000-8000-000000000101', 'K6 Alumbrado', 'Incidencias sinteticas de iluminacion', true),
  ('029b0000-0000-7000-8000-000000000102', 'K6 Via publica', 'Incidencias sinteticas de pavimento', true),
  ('029b0000-0000-7000-8000-000000000103', 'K6 Limpieza', 'Incidencias sinteticas de limpieza', true),
  ('029b0000-0000-7000-8000-000000000104', 'K6 Parques', 'Incidencias sinteticas de arbolado', true),
  ('029b0000-0000-7000-8000-000000000105', 'K6 Movilidad', 'Incidencias sinteticas de semaforos', true),
  ('029b0000-0000-7000-8000-000000000106', 'K6 Inactiva', 'Prueba de filtros sobre categoria inactiva', false);

INSERT INTO departments (id, name, code, description, active) VALUES
  ('029b0000-0000-7000-8000-000000000201', 'K6 Mantenimiento', 'K6_MANT', 'Departamento ficticio de mantenimiento', true),
  ('029b0000-0000-7000-8000-000000000202', 'K6 Parques y jardines', 'K6_PARQ', 'Departamento ficticio de parques', true),
  ('029b0000-0000-7000-8000-000000000203', 'K6 Movilidad urbana', 'K6_MOV', 'Departamento ficticio de movilidad', true),
  ('029b0000-0000-7000-8000-000000000204', 'K6 Departamento inactivo', 'K6_INACT', 'Prueba de catalogo inactivo', false);

INSERT INTO department_memberships (id, user_id, department_id, role_code, joined_at, left_at) VALUES
  ('029b0000-0000-7000-8000-000000000301', '029b0000-0000-7000-8000-000000000005',
   '029b0000-0000-7000-8000-000000000201', 'TECHNICIAN', now() - interval '14 days', NULL),
  ('029b0000-0000-7000-8000-000000000302', '029b0000-0000-7000-8000-000000000006',
   '029b0000-0000-7000-8000-000000000202', 'DEPARTMENT_COORDINATOR', now() - interval '13 days', NULL),
  ('029b0000-0000-7000-8000-000000000303', '029b0000-0000-7000-8000-000000000005',
   '029b0000-0000-7000-8000-000000000202', 'TECHNICIAN', now() - interval '60 days', now() - interval '30 days');

-- 03. OCHO expedientes especiales.
-- 0401: OPERATOR_INCIDENT_ID = tramitacion completa (solicitud, respuesta,
--       VALIDATE y prioridad); permite tambien comprobar duplicados con 0402.
-- 0402: DUPLICATE_CANDIDATE_ID: proximo y creado solo 2 h despues de 0401.
-- 0403: CITIZEN_INCIDENT_ID: propiedad de la cuenta ciudadana, actualizado.
-- 0404: rechazado. 0405: pendiente de informacion. 0406: validado + prioridad.
-- 0407: solicitud respondida, vuelta a REPORTED. 0408: validado + prioridad.
INSERT INTO incidents
 (id, reporter_user_id, category_id, title, description,
  latitude, longitude, accuracy_meters, status, priority,
  created_at, updated_at, version)
SELECT s.id::uuid, s.reporter::uuid, s.category::uuid, s.title, s.description,
       s.lat, s.lon, 5.0, s.status, s.priority,
       now() - s.created_hours * interval '1 hour',
       now() - s.updated_hours * interval '1 hour', s.version
FROM (VALUES
 ('029b0000-0000-7000-8000-000000000401','029b0000-0000-7000-8000-000000000004','029b0000-0000-7000-8000-000000000104',
  'K6 Tronco caido con revision','El arbol bloquea una acera, se pidio informacion y se valido el expediente.',
  36.721300, -4.421400, 'VALIDATED','HIGH',4,48,12),
 ('029b0000-0000-7000-8000-000000000402','029b0000-0000-7000-8000-000000000001','029b0000-0000-7000-8000-000000000104',
  'K6 Candidato duplicado cercano','Incidencia ficticia muy cercana a la anterior, sin vinculo de duplicidad.',
  36.721360, -4.421360, 'REPORTED',NULL,0,46,46),
 ('029b0000-0000-7000-8000-000000000403','029b0000-0000-7000-8000-000000000001','029b0000-0000-7000-8000-000000000101',
  'K6 Farola apagada y corregida','El ciudadano rectifico la descripcion de una farola que no enciende.',
  36.731200, -4.434100, 'REPORTED',NULL,1,240,216),
 ('029b0000-0000-7000-8000-000000000404','029b0000-0000-7000-8000-000000000004','029b0000-0000-7000-8000-000000000102',
  'K6 Denuncia rechazada','El expediente de pavimento fue revisado y finalmente rechazado.',
  36.742000, -4.453100, 'REJECTED',NULL,1,288,240),
 ('029b0000-0000-7000-8000-000000000405','029b0000-0000-7000-8000-000000000001','029b0000-0000-7000-8000-000000000103',
  'K6 Informacion pendiente','Se solicito al informante una aclaracion que sigue pendiente.',
  36.718000, -4.448100, 'AWAITING_INFORMATION',NULL,1,120,100),
 ('029b0000-0000-7000-8000-000000000406','029b0000-0000-7000-8000-000000000001','029b0000-0000-7000-8000-000000000105',
  'K6 Semaforo validado','El operador valido el problema y establecio prioridad media.',
  36.746000, -4.415000, 'VALIDATED','MEDIUM',2,144,112),
 ('029b0000-0000-7000-8000-000000000407','029b0000-0000-7000-8000-000000000004','029b0000-0000-7000-8000-000000000102',
  'K6 Solicitud ya respondida','La persona titular contesto y el expediente retorno a REPORTED.',
  36.750000, -4.465000, 'REPORTED',NULL,2,80,72),
 ('029b0000-0000-7000-8000-000000000408','029b0000-0000-7000-8000-000000000004','029b0000-0000-7000-8000-000000000103',
  'K6 Recogida validada','Una segunda incidencia validada permite probar la publicacion en mapas.',
  37.020000, -4.564000, 'VALIDATED','LOW',2,50,30)
) AS s(id, reporter, category, title, description, lat, lon, status, priority,
       version, created_hours, updated_hours);

-- 04. Dataset escalable de lecturas, catálogos, páginas y geografía provincial.
-- Las cinco agrupaciones permanecen separadas de 0401 por >250 metros.
-- De cada 10 incidentes: 1 VALIDATED con revisión real; de cada 20,
-- una prioridad manual real. El resto queda REPORTED, sin historial artificial.
-- Los dos últimos casos cubren un vértice del contorno y una posición exterior válida para el alta.
WITH gen AS (
 SELECT n,
        ('029b0000-0000-7000-8000-' || lpad((1000+n)::text,12,'0'))::uuid AS id,
        CASE WHEN (n / 10 + n) % 2 = 0 THEN '029b0000-0000-7000-8000-000000000001'::uuid
             ELSE '029b0000-0000-7000-8000-000000000004'::uuid END AS reporter,
        (ARRAY[
          '029b0000-0000-7000-8000-000000000101'::uuid,
          '029b0000-0000-7000-8000-000000000102'::uuid,
          '029b0000-0000-7000-8000-000000000103'::uuid,
          '029b0000-0000-7000-8000-000000000104'::uuid,
          '029b0000-0000-7000-8000-000000000105'::uuid
        ])[((n / 10 + n) % 5) + 1] AS category,
        (ARRAY[36.735,37.019,36.750,36.515,36.783])[((n / 10 + n) % 5) + 1]
          + ((n * 13) % 23) * 0.00025 AS lat,
        (ARRAY[-4.440,-4.560,-5.170,-4.885,-4.102])[((n / 10 + n) % 5) + 1]
          + ((n * 7) % 23) * 0.00025 AS lon,
        now() - (2 + (n % 90)) * interval '1 day' AS at,
        n % 10 = 0 AS validated,
        n % 20 = 0 AS prioritized
 FROM generate_series(9, 9988) AS n
)
INSERT INTO incidents
 (id, reporter_user_id, category_id, title, description,
  latitude, longitude, accuracy_meters, status, priority, created_at, updated_at, version)
SELECT id, reporter, category,
       'K6 Incidencia sintetica ' || n,
       'Incidencia automatica con datos sinteticos para medir API, paginacion y mapas. Numero ' || n || '.',
       CASE n WHEN 9987 THEN map_lat_v1((map_vertices_v1())[1]) WHEN 9988 THEN 38.0 ELSE lat END,
       CASE n WHEN 9987 THEN map_lon_v1((map_vertices_v1())[1]) WHEN 9988 THEN -4.5 ELSE lon END, 5.0,
       CASE WHEN validated THEN 'VALIDATED' ELSE 'REPORTED' END,
       CASE WHEN prioritized THEN 'MEDIUM' ELSE NULL END,
       at, at + CASE WHEN prioritized THEN interval '1 hour'
                    WHEN validated THEN interval '30 minutes'
                    ELSE interval '0 seconds' END,
       CASE WHEN prioritized THEN 2 WHEN validated THEN 1 ELSE 0 END
FROM gen;

-- 05. Revisiones, respuestas, prioridades y transiciones: entidades coherentes
-- con ProcessingService y todas sus relaciones obligatorias.
INSERT INTO incident_reviews
 (id, incident_id, reviewed_by_user_id, decision, reason, reviewed_at, resulting_status)
SELECT s.id::uuid, s.incident::uuid, '029b0000-0000-7000-8000-000000000003'::uuid,
       s.decision, s.reason, now() - s.hours * interval '1 hour', s.status
FROM (VALUES
 ('029b0000-0000-7000-8000-000000011001','029b0000-0000-7000-8000-000000000401','REQUEST_INFORMATION',
  'Aclarar fecha y alcance del arbol caido.', 'AWAITING_INFORMATION',36),
 ('029b0000-0000-7000-8000-000000011002','029b0000-0000-7000-8000-000000000401','VALIDATE',
  NULL, 'VALIDATED',20),
 ('029b0000-0000-7000-8000-000000011004','029b0000-0000-7000-8000-000000000404','REJECT',
  'Los datos aportados no permiten acreditar la incidencia.', 'REJECTED',240),
 ('029b0000-0000-7000-8000-000000011005','029b0000-0000-7000-8000-000000000405','REQUEST_INFORMATION',
  'Falta una descripcion adicional del lugar exacto.', 'AWAITING_INFORMATION',100),
 ('029b0000-0000-7000-8000-000000011006','029b0000-0000-7000-8000-000000000406','VALIDATE',
  NULL, 'VALIDATED',126),
 ('029b0000-0000-7000-8000-000000011007','029b0000-0000-7000-8000-000000000407','REQUEST_INFORMATION',
  'Indicar si el problema persiste al amanecer.', 'AWAITING_INFORMATION',75),
 ('029b0000-0000-7000-8000-000000011008','029b0000-0000-7000-8000-000000000408','VALIDATE',
  NULL, 'VALIDATED',42)
) s(id, incident, decision, reason, status, hours);

-- Revision VALIDATE para cada expediente sintetico que ya tiene ese estado.
INSERT INTO incident_reviews
 (id, incident_id, reviewed_by_user_id, decision, reason, reviewed_at, resulting_status)
SELECT ('029b0000-0000-7000-8000-' || lpad((50000+(right(i.id::text,12))::bigint)::text,12,'0'))::uuid,
       i.id, '029b0000-0000-7000-8000-000000000003'::uuid,
       'VALIDATE', NULL, i.created_at + interval '30 minutes', 'VALIDATED'
FROM incidents i
WHERE i.id::text >= '029b0000-0000-7000-8000-000000001009'
  AND i.id::text <= '029b0000-0000-7000-8000-000000011000'
  AND i.id::text LIKE '029b0000-0000-7000-8000-%'
  AND i.status = 'VALIDATED';

INSERT INTO incident_information_responses
 (id, incident_id, review_id, request_decision, responded_by_user_id, message, responded_at)
VALUES
 ('029b0000-0000-7000-8000-000000012001','029b0000-0000-7000-8000-000000000401',
  '029b0000-0000-7000-8000-000000011001','REQUEST_INFORMATION',
  '029b0000-0000-7000-8000-000000000004', 'El obstaculo cubre la mitad de la acera.',now()-interval '28 hours'),
 ('029b0000-0000-7000-8000-000000012007','029b0000-0000-7000-8000-000000000407',
  '029b0000-0000-7000-8000-000000011007','REQUEST_INFORMATION',
  '029b0000-0000-7000-8000-000000000004', 'El desperfecto continua y tengo datos adicionales.',now()-interval '72 hours');

INSERT INTO priority_decisions
 (id, incident_id, decided_by_user_id, priority, source, justification, decided_at)
VALUES
 ('029b0000-0000-7000-8000-000000013001','029b0000-0000-7000-8000-000000000401',
  '029b0000-0000-7000-8000-000000000003','HIGH','MANUAL',
  'Riesgo de caida al caminar por la acera.',now()-interval '12 hours'),
 ('029b0000-0000-7000-8000-000000013006','029b0000-0000-7000-8000-000000000406',
  '029b0000-0000-7000-8000-000000000003','MEDIUM','MANUAL',
  'Necesita inspeccion municipal planificada.',now()-interval '112 hours'),
 ('029b0000-0000-7000-8000-000000013008','029b0000-0000-7000-8000-000000000408',
  '029b0000-0000-7000-8000-000000000003','LOW','MANUAL',
  'Puede programarse dentro del mantenimiento ordinario.',now()-interval '30 hours');

INSERT INTO priority_decisions
 (id, incident_id, decided_by_user_id, priority, source, justification, decided_at)
SELECT ('029b0000-0000-7000-8000-' || lpad((70000+(right(i.id::text,12))::bigint)::text,12,'0'))::uuid,
       i.id, '029b0000-0000-7000-8000-000000000003'::uuid,
       'MEDIUM','MANUAL','Prioridad manual sintetica para expediente validado.',
       i.created_at + interval '1 hour'
FROM incidents i
WHERE i.id::text >= '029b0000-0000-7000-8000-000000001009'
  AND i.id::text <= '029b0000-0000-7000-8000-000000011000'
  AND i.id::text LIKE '029b0000-0000-7000-8000-%'
  AND i.status = 'VALIDATED' AND i.priority = 'MEDIUM';

INSERT INTO status_changes
 (id, incident_id, changed_by_user_id, from_status, to_status, reason, changed_at)
SELECT s.id::uuid, s.incident::uuid, s.actor::uuid, s.from_status, s.to_status,
       s.reason, now() - s.hours * interval '1 hour'
FROM (VALUES
 ('029b0000-0000-7000-8000-000000010001','029b0000-0000-7000-8000-000000000401','029b0000-0000-7000-8000-000000000003','REPORTED','AWAITING_INFORMATION','Aclarar fecha y alcance del arbol caido.',36),
 ('029b0000-0000-7000-8000-000000010002','029b0000-0000-7000-8000-000000000401','029b0000-0000-7000-8000-000000000004','AWAITING_INFORMATION','REPORTED',NULL,28),
 ('029b0000-0000-7000-8000-000000010003','029b0000-0000-7000-8000-000000000401','029b0000-0000-7000-8000-000000000003','REPORTED','VALIDATED',NULL,20),
 ('029b0000-0000-7000-8000-000000010004','029b0000-0000-7000-8000-000000000404','029b0000-0000-7000-8000-000000000003','REPORTED','REJECTED','Los datos aportados no permiten acreditar la incidencia.',240),
 ('029b0000-0000-7000-8000-000000010005','029b0000-0000-7000-8000-000000000405','029b0000-0000-7000-8000-000000000003','REPORTED','AWAITING_INFORMATION','Falta una descripcion adicional del lugar exacto.',100),
 ('029b0000-0000-7000-8000-000000010006','029b0000-0000-7000-8000-000000000406','029b0000-0000-7000-8000-000000000003','REPORTED','VALIDATED',NULL,126),
 ('029b0000-0000-7000-8000-000000010007','029b0000-0000-7000-8000-000000000407','029b0000-0000-7000-8000-000000000003','REPORTED','AWAITING_INFORMATION','Indicar si el problema persiste al amanecer.',75),
 ('029b0000-0000-7000-8000-000000010008','029b0000-0000-7000-8000-000000000407','029b0000-0000-7000-8000-000000000004','AWAITING_INFORMATION','REPORTED',NULL,72),
 ('029b0000-0000-7000-8000-000000010009','029b0000-0000-7000-8000-000000000408','029b0000-0000-7000-8000-000000000003','REPORTED','VALIDATED',NULL,42)
) s(id, incident, actor, from_status, to_status, reason, hours);

INSERT INTO status_changes
 (id, incident_id, changed_by_user_id, from_status, to_status, changed_at)
SELECT ('029b0000-0000-7000-8000-' || lpad((60000+(right(i.id::text,12))::bigint)::text,12,'0'))::uuid,
       i.id, '029b0000-0000-7000-8000-000000000003'::uuid,
       'REPORTED','VALIDATED', i.created_at + interval '30 minutes'
FROM incidents i
WHERE i.id::text >= '029b0000-0000-7000-8000-000000001009'
  AND i.id::text <= '029b0000-0000-7000-8000-000000011000'
  AND i.id::text LIKE '029b0000-0000-7000-8000-%'
  AND i.status = 'VALIDATED';

-- 06. Auditoria: GET /history solo proyecta acciones INCIDENT_* esperadas.
-- Todo evento de personal oculta al actor en el DTO publico.
INSERT INTO audit_events
 (id, actor_user_id, action, resource_type, resource_id, details, occurred_at)
SELECT ('029b0000-0000-7000-8000-' || lpad((200000+(right(i.id::text,12))::bigint)::text,12,'0'))::uuid,
       i.reporter_user_id, 'INCIDENT_CREATED','INCIDENT',i.id::text,
       jsonb_build_object('version','0'),i.created_at
FROM incidents i
WHERE (i.id::text BETWEEN '029b0000-0000-7000-8000-000000000401'
                      AND '029b0000-0000-7000-8000-000000000408'
       OR i.id::text BETWEEN '029b0000-0000-7000-8000-000000001009'
                         AND '029b0000-0000-7000-8000-000000011000');

INSERT INTO audit_events
 (id, actor_user_id, action, resource_type, resource_id, details, occurred_at)
SELECT ('029b0000-0000-7000-8000-' || lpad((700000+(right(i.id::text,12))::bigint)::text,12,'0'))::uuid,
       i.reporter_user_id, 'INCIDENT_UPDATED','INCIDENT',i.id::text,
       jsonb_build_object('version', '1'),i.updated_at
FROM incidents i
WHERE i.id='029b0000-0000-7000-8000-000000000403';

INSERT INTO audit_events
 (id, actor_user_id, action, resource_type, resource_id, details, occurred_at)
SELECT ('029b0000-0000-7000-8000-' || lpad((300000+(right(r.id::text,12))::bigint)::text,12,'0'))::uuid,
       r.reviewed_by_user_id,'INCIDENT_REVIEWED','INCIDENT',r.incident_id::text,
       jsonb_build_object('reviewId',r.id::text,'decision',r.decision)
         || jsonb_build_object('previousCategoryId', i.category_id::text)
         || CASE WHEN r.decision = 'VALIDATE' THEN jsonb_build_object('categoryId', i.category_id::text) ELSE '{}'::jsonb END
         || CASE WHEN r.reason IS NOT NULL THEN jsonb_build_object('reason',r.reason) ELSE '{}'::jsonb END,
       r.reviewed_at
FROM incident_reviews r JOIN incidents i ON i.id = r.incident_id
WHERE r.id::text LIKE '029b0000-0000-7000-8000-%'
  AND (r.id::text BETWEEN '029b0000-0000-7000-8000-000000011001'
                     AND '029b0000-0000-7000-8000-000000011008'
       OR r.id::text BETWEEN '029b0000-0000-7000-8000-000000051009'
                         AND '029b0000-0000-7000-8000-000000061000');

INSERT INTO audit_events
 (id, actor_user_id, action, resource_type, resource_id, details, occurred_at)
SELECT ('029b0000-0000-7000-8000-' || lpad((500000+(right(r.id::text,12))::bigint)::text,12,'0'))::uuid,
       r.responded_by_user_id,'INCIDENT_INFORMATION_RESPONDED','INCIDENT',r.incident_id::text,
       jsonb_build_object('reviewId',r.review_id::text,'responseId',r.id::text),r.responded_at
FROM incident_information_responses r
WHERE r.id IN ('029b0000-0000-7000-8000-000000012001',
                '029b0000-0000-7000-8000-000000012007');

INSERT INTO audit_events
 (id, actor_user_id, action, resource_type, resource_id, details, occurred_at)
SELECT ('029b0000-0000-7000-8000-' || lpad((600000+(right(p.id::text,12))::bigint)::text,12,'0'))::uuid,
       p.decided_by_user_id,'INCIDENT_PRIORITY_CHANGED','INCIDENT',p.incident_id::text,
       jsonb_build_object('decisionId',p.id::text,'priority',p.priority),p.decided_at
FROM priority_decisions p
WHERE p.id IN ('029b0000-0000-7000-8000-000000013001',
               '029b0000-0000-7000-8000-000000013006',
               '029b0000-0000-7000-8000-000000013008')
   OR (p.id::text BETWEEN '029b0000-0000-7000-8000-000000071009'
                      AND '029b0000-0000-7000-8000-000000081000');

-- 07. Un enlace confirmado ajeno a 0401/0402 demuestra que candidatos
-- vinculados se excluyen sin arruinar la prueba DUPLICATE_CANDIDATE_ID.
INSERT INTO duplicate_links
 (id, incident_id, candidate_incident_id, linked_by_user_id, linked_at, reason)
VALUES
 ('029b0000-0000-7000-8000-000000095001',
  '029b0000-0000-7000-8000-000000001009',
  '029b0000-0000-7000-8000-000000001010',
  '029b0000-0000-7000-8000-000000000003',now()-interval '10 hours',
  'Relacion sintetica ya confirmada, no es el candidato de la prueba k6');

INSERT INTO audit_events (id, actor_user_id, action, resource_type, resource_id, details, occurred_at)
SELECT '029b0000-0000-7000-8000-000000095002'::uuid, linked_by_user_id, 'DUPLICATE_LINK_CONFIRMED', 'DUPLICATE_LINK', id::text,
       jsonb_build_object('incidentId', incident_id::text, 'candidateIncidentId', candidate_incident_id::text), linked_at
FROM duplicate_links;

-- Cada decisión conserva la versión y el estado en el momento de la operación.
WITH eventos AS (
    SELECT id, row_number() OVER (PARTITION BY resource_id ORDER BY occurred_at, id) - 1 AS version,
           CASE action
               WHEN 'INCIDENT_REVIEWED' THEN (SELECT resulting_status FROM incident_reviews r
                                              WHERE r.id = (a.details->>'reviewId')::uuid)
               WHEN 'INCIDENT_PRIORITY_CHANGED' THEN 'VALIDATED'
               ELSE 'REPORTED'
           END AS status
    FROM audit_events a WHERE resource_type = 'INCIDENT'
)
UPDATE audit_events a SET details = a.details || jsonb_build_object('version', e.version::text, 'status', e.status)
FROM eventos e WHERE a.id = e.id AND a.action IN
    ('INCIDENT_REVIEWED', 'INCIDENT_INFORMATION_RESPONDED', 'INCIDENT_PRIORITY_CHANGED');

-- 08. Pruebas de coherencia antes de confirmar cualquier insercion.
DO $verify$
DECLARE
  source_id uuid := '029b0000-0000-7000-8000-000000000401';
  candidate_id uuid := '029b0000-0000-7000-8000-000000000402';
  own_id uuid := '029b0000-0000-7000-8000-000000000403';
  target int := 9988;
  n int;
BEGIN
  IF (SELECT count(*) FROM incidents) <> 10000 OR (SELECT count(*) FROM users) <> 17
     OR EXISTS (SELECT FROM incidents i JOIN users u ON u.id=i.reporter_user_id WHERE i.created_at < u.created_at)
     OR EXISTS (SELECT FROM incidents i WHERE version <>
         (SELECT count(*) - 1 FROM audit_events a WHERE a.resource_type='INCIDENT' AND a.resource_id=i.id::text))
     OR (SELECT count(*) FROM department_memberships WHERE user_id='019a0000-0000-7000-8000-000000000004'
         AND left_at IS NULL AND role_code='TECHNICIAN') <> 1 THEN
    RAISE EXCEPTION 'Recuento, historial de versiones o pertenencia técnica incompatible';
  END IF;
  SELECT count(*) INTO n FROM incidents WHERE
    id::text BETWEEN '029b0000-0000-7000-8000-000000000401' AND '029b0000-0000-7000-8000-000000000408'
    OR id::text BETWEEN '029b0000-0000-7000-8000-000000001009' AND '029b0000-0000-7000-8000-000000011000';
  IF n <> target THEN
    RAISE EXCEPTION 'Incidencias K6: esperadas %, encontradas %. Se revierte todo.', target, n;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM incidents WHERE id=own_id
                 AND reporter_user_id='029b0000-0000-7000-8000-000000000001') THEN
    RAISE EXCEPTION 'El fixture de CITIZEN no pertenece a la cuenta correcta.';
  END IF;
  IF (SELECT count(*) FROM incident_reviews WHERE incident_id=source_id) <> 2
    OR (SELECT count(*) FROM incident_information_responses WHERE incident_id=source_id) <> 1
    OR (SELECT count(*) FROM priority_decisions WHERE incident_id=source_id) <> 1
    OR (SELECT count(*) FROM status_changes WHERE incident_id=source_id) <> 3
    OR (SELECT count(*) FROM audit_events WHERE resource_type='INCIDENT' AND resource_id=source_id::text) <> 5 THEN
    RAISE EXCEPTION 'El expediente completo de tramitacion no es coherente.';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM incidents a JOIN incidents b ON a.id=source_id AND b.id=candidate_id
    WHERE abs(extract(epoch FROM (a.created_at-b.created_at))) <= 259200
      AND 12742000 * asin(sqrt(
        power(sin(radians(b.latitude-a.latitude)/2),2)
        +cos(radians(a.latitude))*cos(radians(b.latitude))
        *power(sin(radians(b.longitude-a.longitude)/2),2))) <= 250
      AND NOT EXISTS (SELECT 1 FROM duplicate_links l WHERE
        (l.incident_id=a.id AND l.candidate_incident_id=b.id)
         OR (l.incident_id=b.id AND l.candidate_incident_id=a.id))
  ) THEN
    RAISE EXCEPTION 'El candidato de duplicados debe existir y quedar a <250m / +/-72 h.';
  END IF;
  IF (SELECT count(*) FROM users WHERE email LIKE 'k6.%@urbanpulse.test') <> 8 THEN
    RAISE EXCEPTION 'Cuentas k6 faltantes o extra inesperadas.';
  END IF;
END
$verify$;

CREATE TABLE public.dev_seed_completed (loaded_at timestamptz PRIMARY KEY);
INSERT INTO public.dev_seed_completed VALUES (now());

SELECT status AS estado, count(*) AS incidencias FROM incidents GROUP BY status ORDER BY status;
COMMIT;
