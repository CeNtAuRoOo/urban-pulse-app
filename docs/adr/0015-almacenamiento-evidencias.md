# ADR-0015: Almacenamiento externo y gestión de evidencias fotográficas

- **Estado:** En implementación; entidad `Attachment` y servicios de almacenamiento desarrollados.
- **Fecha:** 2026-11-04.
- **Revisión:** Aceptado.
- **Ámbito:** Gestión de evidencias, persistencia PostgreSQL y almacenamiento de objetos en Neon.

## Contexto

UrbanPulse necesita permitir que los usuarios adjunten fotografías a las incidencias para aportar evidencias visuales de los problemas comunicados. Estos archivos deben poder almacenarse, recuperarse y consultarse sin comprometer la persistencia de los metadatos ni incrementar innecesariamente el tamaño de la base de datos.

Se requiere separar la información descriptiva de cada archivo de su contenido binario, manteniendo una referencia estable entre ambos sistemas. También es necesario contemplar la validación de los archivos recibidos, su procesamiento previo al almacenamiento, la autorización del usuario que los aporta y la gestión de su eliminación.

La solución debe integrarse con la infraestructura existente de UrbanPulse, basada en PostgreSQL mediante Neon y en un backend desarrollado con Spring Boot y JPA. Para el almacenamiento de archivos se utiliza Neon Object Storage, accedido mediante su API compatible con Amazon S3 y AWS SDK for Java 2.x.

## Alternativas

1. **Almacenamiento directamente en PostgreSQL:** utilizar columnas `BYTEA` para guardar el contenido binario junto con sus metadatos. Simplifica la persistencia y permite transacciones ACID, pero incrementa el tamaño de la base de datos y mezcla información relacional con almacenamiento de archivos.

2. **Almacenamiento en el sistema de archivos del servidor:** guardar las fotografías localmente y persistir únicamente sus rutas en PostgreSQL. Es sencillo de implementar, pero introduce problemas de escalabilidad, persistencia y sincronización entre instancias del backend.

3. **Almacenamiento externo mediante Object Storage:** almacenar los archivos en un servicio especializado y mantener sus metadatos y claves de referencia en PostgreSQL. Permite separar responsabilidades y escalar ambos sistemas independientemente (**elegida**).

## Decisión

Utilizar **Neon Object Storage para almacenar el contenido binario de las evidencias y PostgreSQL para persistir sus metadatos**.

La entidad `Attachment` representa cada evidencia dentro del dominio y dispone de un identificador propio UUIDv7, generado mediante Hibernate:

`@GeneratedValue` y `@UuidGenerator(style = UuidGenerator.Style.VERSION_7)`.

La entidad almacena los siguientes atributos:

- `id`: identificador UUIDv7 de la evidencia.
- `user`: relación con el usuario que ha subido el archivo, mediante la columna `uploaded_by`.
- `originalFilename`: nombre original del archivo, limitado a 128 caracteres.
- `contentType`: tipo de contenido, representado mediante el enumerado `ContentType`.
- `storageKey`: clave única utilizada para localizar el objeto en Neon Object Storage.
- `sizeBytes`: tamaño del contenido almacenado en bytes.
- `createdAt`: instante de creación, generado automáticamente mediante `@CreationTimestamp`.
- `deletedAt`: instante de eliminación lógica, inicialmente nulo.
- `description`: descripción opcional de la evidencia, limitada a 300 caracteres.

**La identificación de la entidad y la identificación del objeto almacenado son independientes.** El campo `id` utiliza UUIDv7, mientras que `storageKey` se genera actualmente mediante `UUID.randomUUID()` y se utiliza como clave del objeto en el almacenamiento externo. No se reutiliza el identificador de la entidad como clave de almacenamiento.

### Integración con Object Storage

La comunicación con Neon Object Storage se encapsula en el servicio `ObjectStorageService`, que utiliza `S3Client` de AWS SDK for Java 2.x.

Se emplea la API compatible con S3 para realizar las operaciones fundamentales:

- **Subida:** `putObject()`, mediante `PutObjectRequest`, indicando el bucket, la clave del objeto y su tipo MIME. El contenido binario se proporciona mediante `RequestBody.fromBytes()`.
- **Descarga:** `getObjectAsBytes()`, mediante `GetObjectRequest`, recuperando el contenido completo como un array de bytes.
- **Eliminación:** `deleteObject()`, mediante `DeleteObjectRequest`, identificando el objeto por su bucket y su clave.

