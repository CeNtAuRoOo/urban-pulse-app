# UrbanPulse — Diseño de autenticación y seguridad (revisión previa al código)

> **Estado: diseño acordado, PENDIENTE DE IMPLEMENTAR.** Revisión de 25/10/2026 contra `main` (`64042857dc88118dc7e0e8dd1e2daa37b5f84633`). Este documento es la especificación objetivo para la primera implementación del tag **Autenticación**. No acredita endpoints desplegados, entidades JPA, migraciones de dominio, tests ni configuración JWT funcional. Al terminar la implementación, el PR deberá actualizar los estados y pruebas reales.

## 1. Referencias y reglas generales

- Requisitos del proyecto, [arquitectura](architecture.md), [modelo de dominio v1](domain-model.md), [diagramas de identidad](diagrams/README.md) y [contrato modular OpenAPI](api/openapi/base-openapi.yaml).
- [ADR-0005](adr/0005-jwt-spring-security.md): autenticación JWT, consulta de permisos, contraseña, rate limiting y seguridad.
- [ADR-0007](adr/0007-invitaciones-personal.md): invitaciones de personal (no implementar en esta rama).
- [ADR-0009](adr/0009-rbac-departamentos-equipos.md): roles globales/departamentales y `CITIZEN` como rol base.
- [ADR-0011](adr/0011-identificadores-uuidv7.md): identificadores UUIDv7 generados por Hibernate.
- Las decisiones de este documento no autorizan cambios ajenos al tag. Actualizar conjuntamente OpenAPI, ambos diagramas, dominio y ADR si se descubren divergencias al implementar; seguir [CONTRIBUTING](../CONTRIBUTING.md).

## 2. Alcance inmediato y mejoras futuras

| Comportamiento                                                                           | Estado de diseño         | Estado real del backend                    |
| ---------------------------------------------------------------------------------------- | ------------------------ | ------------------------------------------ |
| Registro ciudadano `POST /api/v1/auth/register` que devuelve JWT y usuario               | Acordado para esta rama  | **Pendiente**                              |
| Login `POST /api/v1/auth/login`                                                          | Acordado para esta rama  | **Pendiente**                              |
| Cambio `POST /api/v1/users/me/password-changes` con invalidación total de JWT anteriores | Acordado para esta rama  | **Pendiente**                              |
| `User`, `Role`, `UserRole`, persistencia de preferencias y Flyway inicial                | Acordado para esta rama  | **Pendiente**                              |
| Resolución de cuenta activa/roles efectivos por petición protegida, Spring Security      | Acordado para esta rama  | **Pendiente**                              |
| Rate limiting local para login y registro, formato `ProblemDetail`                       | Acordado para esta rama  | **Pendiente**                              |
| Refresh tokens/renovación automática                                                     | **Mejora futura**        | No implementar                             |
| Verificación de correo obligatoria antes de utilizar la app                              | **Mejora futura**        | No implementar envío ni bloqueo todavía    |
| Recuperación de contraseña olvidada por correo                                           | **Mejora futura**        | No implementar                             |
| Primer `ADMIN`: procedimiento manual con hash generado localmente y SQL administrado     | **Tarea posterior**      | No implementar en esta rama                |
| Registro, canje y revocación de invitaciones municipales                                 | **Otro tag**             | No implementar                             |
| Selección `GET ?fields=` y autorización/anotación por atributo                           | **Investigación futura** | No implementar, ni crear tabla de permisos |

## 3. Identidad y modelo de datos

**`User`** (tabla candidata `users`): `id: UUID` v7, `displayName: String` (2–120), `email: String` (normalizado, único independientemente de mayúsculas), `passwordHash: String` (BCrypt; nunca en JSON), `active: boolean` (alta activa), `emailVerified: boolean` (`false` al registrar), `tokenVersion: long` (`0` al registrar), `preferences: NotificationPreferences` con `inApp=true`, `email=true`, `push=true`, `createdAt: Instant`, `updatedAt: Instant`. `emailVerified` no es modificable por el cliente, y `tokenVersion` tampoco; deben excluirse del `UserDTO` público salvo el estado `emailVerified` del titular/autoridad autorizada. Las fechas en SQL usan `timestamptz`.

