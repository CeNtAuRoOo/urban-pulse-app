# ADR-0015: Almacenamiento externo y gestión de evidencias fotográficas

- **Estado:** Aceptado e implementado.
- **Fecha:** 2026-11-04; revisión integral: 2026-11-11.
- **Ámbito:** RF05 y aportación de evidencias de RF13; backend, SQL y almacenamiento privado.

## Contexto y alternativas

Una incidencia urbana es el recurso central. Sus fotografías privadas se guardan en Neon Object Storage y sus metadatos en PostgreSQL. Se conserva el cliente S3 existente; guardar BYTEA en SQL o archivos en el disco de cada instancia mezclaría responsabilidades o dificultaría persistencia compartida.

## Contrato y autorización

Hay cinco operaciones bajo `/api/v1/incidents/{incidentId}/attachments`: POST, GET colección, GET `/{attachmentId}`, GET `/{attachmentId}/content` y DELETE `/{attachmentId}`. No existe colección global pública. POST devuelve 201 y Location relativa al recurso; DELETE devuelve 204 sin cuerpo. El listado no pagina y ordena `createdAt DESC, id DESC`.

`IncidentAccess` comparte con `IncidentService` la política vigente: cuenta activa, tokenVersion actual, rol base CITIZEN y correo verificado; informante u OPERATOR/ADMIN global. TECHNICIAN/DEPARTMENT_COORDINATOR no conceden acceso transversal; conservan acceso como informantes. Todas las operaciones revalidan incidencia y, cuando procede, pareja incidencia/evidencia. Inexistencia y falta de acceso se ocultan con 404. Subir o borrar no modifica el estado de incidencia y funciona en todos sus estados, incluidos RESOLVED y CLOSED.

El principal autenticado es `Jwt`; el controlador extrae sujeto y versión mediante `DatabaseJwtAuthenticationConverter`, nunca recibe una entidad User. Las respuestas usan `Cache-Control: no-store`; la descarga usa MIME validado, longitud real y Content-Disposition attachment con nombre UTF-8 seguro.

`AttachmentDTO` siempre contiene id, incidentId, originalFilename, contentType MIME, sizeBytes, uploadedBy UUID, createdAt y description (null si ausente). No expone storageKey ni URLs S3. UUIDv7 público y clave UUID aleatoria privada son independientes.

## Validación y memoria

Solo JPEG (.jpg y .jpeg) y PNG, determinados por firma y decodificación completa, sin confiar en extensión o MIME declarado. Entrada y resultado tienen límites independientes de **10 MiB (10485760 bytes)**. Multipart admite 11 MiB por petición para su sobrecoste y guarda las partes en disco; la aplicación vuelve a limitar la lectura, también en llamadas internas. Excesos previos al controlador se traducen a 413 ATTACHMENT_TOO_LARGE.

Antes de decodificar se comprueba un máximo de **16 millones de píxeles** y **8192 por lado**. Sustituye el antiguo techo de 40 millones: dos rasters de cuatro bytes por píxel pueden consumir unos 128 MB, además de buffers y sobrecoste del decodificador. No equivale a una cuota global de RAM; dimensionamiento y límites de concurrencia del despliegue siguen siendo necesarios. La caché del escritor se acota antes de aceptar bytes, evitando crecer hasta un resultado ilimitado.

Se rechazan advertencias de decodificación, truncamientos, formatos incompatibles y CRC/chunks PNG inválidos. Se recodifican únicamente píxeles en un raster limpio, sin copiar EXIF/GPS, texto ni perfiles originales; PNG conserva alfa. JPEG puede perder algo de calidad por la recodificación. La descripción admite 300 puntos Unicode, coincidiendo con PostgreSQL y OpenAPI. El nombre descarta rutas, controles y caracteres de formato; limita a 128 puntos Unicode y conserva extensiones fotográficas. Un nombre vacío se convierte en attachment, nunca en ruta ni clave S3.

