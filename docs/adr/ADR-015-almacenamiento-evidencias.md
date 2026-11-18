## Título

ADR-015: Almacenamiento externo y gestión de evidencias fotográficas

## Estado

Aceptado e implementado

## Fecha

2026-11-04; revisión integral: 2026-11-11

## Ámbito

RF05 y aportación de evidencias de RF13; backend, SQL y almacenamiento privado

## Contexto

Una incidencia admite fotografías privadas. SQL y el almacén de objetos pueden fallar por separado.

## Opciones consideradas

Frente a binarios en SQL o acceso directo al bucket se adoptaron objetos privados y mediación del backend. El borrado lógico conserva trazabilidad. La compensación cubre fallos parciales.

## Decisión

Separar metadatos y binarios. Validar y sanear antes de subir. Bloquear expediente y comprobar capacidad al confirmar metadatos y auditoría. Compartir autorización con Incidencias y volver a comprobar la política tras la subida.

## Consecuencias

El actor real queda en auditoría. La sexta evidencia activa se rechaza aun en carrera. Un fallo de compensación puede dejar un objeto huérfano. La descarga tiene una ventana entre autorizar y transferir. [Diseño de evidencias](../design-docs/DD-003-evidencias.md).
