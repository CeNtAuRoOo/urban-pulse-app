# PostGIS y contexto urbano

## Consultar el catálogo

`GET /api/v1/urban-assets` permite consultar activos urbanos a cualquier cuenta activa con JWT vigente y correo verificado. Devuelve una lista JSON y la cabecera `Cache-Control: no-store`. Una instalación nueva devuelve `[]`. El único contenido precargado de negocio de V1 son los cinco roles.

| Consulta | Selección y orden |
| --- | --- |
| Sin parámetros | Hasta 500 filas por ID ascendente |
| `type` | Igualdad con el valor de `source_table`. Los espacios exteriores se recortan y un filtro vacío equivale a omitirlo. El máximo es de 64 puntos Unicode tras el recorte. |
| `latitude`, `longitude`, `radiusMeters` | Los tres parámetros se envían juntos. Latitud y longitud WGS84 admiten los intervalos cerrados de −90 a 90 y de −180 a 180. El radio admite de 1 a 5.000 metros. Se puede combinar con `type`. |

La consulta espacial transforma el punto WGS84 a ETRS89 / UTM zona 30N (EPSG:25830), aplica `ST_DWithin` y ordena por distancia y, en los empates, por ID. Ambas consultas aplican el límite en PostgreSQL antes de hidratar las geometrías. Los parámetros incompletos, no finitos o fuera de rango producen 400. La seguridad distingue credenciales ausentes o inválidas (401) y correo sin verificar (403).

Cada registro contiene `id` numérico, `sourceTable`, `name`, `category`, `description`, `address`, `phone`, `email`, `url` y `geom`. El WKT expresa coordenadas métricas EPSG:25830 sin prefijo SRID. Los cuatro campos de contacto o dirección se serializan como `null`, incluso cuando la fila de origen contiene valores. Los demás metadatos opcionales también pueden ser nulos. [Contrato modular](api/openapi/paths/contexto-urbano.yaml).

## Instalación y persistencia

Compose y Testcontainers utilizan PostgreSQL 18 con PostGIS 3.6, fijado por digest. La [migración V1](../backend/src/main/resources/db/migration/V1__initial_schema.sql) instala la extensión en `public` y crea `cartografia.contexto_urbano`. El rol de instalación necesita los privilegios correspondientes. Si el proveedor administra la extensión, hay que verificar su versión y esquema antes de aplicar V1. Hibernate Spatial participa en la lectura y escritura de geometrías JTS y Hibernate valida el esquema al arrancar.

La clave primaria es un BIGINT de identidad. La tabla exige origen, geometría válida, finita y bidimensional en SRID 25830. Admite puntos, líneas y polígonos. Un índice GiST cubre la geometría y otro índice ordena por origen e identidad. El SQL espacial califica la tabla con su esquema. Las siete funciones de Mapas y sus índices siguen usando tipos nativos de PostgreSQL. [Decisión de Mapas](adr/0017-mapas-provinciales-privacidad.md).

La consolidación en V1 precede al primer despliegue confirmado por el propietario. Flyway comprueba el checksum en cada validación. Un volumen con historial distinto requiere un respaldo verificable y un plan de migración específico antes de reutilizarlo. Para ensayar una instalación limpia se utiliza una base independiente. [Guía de desarrollo](development.md).

## Procedencia y cobertura

El catálogo carece de adquisición, carga inicial y sincronización de datos del Ayuntamiento de Málaga. `source_table` identifica el conjunto de origen de una fila. Por sí solo no acredita procedencia, licencia ni vigencia. Una futura ingesta debe registrar la fuente, licencia, fecha y CRS, reproyectar y validar geometrías, revisar los campos libres y definir cómo actualizar o retirar registros. Las asociaciones de activos con incidencias, las observaciones de proveedores y el contexto agregado permanecen en diseño. [ADR-0018](adr/0018-catalogo-espacial.md).

`mvn -B -ntp clean verify -Ppostgres-it` ejecuta las pruebas unitarias y `UrbanAssetControllerIT`. Esta suite instala V1 con Flyway en una base efímera, arranca Spring con validación de Hibernate y consulta el endpoint con un cliente HTTP. Usa fixtures sintéticos para verificar el catálogo vacío, geometrías y distancias, desempate, límite de respuesta con más de 500 filas disponibles, privacidad, JWT y errores. CI contrasta la respuesta capturada con el esquema publicado. Estas pruebas verifican el comportamiento funcional. La caracterización del rendimiento requiere datos representativos.
