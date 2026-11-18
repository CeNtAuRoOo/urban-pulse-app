## Título

ADR-007: Invitaciones para incorporar permisos municipales

## Estado

Aceptado; módulo backend implementado.

## Fecha

2026-10-11 (decisión definitiva del 30/10/2026).

## Ámbito

Invitaciones para incorporar permisos municipales

## Contexto

Una sola identidad debe permitir el alta ciudadana y la concesión posterior de permisos municipales.

## Opciones consideradas

Se descartaron las contraseñas provisionales, el registro público con privilegios y el alta de usuarios por un administrador.

## Decisión

Un ADMIN invita por correo a una cuenta existente o por crear. El destinatario canjea el código con cuenta verificada y correo coincidente. La concesión del rol o pertenencia, el consumo y la auditoría son atómicos.

## Consecuencias

El usuario mantiene identidad e historial. Los bloqueos y restricciones resuelven canjes concurrentes. El envío posterior al commit puede fallar tras registrar la invitación. [Diseño del canje y amenazas](../design-docs/DD-001-identidad.md).
