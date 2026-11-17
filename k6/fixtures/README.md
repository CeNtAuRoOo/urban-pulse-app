# Preparación de fixtures k6

Estos son procedimientos para una **sesión futura autorizada**. No hay fixtures precargados ni datos creados por esta entrega. Los scripts fallan en init si falta un token/UUID obligatorio; el preflight de la futura ejecución contrasta cuenta y detalle con la API. Una base recién migrada contiene roles, pero no usuarios, catálogos ni incidencias.

## Aislamiento y cuentas

1. Preparar la configuración privada según [Desarrollo](../../docs/development.md). Detener el Compose habitual para liberar sus puertos y usar un proyecto de pruebas identificado, por ejemplo `urbanpulse-k6`, con su volumen SQL nuevo. Revisar JDBC **local** y Mailpit; cambiar de rama no aísla volúmenes. El archivo privado sigue perteneciendo al responsable y no se copia de otra persona. El bucket S3 sigue siendo externo aunque SQL sea local.
2. Registrar desde el frontend una cuenta sintética por rol necesario, con correo exclusivo de pruebas. Confirmar el código en Mailpit (`http://localhost:9999`) desde el cliente antes de usar rutas de negocio. También se puede usar el contrato: `POST /api/v1/auth/register` con `displayName`, `email`, `password`; después `POST /api/v1/auth/email-verifications/confirm` con `{ "code": "<ocho dígitos recibidos>" }` y el JWT limitado. No guardar contraseña, código ni respuesta de login en Git. El código caduca a los diez minutos.
3. El registro concede CITIZEN; **no hay bootstrap automático de ADMIN**. Si ya hay un ADMIN de pruebas, conceder OPERATOR/ADMIN mediante una invitación global y canjearla con el destinatario verificado. Si la base está vacía, su responsable debe aprovisionar explícitamente el primer ADMIN de pruebas. La siguiente transacción ilustra esa preparación manual exclusivamente en la BD local descartable; no es una migración ni se ejecuta desde k6. El UUID debe obtenerse del perfil de la cuenta de pruebas ya verificada:

   ```sql
   BEGIN;
   -- Sustituir el marcador por la cuenta de pruebas confirmada, nunca por un usuario real.
   INSERT INTO user_roles (user_id, role_code)
   SELECT id, 'ADMIN' FROM users
   WHERE id = '<UUID de la cuenta ADMIN de pruebas>'::uuid
     AND active AND email_verified
     AND EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = users.id AND ur.role_code = 'CITIZEN')
   ON CONFLICT DO NOTHING;
   -- Verificar que solo esa cuenta tiene el rol previsto antes de confirmar.
   SELECT role_code FROM user_roles WHERE user_id = '<UUID de la cuenta ADMIN de pruebas>'::uuid;
   COMMIT;
   ```

   Este acceso SQL de preparación no sustituye el bootstrap productivo pendiente. No retirar CITIZEN ni reutilizar la cuenta ciudadana pura para pruebas de permisos. TECHNICIAN/DEPARTMENT_COORDINATOR no sustituyen OPERATOR/ADMIN.

4. Obtener una vez un JWT con `POST /api/v1/auth/login` (`email`, `password`), usando el cliente local; tomar `accessToken` de la respuesta. Verificar con `GET /api/v1/users/me` que la cuenta está activa, verificada y tiene el rol correspondiente. Usar tokens recién emitidos: duran 7200 s; roles, actividad y `tokenVersion` se comprueban en DB. Cambiar contraseña revoca el token. El preflight de los scripts repite la comprobación de rol sin hacer login.
5. Crear una incidencia ciudadana desde el formulario existente o mediante `POST /api/v1/incidents`: título de 5–150 puntos Unicode, descripción de 10–5000 y `coordinates: { latitude: 36.7213, longitude: -4.4214 }`. `categoryId` es opcional. No enviar estado, prioridad, informante ni versión. Conservar el `id` real de la respuesta 201. No confundir `Location`, `ETag` opaca e `Incident.version` numérica.

## Catálogo de variables y relaciones

| Variable                                    | Preparación y condición                                                                                                                                                                                                                     |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `CITIZEN_TOKEN`                             | Cuenta activa/verificada con CITIZEN sin OPERATOR/ADMIN; no un JWT de registro sin confirmar                                                                                                                                                |
| `OPERATOR_TOKEN`, `ADMIN_TOKEN`             | Cuentas diferentes de personal global cuando se elige ese `ACTOR`; conservan CITIZEN                                                                                                                                                        |
| `CITIZEN_INCIDENT_ID`                       | Incidencia realmente creada por el sujeto del token; obligatoria para smoke/read                                                                                                                                                            |
| `OPERATOR_INCIDENT_ID`, `ADMIN_INCIDENT_ID` | Incidencia visible al personal; puede pertenecer al ciudadano. Obligatoria para smoke/read/staff-read del actor seleccionado                                                                                                                |
| `CATEGORY_ID`                               | Opcional: categoría creada con `POST /categories` por ADMIN (`name`, `description?`, `active?`). Activa para altas; se usa como filtro en el listado administrativo                                                                         |
| `MAP_EXPECTED_INCIDENT_ID`                  | Opcional: ID visible en el viewport a zoom >=18. Debe aparecer como individuo; no sirve un cluster ID. No se exige ninguna presencia si se omite                                                                                            |
| `DUPLICATE_CANDIDATE_ID`                    | Opcional: candidato real del expediente administrativo, sin enlace previo, a <=250 m y ±72 h, dentro de los 20 mejor clasificados. Si se declara, se exige su presencia                                                                     |
| `RUN_ID`                                    | Identificador nuevo de 8..64 caracteres ASCII alfanuméricos o guion; obligatorio solo para escritura. Se recomienda un UUID sin llaves generado con `[guid]::NewGuid().ToString()`, pero no se exige ese formato. No es el ID de incidencia |
| `PAGE`, `PAGE_SIZE`                         | Primera página 1 y tamaño 20 por defecto; probar también páginas profundas y vacías manteniendo el dataset fijo                                                                                                                             |

