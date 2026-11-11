# ADR-0015: Mapas provinciales, publicación y privacidad

- Fecha: 2026-11-10.
- Estado: implementado en `feature/implementacion-mapas`.
- Alcance: backend Mapas; no modifica alta, edición, validación, asignación, títulos
  públicos, evidencias ni frontend.

## Decisión

Crear `es.grupo18.urbanpulse.maps` con API, orquestación, políticas de visibilidad y
título, DTO propios y repositorio JDBC de proyecciones. `GET /api/v1/map/incidents`
exige JWT, CITIZEN base, actividad y correo verificado. Reutiliza la cadena de seguridad
sin modificarla. La política vuelve a leer `UserRepository.findSecurityState` y la
consulta se ejecuta en la misma transacción read-only REPEATABLE_READ, con permisos
y datos en una instantánea coherente. Una revocación confirmada antes de la siguiente
petición se aplica usando incluso el mismo JWT; un cambio posterior al comienzo de
la instantánea no cancela retroactivamente una respuesta en curso.

Únicamente `user_roles` OPERATOR/ADMIN concede acceso global y coordenadas exactas.
Los roles departamentales conservan las reglas del ciudadano: incidencias propias
exactas y ajenas solo si existe una revisión VALIDATE. No se infiere publicación del
estado; una validación histórica sobrevive a REJECTED, RESOLVED o CLOSED posteriores.
`EXISTS` impide duplicados con varias revisiones. Las dos ramas SQL, exacta y pública,
son disjuntas por titularidad y se unen con UNION ALL.

## Geografía y privacidad

El [contorno IGN versionado](../../backend/src/main/resources/maps/README.md) representa
toda la provincia, no un rectángulo ni solo la capital. La inclusión de la ubicación
original en ese contorno se evalúa en PostgreSQL, con fronteras inclusivas.

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
indexado y solo transporta esa ubicación a la proyección JDBC. Los grupos se
construyen después de ese filtrado. No se registran coordenadas ni cuerpos en logs.

No es anonimización: la celda, el historial, el contexto y las zonas costeras pueden
reducir considerablemente el área plausible. Tampoco se garantiza una distancia
mínima de 100 m entre punto original y público: 100 m es el lado de la celda. Los
marcadores públicos pueden coincidir y no deben desplazarse usando datos privados.

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

## PostgreSQL sin extensión geográfica

Los tipos nativos point, polygon, lseg y box permiten contención y proyección a la
frontera, y GiST sobre point permite filtrar tanto ubicaciones exactas como públicas.
No se añade PostGIS, JTS ni otra dependencia de ejecución. Los índices parciales
solo incluyen ubicaciones dentro de Málaga. Un índice parcial de incident_reviews
acelera EXISTS VALIDATE. Las funciones son IMMUTABLE y versionadas porque su contorno
y proyección son constantes. No acceden a tablas externas ni redes.
Las funciones compuestas fijan el search_path del esquema de instalación; funcionan
también al construir índices sobre datos existentes, cuando PostgreSQL restringe
el search_path del proceso, y cuando se instalan en un esquema distinto de public.

Costes: 12.339 vértices; comprobar contención y aproximar celdas costeras cuesta más
que un simple bounding box. Los índices trasladan parte del cálculo a la escritura
de coordenadas, sin cambiar reglas del módulo Incidencias. Para grandes volúmenes
se debe medir rendimiento y considerar PostGIS/subdivisión del polígono; no se
promete rendimiento ilimitado. Los timeouts nunca deben convertirse en mapas vacíos.

La migración `V20261110120000__maps_geography.sql` usa numeración temporal para evitar
colisiones con V4/V5 de compañeros. No edita V1–V3. Antes de desplegar ramas combinadas,
coordinar el orden: Flyway no aplica por defecto versiones menores añadidas después
de una versión temporal ya instalada. No se activa outOfOrder global ni se despliega
esta migración sobre Neon en esta entrega.

## Contrato con Tramitación

1. Tramitación conserva una fila `incident_reviews` con `decision=VALIDATE`, autor
   municipal real y fecha, confirmada en la misma transacción que valida. Mapas
   confía en ese historial autoritativo, no en permisos actuales del revisor ni en
   campos enviados por el cliente. Revocar posteriormente el rol del revisor no
   borra una validación histórica. REQUEST_INFORMATION/REJECT no publican.
2. Tramitación será propietaria del título aprobado, su versión/fecha/autor de
   aprobación y de su relación con la versión del título fuente. Debe proporcionar
   una proyección legible por lote `incidentId -> approvedPublicTitle` que solo
   devuelva texto cuya aprobación siga vigente. Null/ausente significa fallback.
3. Adaptar `MapTitlePolicy` y la proyección del repositorio (o un único lote por
   respuesta) para consumir esa información. No introducir N+1 ni un fallback a
   `Incident.title`. La nueva política solo podrá usar título aprobado cuando exista
   validación efectiva y aprobación vigente; todo lo demás conserva `Incidencia UUID`.
4. Publicación y aprobación son hechos distintos. Aprobar un título sin VALIDATE
   no publica. No se implementa retirada retroactiva de una validación; si se acuerda
   esa función, definir su nuevo predicado de publicación y pruebas antes de cambiarlo.
5. El detalle privado conserva su autorización actual. Mapas no crea un detalle
   público completo ni amplía permisos departamentales.

## Verificación

Pruebas unitarias de parámetros, permisos y límites; pruebas PostgreSQL con JWT
firmado, roles y pertenencias reales, revisiones, límites geográficos, privacidad,
agrupaciones y JSON. Ver [guía de Mapas](../maps-backend.md) para comandos y resultados.
