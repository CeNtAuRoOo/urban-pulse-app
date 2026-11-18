# DD-003 — Evidencias privadas y consistencia SQL/S3

Descripción retrospectiva del flujo de fotografías. El backend comparte con Incidencias la autorización del detalle, aplica la política de escritura del expediente y conserva la autoría real en SQL. La representación pública solo identifica al informante original si fue quien subió el archivo. La comprobación de permisos de descarga describe una instantánea anterior a la transferencia.

## Autorización y congelación

Solo el titular u OPERATOR/ADMIN global con acceso al expediente pueden operar sobre sus evidencias. Alta y baja admiten REPORTED y AWAITING_INFORMATION. En los demás estados se devuelve 409 ATTACHMENTS_FROZEN. El servicio revalida bajo bloqueo al confirmar. La lectura de material activo conserva la autorización del detalle privado. Un marcador público en el mapa nunca concede acceso a las fotografías.

## Validación y memoria

Solo JPEG (.jpg y .jpeg) y PNG, determinados por firma y decodificación completa, sin confiar en extensión o MIME declarado. Entrada y resultado tienen límites independientes de **10 MiB (10485760 bytes)**. Multipart admite 11 MiB por petición para su sobrecoste y guarda las partes en disco; la aplicación vuelve a limitar la lectura, también en llamadas internas. Excesos previos al controlador se traducen a 413 ATTACHMENT_TOO_LARGE.

Antes de decodificar se comprueba un máximo de **16 millones de píxeles** y **8192 por lado**. Sustituye el antiguo techo de 40 millones: dos rasters de cuatro bytes por píxel pueden consumir unos 128 MB, además de buffers y sobrecoste del decodificador. No equivale a una cuota global de RAM; dimensionamiento y límites de concurrencia del despliegue siguen siendo necesarios. La caché del escritor se acota antes de aceptar bytes, evitando crecer hasta un resultado ilimitado.

Se rechazan advertencias de decodificación, truncamientos, formatos incompatibles y CRC/chunks PNG inválidos. Se recodifican únicamente píxeles en un raster limpio, sin copiar EXIF/GPS, texto ni perfiles originales; PNG conserva alfa. JPEG puede perder algo de calidad por la recodificación. La descripción admite 300 puntos Unicode, coincidiendo con PostgreSQL y OpenAPI. El nombre descarta rutas, controles y caracteres de formato; limita a 128 puntos Unicode y conserva extensiones fotográficas. Un nombre vacío se convierte en attachment, nunca en ruta ni clave S3.

## Persistencia y concurrencia

La migración inicial [V1](../../backend/src/main/resources/db/migration/V1__initial_schema.sql) crea attachments con incident_id obligatorio, FK RESTRICT a incidencia/autor, storage_key única, checks de formato/tamaño/fechas/nombre e índice parcial de activas ordenadas.

Una incidencia admite **cinco evidencias activas en total**, sumando todos sus autores. Flujo de alta:

1. Revalidar cuenta y visibilidad en una transacción corta.
2. Leer, validar y sanear; subir al bucket fuera de SQL.
3. En nueva transacción, revalidar cuenta bloqueando usuario y después incidencia con PESSIMISTIC_WRITE. Revalidar estado de escritura; contar activas, rechazar la sexta con 409 ATTACHMENT_LIMIT_REACHED, insertar metadatos y auditoría.
4. Esperar la confirmación SQL. Ante fallo de flush, commit, acceso, estado o capacidad, intentar DELETE compensatorio del objeto.

El método suspende transacciones externas para que la confirmación termine antes de devolver éxito. El bloqueo de incidencia coordina instancias y también las bajas. Si falla la compensación, conserva la causa primaria y la excepción suprimida; registra un evento interno con clave opaca y tipos de error para reconciliación, nunca en respuestas públicas. No hay atomicidad distribuida: una caída de proceso o limpieza fallida puede dejar un objeto huérfano.

## Baja, auditoría y descarga

DELETE establece deletedAt una sola vez y conserva fila, autor, FK y objeto S3. Una repetición devuelve 404 y mantiene el estado final; no hay restauración HTTP. La baja libera una plaza y queda excluida de colección, detalle y contenido.

ATTACHMENT_CREATED y ATTACHMENT_DELETED usan AuditEvent, recurso ATTACHMENT y únicamente incidentId en detalles, en la misma transacción SQL. No se amplía el historial público de incidencias ni sus filtros. La limpieza de cuentas excluye también autores de evidencias históricas.

La descarga autoriza y proyecta metadatos en SQL antes de acceder a S3, sin mantener transacción durante la transferencia. Lee como máximo 10 MiB más un byte de detección y comprueba la longitud respecto a SQL. Los fallos S3 producen 503 saneado; nunca se descargan objetos de una evidencia ya eliminada o inaccesible en la comprobación inicial.

## Configuración y verificación

Se conservan StorageProperties, AWS_ENDPOINT_URL_S3, AWS_REGION, STORAGE_BUCKET y las credenciales estándar AWS. StorageConfig usa path-style y tiempos máximos de 60 segundos por llamada y 30 por intento. El bucket es privado y el frontend solo usa el backend autenticado.

Las pruebas de imágenes construyen muestras sintéticas con EXIF/GPS y chunks PNG. AttachmentServicePostgresIT usa JWT firmado, HTTP/MVC, PostgreSQL 18 Testcontainers y un doble S3; verifica capacidad, carreras entre autores, compensación incluso por fallo de commit y autorización. Véase [Desarrollo](../development.md#verificación-del-backend) para resultados fechados.

El perfil neon-storage tiene integration-test y verify. La prueba real exige `NEON_STORAGE_TEST_AUTHORIZED=true` además de AWS\_\* y STORAGE_BUCKET de un entorno no productivo autorizado; usa claves temporales únicas y limpieza finally. Las pruebas ordinarias fijan un endpoint local inerte y no heredan credenciales remotas. Una prueba omitida no acredita conectividad ni operaciones reales.

[ADR-015](../adr/ADR-015-almacenamiento-evidencias.md) · [Contrato de evidencias](../api/openapi/paths/evidencias.yaml) · [Retención y huérfanos](../pending.md#seguridad).