Para disponer de catálogos no vacíos, ADMIN puede crear un departamento con `name` y `code` único ASCII de 2–20 caracteres. Los GET de catálogos devuelven arrays, no páginas; incluyen elementos inactivos. Una lista vacía es válida y no prueba rendimiento con volumen.

Para tramitación usar una incidencia distinta de la del smoke de escritura. Preparar, a través de las operaciones existentes, una revisión REQUEST_INFORMATION y su respuesta del informante; luego una revisión VALIDATE con categoría activa y una decisión de prioridad MANUAL. Revisar el [contrato de Tramitación](../../docs/api/openapi/paths/tramitacion.yaml) y los [DTO](../../docs/api/openapi/components/schemas/tramitacion.yaml): cada POST exige `expectedIncidentVersion` leído del detalle **actual**; no es `If-Match`. El revisor no puede ser el informante. Estos POST no forman parte de la carga. Las colecciones vacías también son válidas, pero no caracterizan el coste de historiales extensos.

Para Mapas, las incidencias propias son visibles sin validación; las ajenas se publican mediante una revisión VALIDATE histórica. El personal global puede ver todas las de la provincia. Un marcador ajeno con `canAccessPrivateDetail=false` no autoriza a consultar su detalle. Para probar presencia, usar un viewport pequeño alrededor de una incidencia propia conocida y `MAP_EXPECTED_INCIDENT_ID`; para el caso vacío, elegir otro viewport autorizado sin datos y omitir esa variable.

## Datasets comparables

Definir antes de medir tres snapshots locales sintéticos: **S=50**, **M=1.000**, **L=10.000 incidencias**. Son tamaños propuestos, no datos existentes ni capacidades acreditadas. Mantener el mismo reparto por titulares y categoría (p. ej., 50 % para el ciudadano medido, 50 % para una segunda cuenta), coordenadas dentro de Málaga y un subconjunto cercano para Duplicados. Preparar por separado expedientes tramitados para que Mapas público y los historiales no estén vacíos. Anotar el número de filas reales de incidencias, revisiones, respuestas, prioridades, auditoría y categorías, además de la distribución de estados y tamaños de texto.

La preparación se hace **fuera del intervalo de medición**: crear el conjunto por las APIs implementadas mediante una herramienta de fixtures revisada, con número total acotado, sin fotos ni correos repetidos; o restaurar un snapshot sintético propio cuyo esquema/Flyway corresponda al commit ensayado. Esta batería no suministra un sembrador masivo ni considera un dataset importado automáticamente válido. Para S puede prepararse desde el cliente; M/L requieren esa preparación adicional. No usar el smoke de escritura en un bucle como sembrador ni extrapolar sus cuatro peticiones a carga de escritura. Verificar titularidad y eventos reales: insertar solo filas `incidents` no crea historial ni validación pública.

Antes de conservar cada snapshot, comprobar los recuentos en la BD de pruebas y consultar con las identidades previstas el detalle, la página y el mapa seleccionados. Guardar el manifiesto de preparación, identificadores y hash del snapshot en `k6/local/` (ignorado), nunca un volcado de producción. Mantenerlo fijo entre A/B/C/D. Las pruebas SQL sintéticas de `MapPerformancePostgresIT` son evidencia histórica independiente; no son fixtures persistentes del backend Compose ni resultados de k6.

## Residuos y limpieza

Las lecturas no modifican negocio. El smoke de escritura deja una incidencia REPORTED y eventos CREATED/UPDATED; no hay DELETE de incidencias. Identificarla por el título `Prueba k6 <RUN_ID>-1-0` y consultarla con `/incidents/mine` o la BD local. Incluso si el HTTP falla tras el commit, puede existir un alta: no reintentar ciegamente.

Conservar o restaurar el snapshot de **la base exclusiva de pruebas**, después de detener su backend y comprobar nombre del proyecto, volumen y conexión. No se incluye un `TRUNCATE` general ni un `down -v` rutinario; evitar borrar filas por un prefijo sin revisar sus FK/auditoría o eliminar volúmenes ajenos. No hay limpieza S3 porque esta batería no sube objetos. Copias, manifiestos, credenciales e informes son privados y locales; [guía de ejecución](../README.md).
