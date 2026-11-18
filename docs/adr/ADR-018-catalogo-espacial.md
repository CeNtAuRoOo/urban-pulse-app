## Título

ADR-018. Catálogo espacial consultable de activos urbanos

## Estado

Aceptado

## Fecha

17/11/2026

## Ámbito

Contexto urbano, instalación de PostGIS y API de lectura.

## Contexto

Se necesita consultar activos por origen y radio con geometrías heterogéneas.

## Opciones consideradas

El modelo de puntos con UUID limita geometrías y altera el identificador del contrato. PostGIS aporta transformación de CRS y distancias métricas. Las funciones nativas provinciales permanecen en Mapas.

## Decisión

Guardar los activos en cartografia.contexto_urbano, con ID numérico, geometría EPSG:25830 e índices espacial y por origen. El cliente envía WGS84 y recibe WKT métrico. La colección expone hasta 500 filas a usuarios verificados, con los campos de contacto y dirección a null.

## Consecuencias

PostGIS pasa a ser requisito de instalación del monolito. Un cambio de ID o representación requiere coordinar consumidores. El catálogo empieza vacío. [Diseño territorial](../design-docs/DD-004-territorio.md) · [Ingesta futura](../pending.md#funcionalidad).