**`Role`** (tabla `roles`): `code: RoleCode` como PK natural, `scope: RoleScope` (`GLOBAL`/`DEPARTMENT`), `description: String`. Datos iniciales por Flyway: `CITIZEN`, `OPERATOR`, `ADMIN` globales y `DEPARTMENT_COORDINATOR`, `TECHNICIAN` departamentales. Los códigos son estables. `CITIZEN` debe pertenecer a **toda cuenta**, incluso cuentas futuras creadas por invitación para staff.

**`UserRole`** (tabla `user_roles`): relación N:M explícita de `User` con `Role` solo para roles `GLOBAL`, con PK compuesta candidata `(user_id, role_code)` y FKs. No duplicar una relación JPA de escritura `@ManyToMany` que compita con la entidad explícita. En esta rama registrar `CITIZEN`; futuros tags añadirán `OPERATOR`/`ADMIN` únicamente por invitación. Los roles departamentales nunca entran en `UserRole` y no se crean todavía `DepartmentMembership`, `Team`, `StaffInvitation` ni `AuditEvent`.

**`NotificationPreferences`** es `@Embeddable`/objeto de valor de `User`; la tabla `users` puede conservar las tres columnas con `NOT NULL DEFAULT TRUE`. No se implementa servicio de avisos ni consentimiento de dispositivo/correo.

**Integridad:** correo único normalizado también en BD; hashes no reversibles; regla del rol global base; restricciones de nulabilidad en Flyway; operaciones de creación y cambio de contraseña transaccionales. Elegir y documentar cómo resolver duplicados bajo concurrencia (la validación previa en Java no basta). Para generación UUID JPA usar el generador `VERSION_7` expresamente y probar que `UUID.version()` devuelve 7. `Role` y PK compuesta son excepciones.

## 4. Flujo del registro

1. `POST /api/v1/auth/register` público, JSON `RegisterRequest(displayName,email,password)`; `additionalProperties: false`.
2. Validar campos, normalizar email y resolver duplicados de modo seguro; no aceptar roles, `active`, `emailVerified` ni `tokenVersion` en la petición.
3. BCrypt para contraseña; crear User activo, no verificado, tokenVersion=0 y preferencias en true; asignar `CITIZEN` mediante `UserRole` en una transacción.
4. Emitir JWT firmado de 7200 s; devolver **`201` con `TokenDTO`** (`accessToken`, `tokenType: Bearer`, `expiresIn: 7200`, `user: UserDTO`). No se hace segundo login.
5. `Location: /api/v1/users/me` es la dirección contextual acordada para el perfil, pero **no enviar una Location engañosa hasta que GET /users/me esté implementado** en su tag (o documentar explícitamente el carácter no disponible si se decide enviarla). OpenAPI deja esta cabecera prevista, no obligatoria.

La cuenta **puede usarse sin verificación de email en esta primera implementación** porque aún no hay proveedor ni endpoint de confirmación; el backend no debe exigir `emailVerified=true` hasta que el flujo futuro esté disponible. En el futuro el registro debe conducir a un paso bloqueante de verificación, comprobado también en backend.

## 5. Flujo de inicio de sesión

1. `POST /api/v1/auth/login` público con `LoginRequest(email,password)`.
2. Resolver User, BCrypt, `active`, versión vigente y roles. Para correo inexistente, credenciales incorrectas o cuenta inactiva, utilizar **`401` uniforme** sin revelar existencia de la cuenta y sin escribir secretos en logs.
3. Emitir `TokenDTO` con `200`. No devolver `404` ni `409` por login normal; `400`/`422` para peticiones incorrectas y `429` cuando haya exceso de frecuencia.
4. No hay endpoint `/auth/refresh` en esta versión. Tras caducidad el cliente tendrá que volver a iniciar sesión.