## Persistencia y concurrencia

La única migración [V1](../../backend/src/main/resources/db/migration/V1__initial_schema.sql) crea attachments con incident_id obligatorio, FK RESTRICT a incidencia/autor, storage_key única, checks de formato/tamaño/fechas/nombre e índice parcial de activas ordenadas. La consolidación está autorizada solo antes de la primera aplicación de Flyway, sobre bases vacías; no es una actualización de bases existentes.

Una incidencia admite **cinco evidencias activas en total**, sumando todos sus autores. Flujo de alta:

1. Revalidar cuenta y visibilidad en una transacción corta.
2. Leer, validar y sanear; subir al bucket fuera de SQL.
3. En nueva transacción, revalidar cuenta bloqueando usuario y después incidencia con PESSIMISTIC_WRITE. Contar activas, rechazar la sexta con 409 ATTACHMENT_LIMIT_REACHED, insertar metadatos y auditoría.
4. Esperar la confirmación SQL. Ante fallo de flush, commit, acceso o capacidad, intentar DELETE compensatorio del objeto.

El método suspende transacciones externas para que la confirmación termine antes de devolver éxito. El bloqueo de incidencia coordina instancias y también las bajas. Si falla la compensación, conserva la causa primaria y la excepción suprimida; registra un evento interno con clave opaca y tipos de error para reconciliación, nunca en respuestas públicas. No hay atomicidad distribuida: una caída de proceso o limpieza fallida puede dejar un objeto huérfano.

## Baja, auditoría y descarga

DELETE establece deletedAt una sola vez y conserva fila, autor, FK y objeto S3. Una repetición devuelve 404 y mantiene el estado final; no hay restauración HTTP. La baja libera una plaza y queda excluida de colección, detalle y contenido.

ATTACHMENT_CREATED y ATTACHMENT_DELETED usan AuditEvent, recurso ATTACHMENT y únicamente incidentId en detalles, en la misma transacción SQL. No se amplía el historial público de incidencias ni sus filtros. La limpieza de cuentas excluye también autores de evidencias históricas.

La descarga autoriza y proyecta metadatos en SQL antes de acceder a S3, sin mantener transacción durante la transferencia. Lee como máximo 10 MiB más un byte de detección y comprueba la longitud respecto a SQL. Los fallos S3 producen 503 saneado; nunca se descargan objetos de una evidencia ya eliminada o inaccesible en la comprobación inicial.

## Configuración y verificación

Se conservan StorageProperties, AWS_ENDPOINT_URL_S3, AWS_REGION, STORAGE_BUCKET y las credenciales estándar AWS. StorageConfig usa path-style y tiempos máximos de 60 segundos por llamada y 30 por intento. El bucket es privado y el frontend solo usa el backend autenticado.

Las pruebas de imágenes construyen muestras sintéticas con EXIF/GPS y chunks PNG. AttachmentServicePostgresIT usa JWT firmado, HTTP/MVC, PostgreSQL 18 Testcontainers y un doble S3; verifica capacidad, carreras entre autores, compensación incluso por fallo de commit y autorización. Véase [Desarrollo](../development.md#verificación-del-backend) para resultados fechados.

El perfil neon-storage tiene integration-test y verify. La prueba real exige `NEON_STORAGE_TEST_AUTHORIZED=true` además de AWS\_\* y STORAGE_BUCKET de un entorno no productivo autorizado; usa claves temporales únicas y limpieza finally. Las pruebas ordinarias fijan un endpoint local inerte y no heredan credenciales remotas. Una prueba omitida no acredita conectividad ni operaciones reales.

## Consecuencias y evolución

La retención y el purgado físico de objetos eliminados, la reconciliación de huérfanos, la anonimización adicional de personas y la autorización departamental de Tramitación son trabajo funcional futuro. No hay workflow CI/CD nuevo. La ausencia de transacción distribuida y el consumo acotado por petición siguen siendo límites operativos explícitos.