El bucket se obtiene de `StorageProperties`, evitando introducir su nombre directamente en la lógica de negocio.

La configuración del cliente S3 y de las propiedades de conexión se mantiene fuera de `AttachmentService`. De esta forma, el servicio de evidencias no depende directamente del SDK de AWS ni de los detalles de comunicación con el proveedor de almacenamiento.

`ObjectStorageService` actúa como una capa de abstracción entre la lógica de negocio y el proveedor externo, facilitando el mantenimiento y una eventual sustitución del sistema de almacenamiento.

### Flujo de subida

El servicio `AttachmentService` centraliza el proceso de incorporación de evidencias:

1. Validar que el archivo no sea nulo ni esté vacío.
2. Validar que la descripción no supere los 300 caracteres.
3. Comprobar que el usuario que realiza la operación existe y está activo.
4. Leer el contenido binario del archivo.
5. Detectar el formato mediante su firma binaria (*magic bytes*), sin confiar exclusivamente en la extensión o el MIME declarado por el cliente.
6. Admitir únicamente fotografías JPEG, PNG y WebP.
7. Procesar la imagen mediante `ImageSanitizer` antes de almacenarla.
8. Generar una `storageKey` única y subir el contenido procesado a Neon Object Storage.
9. Persistir los metadatos de la evidencia en PostgreSQL mediante `TransactionTemplate` y `AttachmentRepository`.
10. Devolver el DTO correspondiente mediante `AttachmentMapper`.

Los nombres de archivo se sanitizan eliminando las rutas de directorios y limitando su longitud a 128 caracteres. El contenido almacenado corresponde a la imagen procesada, y el tamaño registrado en `sizeBytes` representa el tamaño de los bytes sanitizados.

### Consistencia entre PostgreSQL y Object Storage

Dado que PostgreSQL y Neon Object Storage son sistemas independientes, no existe una transacción ACID distribuida que garantice la atomicidad de ambas operaciones.

Se adopta una estrategia de compensación:

- Primero se sube el objeto al almacenamiento externo.
- Posteriormente se persisten sus metadatos en PostgreSQL.
- Si falla la persistencia de los metadatos, se intenta eliminar el objeto previamente almacenado mediante `storage.delete(storageKey)`.
- Si también falla esta operación compensatoria, la excepción de limpieza se añade como excepción suprimida a la excepción original.

De esta manera se intenta evitar que queden archivos almacenados sin una referencia persistida en la base de datos.

### Consulta y eliminación

Se proporcionan operaciones para consultar los metadatos de todas las evidencias no eliminadas, recuperar una evidencia concreta y descargar su contenido binario desde Neon Object Storage.

Las consultas individuales comprueban que `deletedAt` sea nulo. Si la evidencia no existe o ya ha sido eliminada, se devuelve un error `ATTACHMENT_NOT_FOUND` con HTTP 404.

La eliminación se realiza mediante **borrado lógico (*soft delete*)**. El método `markAsDeleted()` establece `deletedAt` únicamente cuando todavía no tiene valor, evitando modificar la fecha de una eliminación anterior.

La eliminación lógica no implica actualmente la eliminación física del objeto en Neon Object Storage.

La relación con `Incident` queda pendiente de implementar, ya que la entidad de incidencias todavía no está disponible en el momento de desarrollar este módulo.

### Tratamiento de metadatos EXIF

Las imágenes recibidas se procesan mediante ImageSanitizer antes de almacenarse en Neon Object Storage. Este procesamiento debe eliminar los metadatos EXIF que puedan contener información sensible, como coordenadas GPS, fecha y hora de captura o información del dispositivo utilizado.

La fotografía almacenada será, por tanto, la versión procesada y no necesariamente el archivo binario original enviado por el usuario.

Consecuencias:
- Se reduce el riesgo de exponer información personal o de localización.
- El contenido almacenado puede diferir del archivo original.
- El tamaño registrado en sizeBytes corresponde a la imagen procesada.
- Es necesario verificar que el sanitizador elimina efectivamente los metadatos y que no conserva información sensible.

