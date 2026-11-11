# Contorno provincial de Málaga

`malaga-ign.geojson` es la respuesta original, sin simplificar ni modificar coordenadas,
de la API oficial del Instituto Geográfico Nacional (IGN/CNIG):

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

## Reproducción offline

Desde la raíz: `node backend/scripts/generate-map-boundary.mjs --check` verifica la
huella y que la migración coincide exactamente con la plantilla y el recurso. Sin
`--check` genera el archivo SQL. **Nunca regenerar una migración ya aplicada**: una
revisión exige otra versión de funciones, migración e índices. El generador falla
si cambia la huella, identidad, número de polígonos o anillos, para exigir revisión.

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
