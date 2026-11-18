## Título

ADR-017: Mapas provinciales, publicación y privacidad

## Estado

Implementado.

## Fecha

2026-11-10

## Ámbito

Consulta provincial y privacidad del mapa de incidencias.

## Contexto

El visor provincial debe contar avisos autorizados sin revelar la posición ni el título privados de avisos ajenos.

## Opciones consideradas

Se valoraron SQL nativo y PostGIS, cálculo por consulta y caché persistente, cuadrícula fija y desplazamiento aleatorio. El cálculo en una instantánea y una cuadrícula estable evita invalidar cachés de permisos o promediar posiciones repetidas.

## Decisión

Mantener las siete funciones y sus índices con geografía provincial fija en V1. Autores y personal global acceden a posiciones exactas. Una revisión VALIDATE habilita la publicación aproximada para terceros. La posición pública se obtiene una vez por celda, se filtra por viewport y solo entonces se agrupa.

## Consecuencias

La aproximación reduce precisión sin anonimizar. En la costa el centro se ajusta a la frontera, lo que incrementa el coste. Se limitan resultados, tiempo y cuota. [Procedencia, proyección y privacidad](../design-docs/DD-004-territorio.md) · [Revisión abierta T01](../pending.md#territorio).