## 6. JWT y autorización de cada petición protegida

- JWT Bearer firmado: claims `sub` = UUID del usuario, `iat`, `exp`, `iss`, `ver` = `User.tokenVersion`. No incluir contraseña, email, datos sensibles ni roles como fuente de permisos. Token de **2 horas**; configuración de firma y emisor mediante entorno, sin secretos en Git.
- Spring Security valida firma, emisor y vigencia. **Cada petición protegida lee de PostgreSQL** la cuenta (`active`, `tokenVersion`) y los roles globales actuales; comparar `ver` con `tokenVersion` para rechazar tokens invalidos. Las comprobaciones futuras de departamento, propiedad y recurso se harán donde proceda, además de roles.
- `UserRole` se carga de forma optimizada (join/proyección adecuada, sin N+1). Toda ruta no declarada explícitamente pública se protege por defecto. Que una operación indique `x-roles` en OpenAPI no sustituye autorización real.
- Una desactivación de cuenta o retirada de rol tiene efecto en la siguiente petición protegida; la revocación de permisos no requiere reemitir el token. Comprobar consumo/latencia con la base Neon existente, sin añadir caché ahora. La desconexión de la BD no debe convertirse en permiso concedido por defecto (fail closed).
- CORS configurado por origen permitido; JWT enviado como `Authorization: Bearer ...`; política de CSRF acorde al transporte, no asumir cookie. Validar los filtros y sus códigos `401`/`403`.

## 7. Cambio de contraseña e invalidación de JWT

1. `POST /api/v1/users/me/password-changes` protegido (tag **Autenticación**, aunque la URL empiece por `/users`). `ChangePasswordRequest(currentPassword,newPassword)`, `additionalProperties:false`, nueva contraseña mínimo 8 caracteres.
2. Validar JWT y cuenta actual; verificar la contraseña actual con BCrypt; aplicar reglas de contraseña y evitar nueva contraseña idéntica si se adopta esa validación al implementar.
3. Guardar el nuevo hash e **incrementar tokenVersion de forma atómica/transaccional**. Proteger frente a dos cambios concurrentes y actualizaciones perdidas.
4. Responder **`204 No Content`** y hacer que el cliente elimine el token utilizado. Ese token, y cualquier otro anterior, debe producir `401` en la siguiente petición protegida debido a `ver` obsoleto.
5. No crear tabla de sesiones ni blacklist de JWT, ni correo de notificación de cambio de contraseña en esta fase.

## 8. Validación, errores y seguridad de entrada

- `400`: cuerpo JSON ilegible o estructura no interpretable.
- `422`: JSON parseado cuyos campos violan Bean Validation (`@Valid`) o reglas de entrada (campos requeridos, longitud, formato), con `errors[]` de ser pertinente.
- `409`: registro con correo duplicado/concurrencia en integridad.
- `401`: login fallido o autenticación JWT ausente/caducada/inválida/obsoleta/cuenta inactiva.
- `403`: autenticación válida pero insuficiente para una operación.
- `429`: límite de solicitudes superado; `Retry-After` cuando se conozca.
- `500`: errores inesperados, sin revelar trazas internas.
- Responder `application/problem+json` según `ProblemDetail` común. Implementar `@RestControllerAdvice` para errores MVC y mecanismos Spring Security (entry point/denied handler) y filtro limitador para fallos fuera del controlador. No interpretar un `404` como fallo de login.

## 9. Limitación de peticiones en la fase inicial

Filtro de seguridad anterior a operaciones costosas; contadores locales por instancia con capacidad y reposición temporal (_Token Bucket_ o equivalente), caducidad de entradas y tamaño máximo de memoria. Políticas independientes por ruta: registro por IP, login por IP y cuenta/email normalizado (sin confirmar existencia). Rechazo `429` sin consultar PostgreSQL cuando el filtro detecta exceso. Para proxies, confiar únicamente en direcciones/cabeceras saneadas de proxies explícitamente configurados. Umbrales, ventanas y reposición **configurables** y probados antes de activar; los números ilustrativos de la conversación no son requisitos aprobados.

