# ADR-0005: Autenticación JWT y autorización con Spring Security

- **Estado:** Aceptado
- **Fecha:** 2026-10-11
- **Ámbito:** UrbanPulse

## Contexto

UrbanPulse expone una API REST consumida desde web y móvil. Ciudadanos y personal municipal tienen permisos distintos; el backend debe verificar identidad y autorización para cada operación sin confiar en restricciones visuales del frontend.

## Opciones consideradas

1. JWT Bearer emitidos y verificados por el backend.
2. Sesiones HTTP mantenidas en servidor.
3. Proveedor externo de identidad.
4. API keys compartidas (descartadas como autenticación de usuarios).

## Decisión

Implementar registro y acceso con credenciales en Spring Boot; proteger la API con Spring Security y tokens JWT firmados. Aplicar autorización por rol y, cuando proceda, por propiedad/estado de los recursos. Los ciudadanos tendrán registro estándar. La asignación de roles del personal se tratará mediante invitaciones administradas (ADR-0007 propuesto). La conexión con PostgreSQL utilizará credenciales del servicio, independientes de los JWT.

## Consecuencias

**Positivas:** separación cliente/servidor, protección centralizada y peticiones autocontenidas.

**Riesgos:** un JWT robado conserva acceso hasta expirar si no existe revocación; almacenamiento seguro de tokens en web y móvil; gestión y rotación de claves; control CORS y CSRF según el mecanismo de transporte. No deben incluirse datos sensibles en el contenido del JWT.

## Consideraciones pendientes

Definir algoritmo de firma y claves, caducidad, refresco/revocación, recuperación de contraseña, almacenamiento de tokens, rate limiting y permisos precisos por endpoint. Spring Security puede **validar** JWT; emitirlos requiere código adicional. No se considera adoptado un servidor OAuth2 completo.
