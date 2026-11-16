# ADR-0012: Gestión de usuarios, perfiles y administración

- **Estado:** Aceptado
- **Fecha:** 2026-11-03
- **Ampliación aprobada:** 2026-11-04; normalización Unicode, concurrencia y protección del último administrador.
- **Decisores:** Equipo de desarrollo de Urban Pulse

## 1. Contexto

Urban Pulse necesita disponer de un sistema de gestión de usuarios que permita a cada persona consultar y actualizar su propia información, así como ofrecer a los administradores herramientas para supervisar y gestionar las cuentas registradas en la plataforma.

También es necesario establecer una separación entre las operaciones que puede realizar cualquier usuario autenticado y aquellas que requieren privilegios administrativos.

Para ello, se implementa una API REST de gestión de usuarios bajo el prefijo `/api/v1/users`.

## 2. Decisión

Se establece un sistema de gestión de usuarios basado en dos ámbitos de operación:

- **Gestión del propio perfil:** operaciones disponibles para el usuario autenticado sobre su propia cuenta.
- **Administración de usuarios:** operaciones restringidas a usuarios con el rol global `ADMIN`.

Los permisos se aplican en el backend, utilizando Spring Security y autorización a nivel de método.

## 3. Funcionalidades del usuario

### 3.1. Consulta del perfil

El usuario autenticado puede consultar su información mediante:

`GET /api/v1/users/me`

La respuesta incluye los datos públicos de su cuenta y sus roles globales y todas sus pertenencias departamentales activas e históricas.

### 3.2. Actualización del perfil

El usuario autenticado puede modificar su nombre visible mediante:

`PATCH /api/v1/users/me`

La actualización está sujeta a validaciones para evitar nombres vacíos o longitudes no permitidas.

El usuario no puede modificar directamente mediante esta operación sus roles, estado de actividad, correo verificado ni otros atributos internos de seguridad.

## 4. Funcionalidades administrativas

Las siguientes operaciones requieren el rol global `ADMIN`.

### 4.1. Consulta de usuarios

`GET /api/v1/users`

Permite consultar los usuarios registrados mediante un listado paginado.

Admite parámetros de paginación y filtros por rol y estado de actividad.

### 4.2. Consulta individual

`GET /api/v1/users/{userId}`

Permite obtener la información de un usuario concreto mediante su identificador UUID.

### 4.3. Activación y desactivación

`PATCH /api/v1/users/{userId}`

Permite modificar el estado de actividad de una cuenta.

La operación está restringida a administradores y utiliza un DTO específico para evitar que se puedan modificar accidentalmente otros atributos del usuario.

## 5. Autorización y seguridad

Se utiliza Spring Security con autorización basada en roles.

Las operaciones administrativas se protegen mediante la anotación:

`@PreAuthorize("hasRole('ADMIN') and hasAuthority('EMAIL_VERIFIED')")`

Los roles se obtienen de la información persistida en la base de datos, evitando depender exclusivamente de los roles incluidos en el JWT.

Las cuentas deben estar activas y cumplir las condiciones de autenticación y verificación de correo establecidas por la política de seguridad.

Se mantiene la separación entre los roles globales y los permisos específicos que puedan existir en otros ámbitos de la aplicación.

## 6. Validación y tratamiento de errores

Se utilizan DTO específicos para las operaciones de lectura y actualización.

Las entradas se validan mediante Jakarta Bean Validation y las respuestas de error se centralizan mediante `GlobalExceptionHandler`.

Se establecen respuestas HTTP coherentes para errores de validación, parámetros incorrectos, recursos inexistentes, falta de autenticación y falta de autorización.

Las respuestas no deben revelar información interna sensible ni detalles de implementación.

## 7. Protección de información sensible

Las respuestas de usuario no deben exponer:

- Hashes de contraseñas.
- Versiones internas de tokens.
- Credenciales.
- Otros atributos internos utilizados exclusivamente por el backend.

Se utilizan DTO para controlar explícitamente la información que se devuelve a los clientes.

## 8. Consecuencias

### Positivas

- El usuario puede gestionar su propio perfil sin acceder a operaciones administrativas.
- Los administradores disponen de herramientas para gestionar las cuentas.
- Se separan claramente los permisos de usuario y administración.
- La paginación y los filtros permiten consultar grandes conjuntos de usuarios.
- Los DTO reducen el riesgo de exposición de información interna.
- La autorización se realiza en el backend y no depende del cliente.

### Consideraciones

- Las operaciones administrativas deben seguir protegidas mediante autorización en servidor.
- Los cambios futuros en los datos de usuario deben mantener las restricciones de validación.
- Las nuevas funcionalidades relacionadas con departamentos y pertenencias deben documentarse de forma independiente.

## 9. Reglas de integración y concurrencia

Las cinco rutas requieren cuenta activa y correo verificado. `identity/api`, `identity/dto`, `identity/service` e `identity/repository` implementan el contrato; `shared/pagination/PageMetadata` evita convenciones duplicadas. Perfil propio obtiene el titular del JWT validado. La administración exige ADMIN global en la cadena de rutas y en el método; una pertenencia departamental no concede ese rol.

`displayName` se normaliza antes de validar tanto en registro como en PATCH: se recortan controles ASCII y espacios/separadores Unicode de borde, se conservan los espacios interiores y se admiten 2–120 puntos de código Unicode. Se rechazan controles interiores y sustitutos UTF-16 aislados. Jakarta y la entidad comparten estas reglas; PostgreSQL comprueba longitud, ausencia de controles y normalización. Una entrada inválida produce `422 VALIDATION_ERROR`, sin usar SQL como validación habitual. JSON nulo, vacío o con propiedades adicionales no permite eludir los DTO estrictos.

PATCH administrativo solo admite `active`. No se permite desactivar al último ADMIN activo y verificado (`409 LAST_ACTIVE_ADMIN`). Se permite la autodesactivación si permanece otro. Un bloqueo transaccional PostgreSQL de administración se toma antes del bloqueo de usuario; el recuento se realiza después de adquirirlo. Esto evita que dos bajas concurrentes eliminen todos los administradores. Los cambios de perfil usan el mismo bloqueo de fila que contraseña, verificación e Invitaciones. La comprobación de `active` en cada petición hace efectiva la baja; activar de nuevo no modifica `tokenVersion` ni los roles.

El listado usa `page=1`, `pageSize=20` (1–100), orden `createdAt DESC, id ASC` y filtros opcionales por `active` y `role`. El filtro departamental solo considera `leftAt IS NULL`, aunque el DTO muestra también la historia. Primero se pagina en SQL y luego se recuperan roles y pertenencias en lote para los IDs seleccionados, sin fetch de colecciones sobre la consulta paginada. Las pertenencias se ordenan por `joinedAt DESC, id ASC`.

La auditoría de Invitaciones se conserva. La auditoría administrativa de Usuarios y catálogos continúa dentro del alcance parcial pendiente de RF18; no se introduce una política de datos o retención implícita. La revisión de Usuarios se publicó en OpenAPI 0.5.0; el contrato vigente 0.7.0 incorpora Incidencias y Evidencias.

## 10. Referencias

- Spring Security.
- `UserController`.
- `UserService`.
- `UserRepository`.
- `UserDTO`.
- `UsersPageDTO`.
- `AdminUpdateUserRequest`.
- `UpdateMyProfileRequest`.
- `GlobalExceptionHandler`.
- API REST `/api/v1/users`.
