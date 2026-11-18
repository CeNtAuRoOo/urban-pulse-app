## Título

ADR-005: Autenticación JWT y autorización con Spring Security

## Estado

Aceptado; núcleo implementado en backend. Extensiones de correo e Invitaciones implementadas después

## Fecha

No consta fecha original.

## Ámbito

identidad, seguridad por petición y decisiones de autorización

## Contexto

UrbanPulse necesita una identidad compartida para ciudadanos y personal municipal, sesiones sin estado de servidor y permisos controlados aunque cambie el rol de una cuenta. La API REST no debe depender de sesiones en memoria ni confiar en autorizaciones declaradas por el frontend o por `x-roles` en OpenAPI. Una credencial válida no garantiza por sí sola que la cuenta siga activa, conserve permisos ni tenga correo verificado.

## Opciones consideradas

Se valoraron sesiones de servidor, JWT con roles incrustados como autoridad hasta expirar y JWT firmado con validación del estado de seguridad actual contra PostgreSQL. Se adopta esta última variante porque permite invalidación global tras cambio de contraseña, activación de permisos tras canje y denegación inmediata de cuentas inactivas, a costa de una consulta a base de datos por petición protegida y de requerir disponibilidad de PostgreSQL.

## Decisión

Firmar JWT de acceso y revalidar versión, actividad, correo y roles actuales en PostgreSQL. El cambio de contraseña revoca los tokens anteriores y la verificación o el canje se reflejan en la petición siguiente.

## Consecuencias

La revocación y los nuevos permisos surten efecto sin mantener sesiones de servidor. La consulta de seguridad añade latencia y necesita PostgreSQL disponible. Códigos con HMAC independiente y entrega SMTP tras commit separan persistencia de entrega. [Diseño de identidad](../design-docs/DD-001-identidad.md).
