# ADR-0005: Autenticación JWT y autorización con Spring Security

- **Estado:** Aceptado
- **Fecha:** 2026-10-11
- **Actualización de referencias:** 2026-10-18
- **Ámbito:** UrbanPulse

## Contexto

UrbanPulse expone una API REST consumida desde web y móvil. Ciudadanos y personal municipal tienen permisos distintos; el backend debe verificar identidad y autorización para cada operación sin confiar en restricciones visuales del frontend.

## Opciones consideradas

1. JWT Bearer emitidos y verificados por el backend.
2. Sesiones HTTP mantenidas en servidor.
3. Proveedor externo de identidad.
4. API keys compartidas (descartadas como autenticación de usuarios).

## Decisión

Implementar registro y acceso con credenciales en Spring Boot; proteger la API con Spring Security y tokens JWT firmados. Aplicar autorización por rol **y por ámbito, titularidad y estado del recurso** cuando corresponda. Los ciudadanos tendrán registro estándar. La incorporación controlada de personal utilizará invitaciones administradas ([ADR-0007, aceptado](0007-invitaciones-personal.md)). La separación entre roles globales y departamentales y las pertenencias a departamentos/equipos se concreta en [ADR-0009](0009-rbac-departamentos-equipos.md). La conexión PostgreSQL utiliza credenciales del servicio independientes de JWT.

## Consecuencias

**Positivas:** separación cliente/servidor, protección centralizada y peticiones autocontenidas.

**Riesgos:** un JWT robado conserva acceso hasta expirar si no existe revocación; almacenamiento seguro de tokens en web y móvil; gestión y rotación de claves; control CORS y CSRF según el mecanismo de transporte. No incluir datos sensibles en JWT.

## Consideraciones pendientes

Definir algoritmo/claves, caducidad, refresco y revocación, recuperación de contraseña, almacenamiento de tokens, limitación de intentos y permisos precisos por endpoint. El modelo de roles de los diagramas es una **referencia inicial a verificar al implementar cada tag**. Spring Security puede validar JWT; emitirlos requiere implementación adicional. No se considera adoptado un servidor OAuth2 completo.

**Implementación:** pendiente; la dependencia y el diseño no garantizan un sistema de autenticación funcional.
