# ADR-0007: Códigos de invitación para incorporar personal municipal

- **Estado:** Aceptado (mecanismo); detalles de política pendientes de implementación.
- **Fecha original:** 2026-10-11.
- **Revisión:** 2026-10-25.
- **Ámbito:** UrbanPulse, identidad y autorización.

## Contexto

Los ciudadanos pueden registrar una cuenta ordinaria pero no atribuirse privilegios municipales. El dominio diferencia roles globales (`CITIZEN`, `OPERATOR`, `ADMIN`) y departamentales (`DEPARTMENT_COORDINATOR`, `TECHNICIAN`). Se requiere autorizar tanto cuentas nuevas como la incorporación de personal que ya disponga de cuenta. **Toda cuenta mantiene `CITIZEN` como rol global base** (ADR-0009).

## Opciones consideradas

1. Códigos de invitación generados por un administrador y canjeados por el destinatario (**elegida**).
2. Alta manual completa por un administrador.
3. Autoregistro abierto con selección de privilegios (**descartada por insegura**).
4. Enlaces/correos como mecanismo complementario de entrega, no como autorización alternativa.

## Decisión

- El administrador autorizado genera un código criptográficamente aleatorio, vinculado al rol permitido, de un solo uso, con caducidad y revocación. Los roles **globales invitables son exclusivamente `OPERATOR` y `ADMIN`**. No se permite invitar a `CITIZEN` porque es el rol base automático de las cuentas.
- Los roles departamentales `DEPARTMENT_COORDINATOR` y `TECHNICIAN` requieren departamento autorizado, validado por el servidor.
- `StaffInvitation` guarda el hash/HMAC del código, rol, departamento opcional, emisor, fechas, revocación, canje y usuario redimidor. Nunca se guarda el código en claro; solo se revela en su emisión. Los detalles criptográficos quedan para el tag Invitaciones.
- El canje es atómico y de uso único y no admite elevación arbitraria de rol/departamento desde el cliente.
- Cuando el correo ya corresponde a una cuenta, el **titular autenticado y con identidad verificada** incorpora el rol o pertenencia autorizados sin duplicar `User`. No se promociona a terceros por conocer su correo. Cuando no exista cuenta, se crea `User` con `CITIZEN` y el privilegio invitado.
- Los roles globales se almacenan en `UserRole`, los departamentales en `DepartmentMembership` (ADR-0009), con auditoría de cambios sensibles y limitación de intentos.

## Repercusión sobre el contrato (diseño pendiente de implementar)

`GlobalInvitationRequest.role` utilizará un enum acotado a `OPERATOR`/`ADMIN` (`StaffGlobalRoleCode`), mientras `DepartmentInvitationRequest` conserva `DepartmentRoleCode`. `RoleCode` y `GlobalRoleCode` generales siguen incluyendo `CITIZEN`. La validación de pertenencia y la política de verificación se implementarán en su propio tag, no en la rama inicial `auth`.

## Consecuencias

**Positivas:** separación de privilegios, historial, cuentas únicas y reutilización de identidades ciudadanas.

**Riesgos:** filtración, fuerza bruta, canjes concurrentes, entrega insegura, toma de cuentas y escalada. Se necesitan atomicidad, comprobación de titularidad, límites, eventos sensibles y protección de códigos.

## Puntos pendientes del tag Invitaciones

- Si el correo queda fijado obligatoriamente al emitir la invitación; cómo acreditarlo y entregar el código.
- Expiración exacta, políticas de intentos/cancelación, revocación y canje concurrente.
- Verificación del email: el futuro subsistema de confirmación obligatoria del registro ciudadano debe diseñarse antes de confiar en `emailVerified` para promociones municipales.
- Procedimiento manual y controlado del primer administrador (mediante generación local de hash y SQL administrado), **fuera de la rama inicial `auth`**, sin claves en código, tests ni repositorio.
- Política de emisión por rol/ámbito y autorizaciones para designar departamentos.
- Forma final de DTO de canje cuando se implemente; mantener sincronización con OpenAPI y el dominio.

**Implementación:** pendiente, incluidas las reglas de primer administrador y verificación de correo. La aceptación de este ADR no implica endpoints funcionales.
