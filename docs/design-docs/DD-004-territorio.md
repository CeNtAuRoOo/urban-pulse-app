# DD-004 — Privacidad del mapa provincial y consulta de activos

Descripción retrospectiva de dos lecturas territoriales con contratos distintos. Mapas autoriza dentro de una transacción de solo lectura REPEATABLE READ. OPERATOR y ADMIN globales ven posiciones exactas. El informante ve exactas las propias. Para las ajenas se exige una revisión VALIDATE histórica, incluso si el estado cambió después. EXISTS evita multiplicarlas. El título público de sustitución es `Incidencia <UUID>`. Consultar el mapa no concede acceso al detalle. Una revocación confirmada se aplica en la siguiente instantánea.

## Procedencia del contorno provincial

La geometría fija de las funciones SQL de V1 deriva de Unidades Administrativas del Instituto Geográfico Nacional (IGN/CNIG).

- Recurso: <https://api-features.ign.es/collections/administrativeunit/items/1174486?f=json>.
- Colección: <https://api-features.ign.es/collections/administrativeunit?f=json>.
- Fecha de descarga/versionado: **2026-11-10**. El servicio no aporta fecha de edición
  del elemento; no se confunde la fecha de descarga con una fecha de levantamiento.
- `nationalcode=34012900000`, `nationallevelname=Provincia`, nombre Málaga.
  No es el municipio homónimo (`34012929067`).
- CRS de descarga: OGC CRS84, WGS84, orden **longitud, latitud** en grados.
- Geometría: MultiPolygon, un polígono, un anillo exterior, 12.339 vértices contando
  el de cierre; ningún hueco en esta versión. No se han eliminado islas ni huecos.
