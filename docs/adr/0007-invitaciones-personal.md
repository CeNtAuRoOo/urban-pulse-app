# ADR-0007: Códigos de invitación para incorporar personal municipal

- **Estado:** Aceptado (mecanismo); detalles de política pendientes de implementación
- **Fecha original:** 2026-10-11
- **Revisión:** 2026-10-18
- **Ámbito:** UrbanPulse, identidad y autorización

## Contexto

Los ciudadanos podrán registrar una cuenta ordinaria, pero no atribuirse por sí mismos privilegios municipales. El dominio v1 separa roles globales (`OPERATOR`, `ADMIN`) y roles con ámbito de departamento (`DEPARTMENT_COORDINATOR`, `TECHNICIAN`), además de `CITIZEN`. Es necesario autorizar tanto nuevos usuarios como la incorporación de personal que **ya tenga cuenta**.

## Opciones consideradas

1. Códigos de invitación generados por un administrador y canjeados por el destinatario (**elegida**).
2. Alta manual completa por un administrador.
3. Autoregistro abierto con selección de privilegios (**descartada por insegura**).
4. Invitaciones mediante enlace/correo como mecanismo adicional de entrega, no como autorización alternativa.

## Decisión

- Un administrador autorizado genera un **código criptográficamente aleatorio**, ligado a un rol permitido, con caducidad y revocación posibles. Si el rol es `DEPARTMENT`, también deberá concretarse y validarse el `Department` autorizado.
- Persistir en `StaffInvitation` el hash/HMAC del código (nunca el secreto en claro), rol, departamento opcional según ámbito, emisor, fechas de creación/caducidad, canje y revocación, y usuario que lo canjeó. La representación del hash y la protección contra ataques de enumeración se definirán al implementar.
- El código se muestra/entrega de forma segura y **una sola vez** al emitirlo; el canje es **atómico y de un único uso**. No permitir cambios de rol o departamento arbitrarios desde el cliente.
- Al recibir el email/código, comprobar si existe una cuenta asociada. Si ya existe, permitir que el titular autenticado y verificado **incorpore el rol a su cuenta**, sin duplicar `User`. Si no existe, seguir el proceso de alta. No promover a un tercero basándose únicamente en conocer su dirección de correo.
- Para roles globales, actualizar `UserRole`; para roles departamentales, `DepartmentMembership` (ADR-0009), según el ámbito del rol.
- Registrar intentos importantes, revocaciones y cambios efectivos de permisos con auditoría y limitar los intentos de canje.

## Consecuencias

**Positivas:** privilegios controlados, historial verificable, cuentas únicas y reutilización de identidades ciudadanas.

**Riesgos:** filtración, fuerza bruta, canjes simultáneos, entrega insegura, toma de cuentas o escalada incorrecta de privilegios. Se necesitan atomicidad, vencimiento, verificación de titularidad, límites de tasa y registro de eventos sensibles sin exponer tokens.

## Puntos que se decidirán al revisar el tag Invitaciones

- Si el email queda obligatoriamente fijado al emitir la invitación; cómo acreditar titularidad y entregar el código.
- Duración, políticas de intentos/cancelación, revocación, recuperación y canje concurrente.
- Creación controlada del **primer administrador** (sin invitación abierta al público).
- Política de emisión por rol/ámbito y autorización para designar departamentos.
- Forma exacta de endpoints/DTO: el OpenAPI actual sigue siendo borrador y **todavía no** representa completamente la reutilización de cuentas ni los roles departamentales.

**Implementación:** pendiente. La aprobación de este ADR no significa que el flujo de invitaciones ya funcione.
