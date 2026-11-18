# ADR-0018. Catálogo espacial consultable de activos urbanos

**Estado** Aceptado el 17/11/2026.

## Contexto

El diseño territorial contempla fuentes municipales, observaciones y activos asociados a incidencias. La primera fase necesita consultar activos por origen y radio. El proyecto aún carece de un conjunto municipal con licencia, procedencia y proceso de actualización verificados.

## Decisión

PostgreSQL 18 y PostGIS 3.6 almacenan el catálogo en `cartografia.contexto_urbano`. V1 crea la tabla vacía y sus índices antes de la primera instalación. Hibernate Spatial integra las geometrías con JPA. EPSG:25830 permite consultar distancias en metros en el ámbito de Málaga. El cliente envía el centro en WGS84 y recibe geometrías WKT en EPSG:25830.

El GET de colección conserva el ID numérico local de la fila y un máximo de 500 resultados con orden estable. El UUID, las relaciones con fuentes y las coordenadas del modelo conceptual continúan reservados al diseño futuro. Sustituir el ID o la representación WKT en esta ruta exigiría acordar la transición con sus consumidores.

La primera fase publica el catálogo a cuentas verificadas. Dirección, teléfono, correo y URL permanecen nulos incluso si están presentes en origen. La incorporación de datos municipales exige identificar licencia, origen, fecha, referencia espacial, reglas de depuración, revisión de texto libre y política de actualización. La instalación inicial y las pruebas solo utilizan una tabla vacía o datos sintéticos.

## Alternativas y consecuencias

Las geometrías nativas de Mapas resuelven su consulta provincial de incidencias y preservan sus reglas de privacidad. PostGIS aporta transformación entre CRS, distancia y soporte de geometrías heterogéneas para el catálogo. Cada módulo conserva sus funciones e índices. Un modelo de puntos con UUID habría limitado las geometrías e introducido un cambio en el contrato de lectura ya publicado.

La extensión pasa a ser requisito de instalación para todo el monolito. Compose y las pruebas comparten imagen y digest. Un destino administrado requiere comprobar permisos y esquema de PostGIS. La consolidación solo es aplicable a una base nueva. La semántica del identificador externo y la actualización reproducible corresponden a la futura integración de fuentes. [Contrato y operación](../postgis-urban-context.md).