- SHA-256: `eb15c7b0de3b648bedc52202a79af47c8887841dce2914986cfca774d8d101ba`.
- Licencia [CC-BY 4.0 del IGN](https://www.ign.es/resources/licencia/Condiciones_licenciaUso_IGN.pdf).
  Atribución: **Unidades Administrativas, descarga 2026-11-10, CC-BY 4.0 ign.es**.
  El recurso SQL proyectado es una obra derivada de este conjunto.

El futuro visor debe incluir la atribución del contorno cuando lo represente. Su precisión
es la de la fuente; no sustituye un deslinde legal ni una línea de costa en tiempo real.
Se incluyen puntos sobre la línea oficial. No se aplica buffer administrativo que
admita ubicaciones de provincias vecinas o mar abierto.

La proyección métrica local es cilíndrica equidistante esférica (no EPSG:3857):

```text
R = 6371008.8 m; origen = (lon -4.5°, lat 36.75°); paralelo estándar = 36.75°
x = R * radians(lon + 4.5) * cos(radians(36.75))
y = R * radians(lat - 36.75)
```

Es afín respecto a las coordenadas del contorno, por lo que conserva la inclusión
en sus segmentos. Distorsión métrica pequeña en esta provincia, adecuada para
**unos** 100 m (no mediciones topográficas ni distancias exactas). La entrada y
salida del API sigue siendo WGS84. Las funciones SQL implementan la inversa.

En el intervalo de latitudes del recurso (36.310280594–37.282199489°), un lado
proyectado de 100 m corresponde aproximadamente a 99.54–100.80 m este/oeste y
99.79–99.81 m norte/sur, usando radios locales del elipsoide WGS84. Así se evita
interpretar un número arbitrario de decimales geográficos como metros.

## Posición pública y ejecución de la consulta

Se usa la proyección cilíndrica equidistante local definida en el recurso y una
cuadrícula fija de 100 m. El punto público es su centro. Si el centro queda fuera
del contorno (costa/frontera), se proyecta **el centro**, no el punto privado, al
segmento oficial más próximo, con desempate por orden del anillo. Toda ubicación
de una misma celda obtiene el mismo punto público; no depende del UUID, usuario,
viewport, estado, categoría o zoom. Así no existe un oráculo de posición privada
mediante rectángulos diminutos. La pertenencia a la provincia sigue siendo una
condición administrativa sobre la ubicación original.

Los originales permanecen en `incidents`; no se añade ni sobrescribe ninguna
coordenada ni columna de título. La rama pública filtra por el punto aproximado
y solo transporta esa ubicación a la proyección JDBC. Los grupos se
construyen después de ese filtrado. No se registran coordenadas ni cuerpos en logs.

No es anonimización: la celda, el historial, el contexto y las zonas costeras pueden
reducir considerablemente el área plausible. Tampoco se garantiza una distancia
mínima de 100 m entre punto original y público: 100 m es el lado de la celda. Los
marcadores públicos pueden coincidir y no deben desplazarse usando datos privados.

### Reutilización del punto público por celda (2026-11-15)

La consulta materializa candidatos autorizados y filtrados por estado/categoría,
selecciona un representante por celda de privacidad de 100 m y materializa su punto
público. Evalúa `map_public_point_v1` una sola vez por celda candidata y reutiliza
el resultado para todas sus incidencias. La función permanece intacta; no se añaden tablas persistentes ni cachés entre peticiones. La rama
exacta de autores y usuarios globales conserva su comportamiento.

Para acotar candidatos se usa el índice GiST de ubicación exacta con el bbox
ampliado 150 m por eje en la proyección local. Es una preselección conservadora:
original a centro de celda <= 50√2 m; si el centro está fuera, la frontera más
próxima no está más lejos que el original interior. El desplazamiento completo
es <= 100√2 m (141,43 m), con margen para tolerancias numéricas. La inclusión
definitiva se decide **siempre por el punto público y el bbox solicitado**, antes
de agrupar y contar. La preselección no excluye posiciones públicas visibles aunque
su original esté fuera del viewport. Los CTE `MATERIALIZED` evitan reevaluaciones
por incidencia. El índice público, ahora integrado en V1, se conserva, incluido su coste de escritura.

## Agrupación y costes

La cuadrícula de grupos está anclada al mismo origen y tiene lado
`100 * 2^(17-zoom)` m para zoom 0..17. Son celdas anidadas. Desde zoom 18 se devuelve
un marcador por UUID, incluso si varias posiciones son idénticas. Un singleton de
cualquier zoom se representa como INCIDENT; el resto como CLUSTER. El identificador
`g1:zoom:columna:fila` es determinista. Conteo y pertenencia corresponden únicamente
al viewport/filtros/usuario actual, por lo que pueden cambiar al desplazar el mapa.
La posición es la del miembro con menor UUID lexicográfico; permanece dentro de
provincia y viewport y nunca promedia posiciones ocultas.

SQL aplica visibilidad, filtro espacial, estado y categoría antes de agrupar. Devuelve
como máximo 1001 filas de proyección (1000 + centinela); nunca entidades JPA completas.
La fila centinela produce 422 MAP_RESULT_LIMIT y no una respuesta truncada. `count`
es el número de gráficos y `totalIncidents` la suma de sus incidencias. El trabajo
interno de PostgreSQL sí depende del volumen visible: tiene timeout 5 s y puede usar
temporales para agregar. Cuota en memoria de 60 consultas por usuario y reposición
60/minuto, compartiendo el limitador acotado existente. No es una cuota distribuida.

El viewport debe tener amplitud por eje <= `min(360,1440/2^zoom)` grados. Se permiten
vistas amplias a zoom bajo, incluso el mundo, pero solo se consideran incidencias
provinciales. La cuadrícula y los límites evitan una respuesta individual ilimitada.

## PostgreSQL nativo en Mapas (independiente de PostGIS)

Los tipos nativos point, polygon, lseg y box permiten contención y proyección a la
frontera, y GiST sobre point permite filtrar tanto ubicaciones exactas como públicas.
**Este módulo `maps` no depende de PostGIS ni de JTS**: sus funciones e índices se
implementan con PostgreSQL nativo.

La base de datos del proyecto incorpora PostGIS por una decisión separada del módulo
`urban_context`, mediante `V1__initial_schema.sql` para el catálogo
`cartografia.contexto_urbano`. Esa extensión no se usa en el endpoint `/map/incidents`;
véase el apartado de catálogo espacial de este documento. Los índices parciales
solo incluyen ubicaciones dentro de Málaga. Un índice parcial de incident_reviews
acelera EXISTS VALIDATE. Las funciones son IMMUTABLE y versionadas porque su contorno
y proyección son constantes. No acceden a tablas externas ni redes.
Las funciones compuestas fijan el search_path del esquema de instalación; funcionan
también al construir índices sobre datos existentes, cuando PostgreSQL restringe
el search_path del proceso, y cuando se instalan en un esquema distinto de public.

Costes: 12.339 vértices; comprobar contención y aproximar celdas costeras cuesta más
que un simple bounding box. Los índices trasladan parte del cálculo a la escritura
de coordenadas, sin cambiar reglas del módulo Incidencias. Para grandes volúmenes
se debe medir rendimiento y considerar optimizaciones del polígono; PostGIS no se
introduce como dependencia de este módulo aunque esté habilitado en otra parte del
proyecto; no se
promete rendimiento ilimitado. Los timeouts nunca deben convertirse en mapas vacíos.

## Catálogo espacial

La clave primaria es un BIGINT de identidad. La tabla exige origen, geometría válida, finita y bidimensional en SRID 25830. Admite puntos, líneas y polígonos. Un índice GiST cubre la geometría y otro índice ordena por origen e identidad. El SQL espacial califica la tabla con su esquema. Las siete funciones de Mapas y sus índices siguen usando tipos nativos de PostgreSQL.

La búsqueda transforma el centro WGS84 a EPSG:25830, aplica ST_DWithin en metros y ordena por distancia e ID. El límite de 500 filas se aplica antes de hidratar geometrías. Sin centro se usa el orden por ID. El filtro por origen se puede combinar con el radio. Las respuestas en WKT omiten el contenido de los cuatro campos sensibles de contacto y dirección mediante valores nulos.

## Verificación y evolución

MapPostgresIT y MapPerformancePostgresIT ejercitan fronteras, filtros, visibilidad, centinela y evaluación por celda. UrbanAssetControllerIT contrasta consultas con PostGIS, geometrías y JSON reales. [ADR-017](../adr/ADR-017-mapas-provinciales-privacidad.md) · [ADR-018](../adr/ADR-018-catalogo-espacial.md) · [Contrato](../api/README.md) · [Decisiones abiertas](../pending.md#territorio).
