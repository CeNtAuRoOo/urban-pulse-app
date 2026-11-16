# ADR-0016: CI y PostgreSQL local para desarrollo con Docker Compose

- **Estado:** Aceptado e implementado
- **Fecha:** 2026-11-14
- **Ámbito:** desarrollo y validación automática; despliegue productivo fuera de alcance
- **Evoluciona parcialmente:** [ADR-0002](0002-neon-postgresql.md) y [ADR-0006](0006-flyway.md)

## Contexto y evolución

La decisión inicial utilizaba Neon PostgreSQL también para desarrollo. Compartir esa base entre integrantes con migraciones distintas mezcla historiales Flyway. La decisión definitiva separa PostgreSQL local de desarrollo y Neon PostgreSQL de producción. Se simplifica la propuesta inicial: un único Compose, sin modalidad SQL remota, overrides JDBC ni configuración de worktrees simultáneos.

## Decisión

La aplicación se desarrolla siempre mediante `compose.yaml`, con `name: urban-pulse-app`. Sus cuatro servicios son backend, frontend, PostgreSQL 18 Alpine y Mailpit. Publica exclusivamente en loopback backend 8080, frontend 8888, PostgreSQL 5432, SMTP 1025 y Mailpit web 9999 (interno 8025).

El backend espera al healthcheck de PostgreSQL. `backend/.env.development` define JDBC a `postgres:5432` y correo a `mailpit:1025`; Compose carga el archivo sin overrides redundantes. `frontend/.env` proporciona la API pública `http://localhost:8080`; CORS autoriza `http://localhost:8888`. No se utiliza configuración de entorno en la raíz.

Neon Object Storage se mantiene tanto en desarrollo como en producción, con recursos y credenciales propios. PostgreSQL local no aísla los objetos externos. Producción es independiente de Compose: Neon PostgreSQL, Neon Object Storage, SMTP real y secretos de despliegue; los marcadores productivos se completan al preparar ese despliegue.

Cada integrante tiene su base local persistente. El volumen no cambia al hacer `git switch`; un historial incompatible necesita una base de prueba nueva o una reconciliación explícita, conservando los datos necesarios. No se exige personalizar proyectos ni puertos para el trabajo habitual. `down` conserva volúmenes; `down -v` los elimina y solo corresponde a recursos propios descartables.

Flyway es la única herramienta de migración, V1 permanece intacta y Hibernate usa `validate`. Coordinar versiones antes de integrar ramas: el aislamiento entre equipos no resuelve una colisión V2/V2. Las migraciones aplicadas en bases compartidas son inmutables; no usar `repair` para ocultar conflictos. La [guía de desarrollo](../development.md#3-ramas-y-migraciones-concurrentes) detalla el procedimiento.

GitHub Actions verifica PR hacia main, push a main y ejecución manual mediante tres jobs: Maven/Surefire/Failsafe con PostgreSQL 18 Testcontainers; npm/test/tipos/lint/exportación web; OpenAPI modular y el único Compose. La validación Compose prepara temporalmente los archivos privados desde las plantillas versionadas, sin secretos ni arranque. Se excluyen NeonConnectivityIT, neon-storage y el runner HTTP mutativo. Hay permisos de lectura, cachés y cancelación de ejecuciones obsoletas, sin CD.

## Alternativas y consecuencias

Mantener SQL remoto compartido conserva la interferencia entre desarrolladores. Testcontainers resuelve el aislamiento automatizado, pero no sustituye la base persistente de la aplicación. PostgreSQL local añade consumo de disco y exige revisar el historial al cambiar de rama; simplifica el trabajo cotidiano sin administrar modos de conexión alternativos.

Las pruebas de arranque Compose complementan Testcontainers porque comprueban imágenes, puertos, DNS, entorno y correo. Se mantienen como verificación local reproducible, sin un cuarto job que levante toda la aplicación y requiera configuración S3 privada. Las pruebas aisladas no acreditan producción ni operaciones reales en Neon Object Storage.

## Ampliación de Tramitación (15/11/2026)

El perfil postgres-it incorpora explícitamente ProcessingPostgresIT y ProcessingMigrationPostgresIT. Conserva las nueve suites anteriores; Evidencias usa dobles S3. La equivalencia local del workflow no dispara ejecuciones remotas. Resultados y entorno en [Desarrollo](../development.md).