En 3 instancias no existe contador global: un cliente podría distribuir intentos entre ellas. Se acepta explícitamente esta limitación inicial para reducir infraestructura; si se requieren garantías globales, migrar a gateway o almacenamiento compartido cuando se aborde el despliegue.

## 10. Integraciones y funcionalidades aplazadas

- **Verificación por email:** añadir ya `User.emailVerified=false`, pero no implementar correos, enlace, código, endpoint ni bloqueo hasta contar con un proveedor. Cuando exista, registrarse implicará confirmar correo **antes de continuar utilizando la app**; el control deberá ser server-side. No suponer que un correo sin verificar prueba titularidad en invitaciones municipales.
- **Recuperación de contraseña olvidada:** futuro flujo con solicitud neutra que no enumere correos y canje de token único/caducable por email verificado; no implementarlo por ahora.
- **Refresh tokens**: no existen; token de acceso expira en 2 h.
- **Primer ADMIN**: crear en tarea aparte mediante script local temporal para generar hash BCrypt y luego SQL directo administrado en Neon. No versionar secretos, contraseñas ni pruebas contra production. El administrador recibirá ambos roles globales `CITIZEN` y `ADMIN`.
- **Invitaciones**: globales invitables solo `OPERATOR`/`ADMIN`; roles departamentales mediante pertenencia; toda cuenta conserva `CITIZEN`. Revisar en su tag canje para usuarios existentes y verificación de titularidad.
- **GET fields**: se estudiarán anotaciones por propiedad, con capacidades como `OWNER`/`ADMIN`/`NONE`, filtros por contexto y proyección SQL. No introducir ahora campos dinámicos, nueva tabla de permisos ni serialización directa de entidades. El enfoque no está aprobado en la actual guía OpenAPI.

## 11. Pruebas y proceso de aceptación pendiente

Tests unitarios sin Neon para contraseñas, roles, JWT, tokenVersion, rate limiting y formatos de error. Tests MVC/security para registro/login/cambio, errores 400/401/403/409/422/429, recursos públicos/protegidos y serialización DTO sin secretos. Integración JPA/Flyway sobre PostgreSQL efímera: UUIDv7, FK y ámbitos, email único, transacciones/rollback, rol base, valores de preferencias y concurrencia en cambio de contraseña. El smoke test `NeonConnectivityIT` se mantiene manual y separado; **no ejecutar experimentos ni migraciones sobre `production`**.

Antes de Codex: este documento y contrato revisado se versionan indicando `pendiente de implementación`; las modificaciones de diagramas `.drawio` y exportaciones se editan coordinadamente. Después de Codex: ejecutar tests y lint Redocly, regenerar `docs/api/openapi.yaml`/`docs/api/openapi.html`, actualizar evidencias de pruebas y documentación de estado real. Mantener CHANGELOG/PR con los hechos comprobados, sin declarar trabajo completado anticipadamente.

## 12. Cuestiones técnicas que podrá concretar la implementación sin cambiar el diseño

- Algoritmo y configuración de firma compatibles con Spring Security y riesgo; almacenamiento seguro de claves y rotación futura.
- Parámetros exactos de BCrypt y política adicional de contraseña, además del mínimo de 8 caracteres.
- Límites numéricos concretos, método de limpieza/TTL y biblioteca de rate limiting.
- Índices y normalización SQL exactos para correo, FK y ámbito GLOBAL de `UserRole`.
- Comportamiento de DTO ante ausencia futura de pertenencias departamentales y autorización de otros tags; nunca exponer `passwordHash`/`tokenVersion`.

Cualquier divergencia sustantiva que cambie un acuerdo (por ejemplo, no consultar permisos por petición o no invalidar JWT al cambiar contraseña) debe consultarse y documentarse, **no resolverse unilateralmente por código**.
