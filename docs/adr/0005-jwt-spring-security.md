# ADR-0005: Autenticación JWT y autorización con Spring Security

- **Estado:** Aceptado como **diseño**; implementación de `auth` pendiente.
- **Fecha:** 2026-10-11.
- **Revisión de diseño:** 2026-10-25.
- **Ámbito:** UrbanPulse, backend, identidad y seguridad.

## Contexto

UrbanPulse expone una API REST compartida por clientes web y móviles. Ciudadanos y personal municipal tienen permisos distintos y pueden tener varios roles. El backend debe validar cada operación, independientemente del frontend. La infraestructura Spring Boot/Spring Security y la conexión a Neon están preparadas; **no existen todavía endpoints de autenticación ni entidades de identidad implementados**.

## Opciones consideradas

1. JWT Bearer emitidos y verificados por el backend (**elegida**).
2. Sesiones HTTP mantenidas en el servidor.
3. Proveedor externo de identidad.
4. API keys compartidas (descartadas como autenticación de usuarios).

En cuanto a autorización: confiar en roles embebidos en JWT durante toda su vigencia o consultar el estado y los privilegios actuales en PostgreSQL (**elegida**). En cuanto a abusos: contador en PostgreSQL/Redis/gateway o limitación inicial local en la memoria del backend (**elegida por ahora**).

## Decisión: primera implementación pendiente

1. **Registro ciudadano** `POST /auth/register` y **login** `POST /auth/login` mediante credenciales y BCrypt, con rechazo genérico de credenciales incorrectas; el registro público solo concede `CITIZEN` (ADR-0009). Ambos devuelven un `TokenDTO` con JWT de acceso y datos autorizados del titular; registro responde `201`, login `200`.
2. JWT Bearer **firmado**, sin datos sensibles ni roles como fuente autoritativa. Duración inicial **7200 segundos (2 h)**, sin refresh token. Claims mínimos `sub` (UUID), `iat`, `exp`, `iss` y `ver` (`User.tokenVersion`). El emisor y el material de firma se configuran externamente, nunca mediante secretos versionados. El algoritmo concreto y la gestión de rotación deben configurarse y probarse antes del despliegue.
3. Validar la firma, caducidad y claims en Spring Security y **consultar PostgreSQL en cada petición protegida** para comprobar cuenta activa, `tokenVersion` y roles vigentes. Comprobar también titularidad, ámbito departamental y estado de recurso cuando se implementen esos casos. Un JWT emitido antes de una revocación de rol no concede permisos retirados; una cuenta desactivada pierde acceso en la siguiente petición.
4. **Cambio de contraseña autenticado:** `POST /users/me/password-changes`. Comprobar contraseña actual, guardar el hash BCrypt nuevo e **incrementar `User.tokenVersion` en la misma transacción**. Todos los JWT anteriores, incluido el empleado para cambiarla, dejan de ser válidos en las siguientes peticiones. La respuesta de éxito es `204` y el cliente deberá iniciar sesión de nuevo.
5. **Rate limiting inicial en memoria por instancia**, en la capa/filtros de seguridad, antes de operaciones costosas. Aplicar políticas diferenciadas a registro y login, por IP y, en login, también por identificador normalizado de cuenta. Contadores con expiración/reposición temporal y tamaño acotado; rechazar exceso con `429` y `Retry-After` cuando sea calculable. Los umbrales deben ser configurables y probados; no se fijan cifras definitivas de negocio en este ADR. Una instancia reiniciada pierde sus contadores y varias instancias no comparten límite.
6. **Errores uniformes:** `application/problem+json` (`ProblemDetail`). `400` para JSON no interpretable, `422` para datos válidamente parseados que incumplen validaciones, `409` para registro con email duplicado, `401` para fallo de login/token y `403` para falta de privilegios. Integrar `@RestControllerAdvice` con `AuthenticationEntryPoint`/`AccessDeniedHandler` y filtros, porque algunas excepciones ocurren antes del controlador.
7. **Correo:** registrar cuenta con `emailVerified=false`, sin verificación obligatoria hasta implementar el flujo de correo. Nunca permitir que un cliente modifique el indicador. La confirmación obligatoria y recuperación por correo quedan como fases posteriores y deberán impedir continuar a cuentas sin verificar **cuando estén disponibles**, con comprobación también en backend.
8. **Preferencias iniciales:** `NotificationPreferences` con `inApp`, `email` y `push` en `true`; guardar como preferencia, sin implementar envíos ni confundirla con permiso del dispositivo o consentimiento de comunicaciones comerciales.
9. Proteger con HTTPS/TLS en despliegue, definir CORS de orígenes permitidos y revisar CSRF según transporte Bearer. No registrar contraseñas, hashes, JWT ni secretos; usar errores no enumerables. La autenticación del servicio PostgreSQL permanece independiente de los JWT de usuarios.

## Pendiente de realizar en la rama de autenticación

- Revisar y acordar OpenAPI y las dos vistas del dominio; aplicar migración Flyway inicial solo para `User`, `Role` y `UserRole`, con datos iniciales de roles.
- Implementar registro, login, cambio de contraseña, emisión/validación JWT, consulta de autoridades, protección de rutas, errores y límite local; añadir tests aislados y de integración PostgreSQL efímera.
- Concretar y verificar en implementación parámetros de firma, coste BCrypt, cuotas concretas del limitador, tratamiento de concurrencia de `tokenVersion` y cookies/cabeceras de seguridad; mantener decisiones trazables.
- Regenerar artefactos OpenAPI desde el contrato modular, actualizar diagramas editables/exportaciones y marcar funcionalidades como implementadas **solo después** de ejecutar las pruebas.

## Mejoras futuras expresamente fuera de esta rama

- Refresh tokens, renovación y cierre de sesiones individual.
- Verificación de email por correo, **obligatoria para avanzar** una vez implementada; envío de códigos/enlaces y reenvío controlado.
- Recuperación de contraseña olvidada por correo, tokens de recuperación de uso único y caducidad.
- Despliegue de limitación compartida mediante gateway/Redis si hay escalado horizontal o requisitos estrictos de límite global.
- Política avanzada de visibilidad de campos `fields` y anotaciones `OWNER`, `ADMIN`, `NONE`, etc. Pendiente de investigación y aprobación; no forma parte del contrato activo.

## Consecuencias y riesgos

**Ventajas:** API autocontenida, autorización coherente con el dominio, revocación por cambio de contraseña sin tabla de tokens, coste operativo bajo inicialmente.

**Costes/riesgos:** lectura de PostgreSQL en cada petición protegida; posibilidad de fallo de conexión, caducidad de sesión sin refresh y ausencia temporal de email verificable. El limitador local no proporciona una cuota global entre instancias. El JWT robado seguirá funcionando hasta caducar si no se cambia contraseña, desactiva cuenta o retiran sus permisos. La consulta de `tokenVersion` solo invalida tokens al incrementar su valor por una acción aprobada, no existe una blacklist por token.

## Fuentes y relaciones

- [Diseño funcional de autenticación](../auth-design.md).
- [ADR-0007: invitaciones](0007-invitaciones-personal.md).
- [ADR-0009: roles](0009-rbac-departamentos-equipos.md).
- [ADR-0011: UUIDv7](0011-identificadores-uuidv7.md).
- [OpenAPI modular](../api/openapi/paths/autenticacion.yaml).

**Estado real:** este ADR registra una decisión de diseño, **no** código existente ni pruebas funcionales ya superadas.
