# ADR-0007: Códigos de invitación para roles del personal municipal

- **Estado:** Propuesto
- **Fecha:** 2026-10-11
- **Ámbito:** UrbanPulse

## Contexto

Los ciudadanos pueden registrarse con correo y contraseña, pero no deben autoasignarse roles privilegiados como operador, técnico, analista o administrador. Se necesita una vía controlada para incorporar personal sin exponer un registro público de roles.

## Opciones consideradas

1. Códigos de invitación generados por un administrador y canjeados por el destinatario.
2. Alta manual completa de cuentas por un administrador.
3. Autoregistro abierto permitiendo escoger rol (descartado por inseguro).
4. Invitaciones por correo con enlace, como variante futura.

## Decisión

Se propone que un administrador autenticado genere invitaciones de alta entropía vinculadas a un rol permitido. En la base de datos se guardarán el hash/HMAC del código, rol autorizado, creación, caducidad, estado y fechas de uso/revocación. El código en claro solo se entrega de forma segura al destinatario. El canje será **de un solo uso y atómico**; la cuenta o elevación de rol solo se producirá tras validar el código. Las reglas limitarán qué roles puede emitir cada administrador y prohibirán asignaciones arbitrarias desde el cliente.

## Consecuencias

**Positivas:** alta controlada y trazable, sin exposición de roles privilegiados en un endpoint de registro público.

**Riesgos:** filtración, reutilización, fuerza bruta, canjes simultáneos y distribución insegura. Se necesitan caducidad breve, limitación de intentos, auditoría y revocación. El código debe generarse criptográficamente; no usaremos códigos cortos o predecibles.

## Consideraciones pendientes

Concretar si la invitación está vinculada a un correo, si habilita alta nueva o promoción de cuenta existente, caducidad, entrega, reglas de revocación y recuperación; definir cómo se provisiona inicialmente el primer administrador. No implementar el flujo antes de cerrar estos detalles.