## Consecuencias

**Ventajas:**

- Separación entre almacenamiento relacional y almacenamiento binario.
- Reducción del tamaño de PostgreSQL al evitar almacenar directamente las fotografías.
- Escalabilidad independiente de los metadatos y los archivos.
- Identificación estable de las evidencias mediante UUIDv7.
- Validación del formato real de las imágenes y procesamiento previo a su almacenamiento.
- Asociación de cada evidencia con el usuario que la ha aportado.
- Eliminación lógica que conserva los metadatos y permite mantener el historial.
- Estrategia compensatoria para reducir la aparición de objetos huérfanos cuando falla la persistencia en PostgreSQL.
- Encapsulación de la comunicación con Object Storage mediante `ObjectStorageService`.
- Configuración externalizada del bucket y de los parámetros de almacenamiento mediante `StorageProperties`.
- Menor acoplamiento entre la lógica de negocio y los detalles de integración con el proveedor.

**Costes y limitaciones:**

- Dependencia de Neon Object Storage y de la compatibilidad de su API con S3.
- Necesidad de gestionar la consistencia entre dos sistemas de persistencia independientes.
- Las operaciones compensatorias no garantizan atomicidad: un fallo durante la limpieza puede dejar objetos huérfanos.
- Las descargas requieren una operación adicional contra el almacenamiento externo.
- El borrado lógico conserva los objetos binarios, por lo que será necesario definir una política de retención y eliminación física.
- Es necesario controlar los permisos de acceso al contenido y evitar que las claves de almacenamiento se utilicen como mecanismos de autorización.
- La relación entre evidencias e incidencias todavía no está implementada.
- La descarga mediante `getObjectAsBytes()` carga el contenido completo en memoria, lo que puede suponer limitaciones para archivos de gran tamaño.

## Evidencias de implementación y pendientes

- **Entidad `Attachment` implementada:** identificador UUIDv7, relación con `User`, metadatos, clave externa de almacenamiento, marcas temporales y descripción.
- **Servicio `AttachmentService` implementado:** subida, consulta de metadatos, descarga y eliminación lógica.
- **Servicio `ObjectStorageService` implementado:** encapsula las operaciones de subida, descarga y eliminación de objetos mediante `S3Client`.
- **Integración S3:** utilización de `PutObjectRequest`, `GetObjectRequest` y `DeleteObjectRequest` para interactuar con Neon Object Storage.
- **Configuración externalizada:** bucket y parámetros de almacenamiento gestionados mediante `StorageProperties`.
- **Transferencia binaria:** subida mediante `RequestBody.fromBytes()` y descarga mediante `ResponseBytes<GetObjectResponse>`.
- **Validación de formatos:** reconocimiento de JPEG, PNG y WebP mediante firmas binarias.
- **Procesamiento de imágenes:** integración con `ImageSanitizer` antes de almacenar el contenido.
- **Consistencia entre sistemas:** utilización de `TransactionTemplate` y eliminación compensatoria del objeto si falla la persistencia de sus metadatos.
- **Gestión de errores:** excepciones específicas para archivos vacíos, formatos inválidos, errores de procesamiento, almacenamiento y descarga.


Pendientes:

- Implementar la relación entre `Attachment` e `Incident` mediante una clave foránea.
- Verificar las migraciones Flyway y las restricciones de PostgreSQL correspondientes a la entidad.
- Comprobar la configuración de endpoint, credenciales, región y direccionamiento del cliente S3 para Neon.
- Añadir pruebas de integración para subida, persistencia, recuperación y eliminación.
- Probar los escenarios de fallo de PostgreSQL y Object Storage, incluyendo fallos durante las operaciones compensatorias.
- Verificar que las imágenes procesadas no contienen información no deseada 

**Estado real:** la entidad `Attachment`, `AttachmentService` y `ObjectStorageService` están desarrollados en la rama `feature/modulo-evidencia`. La implementación contempla la separación entre metadatos en PostgreSQL y contenido binario en Neon Object Storage, utilizando la API compatible con S3 mediante AWS SDK for Java 2.x. La relación con `Incident`, las pruebas de integración y la validación completa del comportamiento ante fallos siguen pendientes de verificación.