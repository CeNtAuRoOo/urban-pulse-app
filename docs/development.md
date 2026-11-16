# Guía de desarrollo de UrbanPulse

El desarrollo se ejecuta siempre con Docker Compose: backend Spring Boot, frontend Expo Web, PostgreSQL 18 local y Mailpit. Neon Object Storage de desarrollo conserva las fotografías; Neon PostgreSQL se reserva para producción. `V1__initial_schema.sql` crea 14 tablas funcionales y cinco roles; V2 añade respuestas y restricciones de Tramitación, sin usuarios, ADMIN ni catálogos de demostración. V5 añade las funciones e índices geográficos de Mapas.

## 1. Configuración privada

Se necesita Docker Engine/Desktop en ejecución y Docker Compose con `up --wait`. No hace falta instalar Java, Maven o Node en el host para arrancar la aplicación. El único archivo Compose es `compose.yaml`, exclusivamente de desarrollo, con el proyecto `urban-pulse-app`.

Copiar las plantillas **solo si no existen los archivos privados**. Desde la raíz, en PowerShell:

```powershell
if (!(Test-Path backend/.env.development)) { Copy-Item backend/.env.example backend/.env.development }
if (!(Test-Path frontend/.env)) { Copy-Item frontend/.env.example frontend/.env }
```

Completar los valores antes de arrancar. No se utiliza un `.env` en la raíz. `backend/.env.production` pertenece al futuro despliegue y Compose no lo carga; sus marcadores `#####` se completarán al preparar producción.

| Archivo                                   | Configuración                                                                                                |
| ----------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `backend/.env.example`                    | Plantilla versionada de nombres y comentarios para ambos entornos; no contiene secretos.                     |
| `backend/.env.development`                | JDBC local, Neon Object Storage de desarrollo, JWT/HMAC, CORS y Mailpit. Privado, cargado por Compose.       |
| `backend/.env.production`                 | Neon PostgreSQL, Object Storage y SMTP productivos, con sus secretos propios. Privado, fuera de Compose.     |
| `frontend/.env.example` / `frontend/.env` | Plantilla versionada / configuración privada local del cliente. `EXPO_PUBLIC_API_URL=http://localhost:8080`. |

Para desarrollo, configurar `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/urban-pulse`, `SPRING_DATASOURCE_USERNAME=urbanpulse` y la contraseña local indicada en `POSTGRES_PASSWORD` de Compose. `postgres` es el DNS interno del servicio; `localhost` dentro del backend identificaría al propio contenedor. Compose carga estas variables desde el archivo privado **sin sobrescribirlas**: no colocar una URL remota en el archivo de desarrollo.

Configurar `MAIL_HOST=mailpit`, `MAIL_PORT=1025`, `MAIL_SMTP_AUTH=false`, `MAIL_STARTTLS=false`, usuario/contraseña SMTP vacíos y un remitente de desarrollo. `CORS_ALLOWED_ORIGINS=http://localhost:8888` autoriza el origen del navegador. La URL pública del frontend apunta al backend publicado, nunca a `http://backend:8080`; `localhost` y `127.0.0.1` son orígenes distintos.

Generar **tres claves Base64 independientes**, de al menos 32 bytes aleatorios, para `JWT_SECRET`, `EMAIL_VERIFICATION_SECRET` y `STAFF_INVITATION_SECRET`. Ejecutar tres veces y guardar cada resultado únicamente en el archivo privado:

```bash
docker run --rm node:24-bookworm-slim node -e "console.log(require('node:crypto').randomBytes(32).toString('base64'))"
```

Completar también `JWT_ISSUER`. No reutilizar claves de tests ni productivas. Neon Object Storage requiere `AWS_ENDPOINT_URL_S3`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` y `STORAGE_BUCKET` del recurso de desarrollo autorizado. La base SQL local no aísla el bucket: no hacer pruebas mutativas compartidas sin un escenario preparado y autorización. El CI usa dobles S3 y no necesita esas credenciales.

## 2. Arranque y persistencia

Desde la raíz:

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

`config --quiet` comprueba la configuración sin imprimir secretos; no prueba conexiones ni valida por sí solo los valores privados. Para completar la comprobación, esperar el arranque del backend y abrir frontend y Mailpit.

| Servicio     | Acceso desde el host  | Dirección interna |
| ------------ | --------------------- | ----------------- |
| Backend      | http://localhost:8080 | `backend:8080`    |
| Frontend web | http://localhost:8888 | `frontend:8888`   |
| PostgreSQL   | `127.0.0.1:5432`      | `postgres:5432`   |
| Mailpit SMTP | `127.0.0.1:1025`      | `mailpit:1025`    |
| Mailpit web  | http://localhost:9999 | `mailpit:8025`    |

Todos los puertos publicados se vinculan a `127.0.0.1`. Los clientes móviles requieren una configuración de acceso explícita y no forman parte de este procedimiento web.

El backend espera al healthcheck de PostgreSQL. Flyway es la única herramienta de inicialización: aplica V1, V2 y V5 a una base vacía y Hibernate valida después con `ddl-auto=validate`. No se usa `ddl-auto=update` ni SQL de inicialización paralelo. Arrancar la aplicación puede aplicar migraciones pendientes; `spring-boot:run` no ejecuta las suites de pruebas.

```bash
docker compose up -d --wait postgres
docker compose exec postgres pg_isready -U urbanpulse -d urban-pulse
docker compose exec postgres psql -U urbanpulse -d urban-pulse -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank"
docker compose restart postgres
docker compose up -d --wait postgres
docker compose down
docker compose up -d
```

El volumen `urban-pulse-app_postgres-data` se monta en `/var/lib/postgresql`, como requiere la imagen PostgreSQL 18. `down` elimina contenedores y red, **conservando los volúmenes**; `up` reutiliza los datos. `stop`/`start` conserva también los contenedores. Cambiar las variables iniciales de PostgreSQL no modifica usuarios ni contraseñas de un clúster ya creado.

`down -v` **borra los volúmenes del proyecto**, incluida la base y las cachés Maven/npm. No es un paso habitual de mantenimiento ni una solución a fallos Flyway. Solo se utiliza sobre recursos propios descartables después de comprobar el proyecto y respaldar los datos necesarios.

Para comprobar el correo, registrar una cuenta sintética desde el frontend y revisar su mensaje de verificación en Mailpit (`http://localhost:9999`); confirmar el código desde el cliente. Esto comprueba backend → `mailpit:1025`, no entrega en Internet. El envío ocurre después del commit SQL; un fallo SMTP no revierte el registro. No hay outbox ni reintentos automáticos.

## 3. Ramas y migraciones concurrentes

Cada integrante dispone de su propio Docker y su base local; las migraciones de una persona no se aplican sobre las bases de sus compañeros. **Cambiar de rama Git no reinicia ni separa el volumen local**: se conserva el historial Flyway anterior.

Antes de arrancar una rama diferente, revisar sus migraciones frente al historial local. Si son compatibles, continuar normalmente. Si el historial difiere, detener el entorno y conservar sus datos; para una comprobación desde cero puede usarse temporalmente otro proyecto, por ejemplo `docker compose -p urbanpulse-migracion-prueba up -d --build`, con el entorno habitual detenido para liberar sus puertos. Al terminar, detener ese proyecto y volver al habitual. No es necesario ejecutar worktrees simultáneos ni personalizar puertos. Eliminar volúmenes solo si pertenecen a esa prueba y sus datos son descartables.

El aislamiento entre equipos **no resuelve colisiones al integrar ramas**:

1. Coordinar la siguiente versión libre y revisar las migraciones de `origin/main` antes de integrar; en esta integración existen V1, V2 de Tramitación y V5; coordinar la siguiente versión posterior a V5, sin rellenar huecos históricos ni duplicar versiones.
2. Si dos ramas introducen distintas V2, acordar el orden según sus dependencias. Solo el trabajo no publicado ni aplicado en bases compartidas puede renumerarse antes de integrar.
3. Validar la secuencia conjunta sobre una base nueva y, cuando haya cambios incrementales, también desde el esquema compartido anterior. No reutilizar un historial local incompatible ni editarlo a mano.
4. Una migración aplicada en una base compartida es inmutable. Si hay historiales compartidos en conflicto, detener la integración y preparar una reconciliación revisada, con respaldo y migraciones compatibles.
5. No usar `flyway repair`, `outOfOrder` ni `ddl-auto=update` para ocultar discrepancias.

[ADR-0006](adr/0006-flyway.md) conserva la historia de consolidación; [ADR-0016](adr/0016-ci-postgresql-local.md) define los entornos actuales.

## 4. Desarrollo y producción

| Entorno    | Aplicación               | PostgreSQL          | Objetos                           | Correo              |
| ---------- | ------------------------ | ------------------- | --------------------------------- | ------------------- |
| Desarrollo | Docker Compose           | PostgreSQL 18 local | Neon Object Storage de desarrollo | Mailpit             |
| Producción | Despliegue independiente | Neon PostgreSQL     | Neon Object Storage de producción | Proveedor SMTP real |

No existe una modalidad de desarrollo contra Neon PostgreSQL ni un Compose productivo. El despliegue requiere endpoints, secretos y políticas propios; los marcadores productivos sin completar no afectan al entorno local. Ver [Despliegue](deployment.md).

## 5. CI y comprobaciones locales

El [workflow CI](../.github/workflows/ci.yml) se ejecuta en **PR hacia main**, **push a main** y **workflow_dispatch**. Las ramas de desarrollo se validan al abrir/actualizar su PR o mediante ejecución manual, evitando duplicar cada actualización. La ejecución manual desde GitHub requiere que el workflow exista en la rama por defecto.

Tres jobs independientes, permisos `contents: read`, cachés Maven/npm, límites de tiempo y cancelación de ejecuciones obsoletas por evento/referencia. No despliega ni utiliza credenciales privadas.

### Backend: unitarias/MVC e integración PostgreSQL

El comando del job, desde `backend/`, es:

```bash
mvn -B -ntp clean verify -Ppostgres-it
```

Surefire ejecuta unitarias/MVC; Failsafe ejecuta `integration-test` y `verify` para trece suites: AuthenticationPostgresIT, EmailVerificationPostgresIT, StaffInvitationPostgresIT, UserRepositoryPostgresIT, DepartmentPostgresIT, CategoryPostgresIT, DepartmentMembershipPostgresIT, IncidentPostgresIT, AttachmentServicePostgresIT, ProcessingPostgresIT, ProcessingMigrationPostgresIT, MapPostgresIT y MapPerformancePostgresIT. Usan PostgreSQL 18 efímero de Testcontainers, Flyway y Hibernate validate, independientes de la base persistente de Compose. S3 y SMTP se sustituyen por dobles o configuración local de pruebas. Cualquier fallo de compilación, Docker, migración o test falla el job. Sin el perfil, los `*IT` ordinarios no se ejecutan.

Para repetirlo sin instalar Java/Maven en Windows, desde la raíz con Docker Desktop (PowerShell):

```powershell
docker run --rm --mount "type=bind,source=$((Get-Location).Path)\backend,target=/src,readonly" --mount type=volume,source=urbanpulse-auth-maven,target=/root/.m2 --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.16-eclipse-temurin-25 sh -c 'mkdir -p /tmp/check; cp -a /src/pom.xml /src/src /tmp/check/; cd /tmp/check; mvn -B -ntp clean verify -Ppostgres-it'
```

La copia interna evita que `clean` compita con el backend de desarrollo sobre `target`. Este contenedor es un ejecutor de tests, no otra modalidad de ejecución de la aplicación. No necesita los archivos privados ni iniciar Compose.

### Frontend

El job usa Node.js 24 y ejecuta desde `frontend/`:

```bash
npm ci
npm test
npx tsc --noEmit
npm run lint
npx expo export --platform web
```

También se pueden ejecutar con `docker compose exec frontend <comando>`; detener/recrear el frontend si se reinstalan dependencias mientras Expo está activo. `npm test` usa `node:test` con fetch simulado para formularios, Unicode, cliente HTTP, ETag y errores. `dist/` está ignorado y exportarlo no despliega Nginx.

`npx expo install --check` es mantenimiento opcional: consulta recomendaciones cambiantes y no bloquea CI. `scripts/integration.cjs` queda excluido: modifica datos HTTP y requiere cuentas, contraseña y fixtures específicos; no debe dirigirse a recursos compartidos. La validación funcional móvil y la UI de evidencias siguen pendientes.

### OpenAPI y Compose

Desde la raíz:

```bash
npx --yes @redocly/cli@2.16.0 lint docs/api/openapi/base-openapi.yaml
docker compose config --quiet
git diff --check
```

El job copia `backend/.env.example` a `backend/.env.development` y `frontend/.env.example` a `frontend/.env` únicamente en su checkout temporal. Valida solo `compose.yaml`, sin arrancar servicios ni conectar a Neon. Las plantillas vacías bastan para resolver `env_file`, pero el arranque real requiere completar la configuración. No regenerar artefactos en CI; cuando cambie la fuente OpenAPI, seguir [API](api/README.md#7-validación-y-visualización).

### Pruebas externas

`NeonConnectivityIT` es una comprobación histórica explícita de JDBC y `HeadBucket`, fuera de Surefire ordinario y de `postgres-it`. Espera el usuario SQL remoto histórico; no representa el desarrollo local actual ni debe apuntarse a producción para validar esta rama.

El perfil `neon-storage` prueba PUT/GET/DELETE reales con PostgreSQL Testcontainers. Exige `NEON_STORAGE_TEST_AUTHORIZED=true` y configuración S3 de un recurso de pruebas no productivo autorizado; disponer de credenciales de desarrollo no constituye autorización para ejecutarlo. Tampoco pertenece al CI ordinario. Una prueba omitida no acredita conectividad.

## 6. Límites y evidencias históricas

Las pruebas aisladas y el arranque Compose no acreditan despliegue productivo ni operaciones reales S3. Siguen fuera de alcance CD, hosting/TLS, SMTP productivo, bootstrap del primer ADMIN, retención/purgado, las fases posteriores de Tramitación y validación móvil completa.

Los resultados siguientes conservan sus fechas y configuraciones originales. Las menciones a Neon PostgreSQL de desarrollo, V2/V3 y puertos anteriores son históricas. El recorrido HTTP de Invitaciones utilizó archivos que no están en el repositorio (`compose.test.yaml`, `backend/Dockerfile.test` y `backend/scripts/invitations_http_test.py`); se conserva su resultado sin presentarlo como un procedimiento reproducible actual.

## Verificación del backend

### Integración de Mapas con Tramitación PR #24 (16/11/2026)

Merge local de main 27357ea sobre Mapas b78a6ea. Se conservan Mapas, Evidencias,
CI y processing. V1 y V2 coinciden con main; V5 y el código de producción de Mapas
permanecen intactos. No se modifican bases compartidas ni se accede a Neon.

| Comprobación | Resultado |
| --- | --- |
| `mvn -B -ntp clean verify -Ppostgres-it` | **351 pruebas correctas: 187 Surefire + 164 Failsafe**, cero fallos, errores u omisiones |
| Mapas | 12 unitarias + 20 IT funcionales + 4 IT de rendimiento |
| Evidencias | 19 IT y 8 unitarias de imágenes |
| Tramitación | 19 IT funcionales + 2 de migración; unitarias incluidas en Surefire |
| `npm ci`, `npm test`, `npx tsc --noEmit`, `npm run lint`, `npx expo export --platform web` | PASS; cinco pruebas frontend y exportación web local |
| `docker compose config --quiet` | PASS con copia aislada de compose.yaml y plantillas versionadas |
| Recurso IGN `--check` | PASS; 12.339 vértices y V5 reproducible |
| Redocly 2.16.0 | Fuente y bundle válidos; bundle y HTML regenerados con redoc-template.hbs |

Las trece suites Failsafe ejecutaron: Authentication 8, EmailVerification 12,
StaffInvitation 24, UserRepository 15, Department 6, Category 6, DepartmentMembership 9,
Incident 20, AttachmentService 19, Map 20, MapPerformance 4, Processing 19 y
ProcessingMigration 2. No se suman ejecuciones históricas. Maven se ejecutó dentro de
una copia Docker con Java 25.0.4.1, Maven 3.9.16, Spring Boot 4.1.2 y PostgreSQL 18.6
Testcontainers. Frontend usó Node 24.18.0, una copia de archivos versionados,
EXPO_NO_DOTENV=1 y API localhost; ningún archivo privado de entorno se copió.
No se arrancó Compose ni se ejecutaron las pruebas externas Neon.

Los escenarios nuevos recorren dos solicitudes/respuestas reales antes de VALIDATE
o REJECT, verificando estados, permisos, coordenadas, ausencia de datos privados y
no acceso por el marcador a detalle, fotografías o resultados internos. La revisión
V1 con snapshot null sigue siendo válida para publicación histórica. Se verifica
V5 sobre V1/V2 con datos; otra base reproduce V1/V5 antes de recibir V2: rechazo
por defecto, ensayo administrativo puntual fuera de orden solo en Testcontainers,
conservación de datos/checksums y validación posterior con outOfOrder=false.
[Procedimiento y límites](adr/0006-flyway.md#v2-recibida-después-de-v5).

Los informes XML, planes EXPLAIN, ejemplos HTTP y logs frontend están en
`backend/target/maps-processing-merge-verification`, ignorado por Git. Las pruebas
costera (1.000 en una celda), de 10.000 y de 50.000 registros pasan y mantienen el
timeout de cinco segundos del servicio. No se usa esta ejecución concurrente con
npm/Expo para comparar latencias con las mediciones controladas anteriores.

Los ejemplos HTTP recién capturados de Mapas pasan el verificador AJV del repositorio,
incluido el rechazo de campos privados. Una segunda generación del bundle coincide
byte a byte (SHA-256 `0de6abe0fb0b8e16fb553186f5e83bae912913ed7a27a5f730888cb4bef2ed63`).
Se verifican 162 enlaces locales sin destinos ausentes. Se eliminan únicamente tres
espacios finales de comentarios en Incident.java recibidos de main; no cambia su
código ejecutable. Las comprobaciones de whitespace se aplican al árbol y al índice.

OpenAPI 0.8.0 conserva 45 operaciones implementadas y 32 futuras. Permanecen los
avisos conocidos de licencia/localhost y los tres parámetros sin uso en el bundle.
El árbol npm conserva 21 vulnerabilidades notificadas (1 baja, 14 moderadas, 6 altas),
deprecaciones y un script de instalación de unrs-resolver sin autorizar; no se aplica
`audit fix` ni se modifica el lockfile. Expo avisa de FORCE_COLOR/NO_COLOR.
GitHub Actions remoto, despliegue y servicios externos no se ejecutaron.

Los resultados siguientes son históricos y no se suman a esta integración.

### Integración anterior de Evidencias y CI (16/11/2026, antes de Tramitación)

`mvn -B -ntp clean verify -Ppostgres-it`: **BUILD SUCCESS, 319 pruebas**
(183 unitarias/MVC y 136 de integración PostgreSQL), cero fallos, errores u omisiones.
Las once suites Failsafe incluyen Mapas (18 funcionales + 4 de rendimiento) y
Evidencias (16); Surefire conserva las 12 unitarias de Mapas y 8 de imágenes.
Se ejecutó desde backend dentro de una copia Docker con Java 25.0.4.1, Maven 3.9.16,
Spring Boot 4.1.2 y PostgreSQL 18.6 efímero mediante Testcontainers. S3 simulado;
NeonConnectivityIT y NeonAttachmentStorageIT no se ejecutan. No se accede a Neon.

Cada base vacía aplica V1 consolidada y V5 intactas. Las expectativas de Flyway
y los TRUNCATE incluyen el esquema de Evidencias; la actualización de Mapas se
prueba sobre V1 con datos y un search_path distinto. No se recuperan V2/V3 ni se
añade V4. Código de producción de Mapas idéntico a la rama previa; Evidencias y
workflow CI idénticos a main. Informes y planes en `backend/target/maps-merge-verification`.

Recurso IGN reproducible: correcto (12.339 vértices). Redocly 2.16.0: fuente modular
válida, bundle y HTML regenerados y validados; se conservan los contratos de ambos módulos.
Cero errores: dos avisos conocidos en la fuente (licencia/localhost) y cinco en el
bundle (los anteriores más AssessmentId, TrainingId y DocumentId sin uso).
El contrato contiene 35 operaciones implementadas y 36 futuras.
Los ejemplos HTTP recién capturados de Mapas pasan el verificador AJV del repositorio.
Los resultados históricos siguientes conservan su fecha y no se suman a esta ejecución.

### Revisión correctiva de Tramitación: privacidad, contrato y SVG (15/11/2026)

Esta verificación es posterior a la implementación inicial y a la regeneración manual de SVG realizada por el usuario. La rama sigue siendo `codex/tramitacion-fase-1`, sin upstream, con HEAD `63f34b23ae07e20d78383d179bf195f16e98d8f4`; no se hicieron commits, push, merge, rebase, PR ni ejecuciones remotas. Se releyó el workflow vigente y se ejecutaron localmente sus tres grupos de comprobaciones.

H1 corrige la proyección del historial: solo identifica al informante original en registro, corrección y respuesta; cualquier otro autor queda omitido, con independencia de su rol, nombre o actividad actuales. La auditoría conserva el actor real y PATCH mantiene autorización y ETag/412. La revisión adicional detectó `AttachmentDTO.uploadedBy`; con autorización expresa del usuario ahora devuelve null para autores distintos del informante, conservando la relación SQL y auditoría. Revisiones y prioridades internas mantienen sus datos y permisos.

H3 reproduce mediante una prueba HTTP que cambiar fotografías tras la consulta municipal no incrementa la versión de Incident: VALIDATE puede confirmar la versión anterior y congelar las fotos vigentes al confirmar. Es una limitación deliberada, no una garantía de snapshot fotográfico. No se modifican la versión por fotos, las migraciones ni el contrato de escritura. Véanse [ADR-0014](adr/0014-incidencias-concurrencia-historial.md) y [ADR-0015](adr/0015-almacenamiento-evidencias.md).

Entorno comprobado: Maven 3.9.16, Temurin 25.0.4.1, Docker Engine 29.9.3, PostgreSQL 18.7 efímero/Testcontainers y Node 24.15.0. Maven se ejecutó en una copia interna de `src/` y `pom.xml` desde un montaje de solo lectura; el comando Docker de la sección Backend reproduce ese aislamiento. S3 usa dobles, sin credenciales reales ni operaciones Neon. Frontend usa `CI=true`, `EXPO_NO_TELEMETRY=1` y `EXPO_PUBLIC_API_URL=http://localhost:8080`.

| Comprobación ejecutada                                                                                                                      | Resultado                                                                           |
| ------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| `mvn -B -ntp verify -Ppostgres-it -Dtest=IncidentServiceTest -Dit.test=IncidentPostgresIT,ProcessingPostgresIT,AttachmentServicePostgresIT` | PASS: 5 unitarias y 58 IT dirigidas                                                 |
| `mvn -B -ntp clean verify -Ppostgres-it`                                                                                                    | **PASS: 314 pruebas**, 175 Surefire en 28 suites y 139 Failsafe en 11 suites        |
| `mvn -B -ntp javadoc:javadoc -Ddoclint=all`                                                                                                 | PASS: cero errores y 66 avisos de completitud documental                            |
| `npm ci`                                                                                                                                    | PASS: 840 paquetes instalados, lockfile sin cambios                                 |
| `npm test`                                                                                                                                  | PASS: cinco pruebas frontend                                                        |
| `npx tsc --noEmit` y `npm run lint`                                                                                                         | PASS                                                                                |
| `npx expo export --platform web`                                                                                                            | PASS: 805 módulos, exportación local sin despliegue                                 |
| Redocly 2.16.0: lint modular, bundle, build-docs y lint del bundle                                                                          | PASS; fuente y artefactos regenerados, sin edición manual de YAML/HTML consolidados |
| `docker compose config --quiet`                                                                                                             | PASS con los archivos privados existentes, sin sobrescribirlos ni mostrarlos        |
| `git diff --check`, enlaces/anclas Markdown, XML y huellas de diagramas/migraciones                                                         | PASS                                                                                |

Los XML finales registran cero fallos, errores, omisiones o flakes. Failsafe ejecutó AuthenticationPostgresIT **8**, EmailVerificationPostgresIT **12**, StaffInvitationPostgresIT **24**, UserRepositoryPostgresIT **15**, DepartmentPostgresIT **6**, CategoryPostgresIT **6**, DepartmentMembershipPostgresIT **9**, IncidentPostgresIT **20**, AttachmentServicePostgresIT **19**, ProcessingPostgresIT **19** y ProcessingMigrationPostgresIT **1**. No se añadieron suites ni se modificó la selección cerrada de Failsafe; las dirigidas no se suman otra vez al total. Los informes y logs de esta ejecución quedan en `backend/target/corrective-final/`, ignorado por Git.

Las regresiones HTTP usan JWT firmado y comprueban PATCH por OPERATOR/ADMIN ajenos, actor omitido en todo el cuerpo, auditoría real, cambios posteriores de cuenta, corrección propia con fecha/tipo/versión, no-store, paginación y 404 indistinguible. El recuento SQL del historial permanece igual con páginas de una y diez filas de autores distintos, y no supera cinco consultas. Se mantienen los motivos públicos de rechazo/solicitud, se ocultan justificaciones e identidades municipales y se comprueba el acceso interno legítimo. Evidencias verifica uploadedBy null en alta/listado/detalle municipal y UUID propio para el informante.

Se reprodujo que el antiguo nullable exterior con allOf rechazaba null en AJV. La representación OAS 3.0.3 se corrigió a oneOf entre PublicReviewDTO y una rama limitada a null, sin modificar el JSON del backend. La especificación resultante se valida estructuralmente mediante Redocly. Las comprobaciones específicas de nulabilidad y propiedades requeridas no quedan cubiertas por esa validación estática. Un bundle independiente coincide byte a byte con el final: SHA-256 `0877fb7d127bba9e506afae94745f9f1ea6a0dd3f62ae528a9abb891273fbaed`. Comandos y fundamento en [API](api/README.md#correcciones-de-privacidad-y-nulabilidad-15112026).

H2 confirma **tres fuentes con ocho páginas y ocho SVG**, incluido `relacional-05.svg`. Los ocho SVG y las tres fuentes `.drawio`, junto con V1/V2, conservan exactamente sus SHA-256 del inicio de esta revisión. Se verificaron XML, IDs y referencias de conexiones de las fuentes, inventario, previsualizaciones enlazadas y enlaces/anclas de 21 Markdown modificados; no se realizó una nueva inspección visual ni se atribuye a Codex la exportación manual del usuario. [Inventario actual](diagrams/README.md).

Avisos: Redocly conserva dos advertencias de licencia/localhost y tres componentes sin uso adicionales en el bundle. Javadoc mantiene 66 avisos del backend/constructores generados, sin errores. npm informa de las mismas 21 vulnerabilidades del árbol de dependencias (1 baja, 14 moderadas, 6 altas), deprecaciones y script bloqueado de unrs-resolver; no se aplicó audit fix ni se alteraron dependencias. Expo avisa de precedencia de FORCE_COLOR sobre NO_COLOR. Ninguno de estos avisos impidió las verificaciones. No hay cambios de frontend/workflow, secretos, `.env` privados ni artefactos target/dist destinados a Git.

### Tramitación fase 1: verificación del 15/11/2026

Se ejecutaron localmente todos los pasos verificables de los tres jobs de `.github/workflows/ci.yml`, además de las comprobaciones documentales. No se disparó GitHub Actions, no se desplegó y no se accedió a Neon para pruebas mutativas. Entorno: Windows/WSL2, Docker Engine 29.9.3, imagen `maven:3.9.16-eclipse-temurin-25` con Maven 3.9.16 y Temurin 25.0.4.1, Spring Boot 4.1.2, PostgreSQL **18.7** (`postgres:18-alpine`, Testcontainers) y Node **24.15.0**. `mvn --version` y `docker info` correctos.

| Comprobación ejecutada                                              | Resultado                                                                     |
| ------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `mvn -B -ntp clean verify -Ppostgres-it`                            | **PASS: 308 pruebas**, 174 Surefire + 134 Failsafe; 28 + 11 suites            |
| `mvn -B -ntp test`                                                  | PASS: 174 pruebas, sin sumar esta repetición al total anterior                |
| `mvn -B -ntp javadoc:javadoc -Ddoclint=all`                         | PASS desde copia limpia: cero errores, 66 avisos de completitud documental    |
| `npm ci`                                                            | PASS; instalación desde el lockfile existente, sin modificar dependencias     |
| `npm test`                                                          | PASS: cinco pruebas frontend                                                  |
| `npx tsc --noEmit`                                                  | PASS                                                                          |
| `npm run lint`                                                      | PASS                                                                          |
| `npx expo export --platform web`                                    | PASS: exportación web, 805 módulos                                            |
| Redocly 2.16.0: lint modular, bundle, build-docs y lint del bundle  | PASS; contrato 0.8.0, 44 operaciones implemented y 33 future                  |
| `docker compose config --quiet`                                     | PASS con los archivos privados existentes, sin sobrescribirlos ni imprimirlos |
| `git diff --check`, enlaces/anclas Markdown y estructura XML drawio | PASS                                                                          |

Los XML finales de Surefire/Failsafe acreditan **cero fallos, errores, omisiones o flakes**. Las once suites de integración efectivamente ejecutadas son:

| Suite Failsafe                 | Pruebas |
| ------------------------------ | ------: |
| AuthenticationPostgresIT       |       8 |
| EmailVerificationPostgresIT    |      12 |
| StaffInvitationPostgresIT      |      24 |
| UserRepositoryPostgresIT       |      15 |
| DepartmentPostgresIT           |       6 |
| CategoryPostgresIT             |       6 |
| DepartmentMembershipPostgresIT |       9 |
| IncidentPostgresIT             |      18 |
| AttachmentServicePostgresIT    |      17 |
| ProcessingPostgresIT           |      18 |
| ProcessingMigrationPostgresIT  |       1 |
| **Total integración**          | **134** |

Processing cubre HTTP con JWT firmado, variantes y límites Unicode/JSON, roles reales y autorrevisión, categoría activa bajo bloqueo, información secuencial, prioridad manual, SQL paginado/ordenado, privacidad, ETag, carreras y rollback de auditoría/commit. La migración se prueba tanto desde vacío como sobre V1 con datos y fotografías: conserva el historial, deja `resultingStatus` histórico desconocido en null y exige integridad a las nuevas escrituras. Evidencias comprueba todos los estados y compensa S3 si una revisión congela la incidencia durante la subida. S3 usa dobles aislados; no se habilitó `NEON_STORAGE_TEST_AUTHORIZED`.

Las primeras ejecuciones detectaron fixtures de categorías/departamentos no conformes al esquema, limpiezas y expectativas antiguas de Flyway, una excepción optimista de PATCH al adquirir el bloqueo y una opción trasladada en Jackson 3. Se corrigieron y se repitió la regresión completa. Las expectativas de dos migraciones y 15 tablas sustituyen legítimamente las de V1; no se eliminaron tests. La ejecución final terminó en BUILD SUCCESS. Después solo se completaron comentarios Java y se regeneró Javadoc desde una copia limpia.

Para evitar la lentitud de `target` sobre el montaje Windows, Maven ejecutó una copia interna de `src/` y `pom.xml`, con el montaje de origen de solo lectura. El comando reproducible de la sección Backend utiliza el mismo procedimiento. Se conservaron localmente los XML y el log final en `backend/target/processing-final/`, ignorado por Git. Las pruebas unitarias adicionales y Javadoc se ejecutaron en copias independientes con los comandos de la tabla. Frontend utilizó `CI=true`, `EXPO_NO_TELEMETRY=1` y `EXPO_PUBLIC_API_URL=http://localhost:8080` como el workflow.

Los artefactos OpenAPI se generaron con los comandos de [API](api/README.md#7-validación-y-visualización), incluida la plantilla versionada `redoc-template.hbs`. Un segundo bundle independiente produjo exactamente el mismo SHA-256 que `docs/api/openapi.yaml`: `20e83e70efb3dd14b5d509adb0f54b74a14db03fc7b247a0d3956b122e9f679a`. El lint modular conserva dos avisos de licencia/localhost; el bundle añade tres parámetros preexistentes sin uso (AssessmentId, TrainingId y DocumentId). Javadoc conserva avisos del backend y constructores Lombok; no hay errores. Maven también muestra avisos de instrumentación Mockito/Byte Buddy y APIs Java de dependencias.

`npm ci` informó de 21 vulnerabilidades en el árbol existente (1 baja, 14 moderadas y 6 altas), deprecaciones y un script de instalación bloqueado de `unrs-resolver`; los cinco pasos frontend pasaron. Su actualización requiere una revisión de dependencias aparte: no se aplicó `npm audit fix` ni se cambió el lockfile. Los intentos inicialmente restringidos por permisos/red del entorno se repitieron con autorización.

La revisión funcional/seguridad contrasta matriz de estados, permisos, privacidad y transacciones; la revisión técnica/documental contrasta módulos, SQL, pruebas incluidas, contrato y referencias. Las tres fuentes drawio tienen XML y referencias válidos: conservan todos los IDs, estilos, geometrías y conexiones previos; ER incorpora una quinta página. **En esa ejecución Codex dejó V1 y todos los SVG intactos**. Posteriormente, el usuario regeneró manualmente los SVG e incorporó `relacional-05.svg`; la revisión correctiva distingue ese trabajo de la verificación inicial y conserva las exportaciones. La rama local `codex/tramitacion-fase-1` parte de `63f34b23ae07e20d78383d179bf195f16e98d8f4`, contrastado con `origin/main`, sin upstream, commits nuevos, push ni PR.

### Evidencias y V1 consolidada: verificación del 11/11/2026

Entorno aislado: Maven 3.9.16, Java 25.0.4.1, Spring Boot 4.1.2, Hibernate 7.4.13.Final, Testcontainers 2.0.5 y PostgreSQL 18.6. Se ejecutó Maven en `maven:3.9.16-eclipse-temurin-25`, montando backend y la caché Maven; las IT accedieron al socket Docker con `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`. No se usaron bases compartidas, archivos privados de entorno ni credenciales Neon.

| Comando desde backend                       | Resultado                                                                        |
| ------------------------------------------- | -------------------------------------------------------------------------------- |
| `mvn -B -ntp test`                          | PASS: 171 pruebas unitarias/MVC, 27 suites                                       |
| `mvn -B -ntp clean verify -Ppostgres-it`    | PASS: 171 Surefire + 114 Failsafe = **285 pruebas**, 36 suites                   |
| `mvn -B -ntp failsafe:verify -Ppostgres-it` | PASS: comprobación adicional de los informes finales, sin repetir ni sumar tests |
| `mvn -B -ntp javadoc:javadoc -Ddoclint=all` | PASS: cero errores; 64 avisos de completitud documental                          |

Javadoc se regeneró sin reutilizar su marcador de caché tras completar las descripciones del módulo. Permanecen avisos sobre constructores implícitos/Lombok y comentarios del resto del backend; no se amplía esta entrega a un barrido documental general. Los últimos cambios Java son exclusivamente comentarios.

Los informes XML de la ejecución limpia registran cero fallos, errores, omisiones o flakes. Failsafe contiene AttachmentServicePostgresIT 16, AuthenticationPostgresIT 8, EmailVerificationPostgresIT 12, CategoryPostgresIT 6, UserRepositoryPostgresIT 15, IncidentPostgresIT 18, StaffInvitationPostgresIT 24, DepartmentMembershipPostgresIT 9 y DepartmentPostgresIT 6. Cada contexto aplica solo V1 desde PostgreSQL vacío y Hibernate valida las entidades; se comprueban 14 tablas funcionales, cinco roles y restricciones finales. No se suman pruebas dirigidas ni ejecuciones históricas.

La suite de Evidencias comprueba las cinco rutas con JWT firmado, DTO y cabeceras, visibilidad y pareja de IDs, todos los estados, límite conjunto y carreras con una/cero plazas, bajas lógicas, auditoría atómica, lectura fallida, tamaño declarado falso, rechazo multipart previo al controlador y errores PUT/GET/commit/compensación. El almacenamiento es un doble aislado; las transferencias se comprueban fuera de transacción SQL. Las ocho pruebas de imágenes usan muestras sintéticas JPEG con EXIF/GPS y PNG con texto/eXIf, alfa, corrupción, dimensiones, límites exactos de entrada y expansión excesiva de salida.

La primera ejecución dirigida detectó una consulta derivada que interpretaba incidentId como atributo persistente: se corrigió el recorrido explícito `Incident_Id`. La repetición dirigida pasó con 12 unitarias y 13 IT; la ejecución completa posterior incluye las tres IT adicionales de lectura, rollback de auditoría y conservación del autor histórico. La comprobación previa del esquema con CategoryServiceTest/CategoryPostgresIT también pasó (7 + 6); estas ejecuciones no aumentan el total de 285.

OpenAPI 3.0.3 / contrato 0.7.0: Redocly 2.16.0 validó la fuente modular, regeneró bundle YAML y HTML y validó de nuevo el bundle. Cero errores; la fuente presenta los dos avisos conocidos de licencia/localhost y el bundle añade tres componentes preexistentes sin uso (AssessmentId, TrainingId y DocumentId). Recuento verificado: **34 operaciones implemented, 37 future, 71 totales**. Enlaces/anclas Markdown locales y `git diff --check` correctos; las tres fuentes drawio conservan páginas, IDs, geometría, estilos y conexiones. Codex no modificó los SVG; sus exportaciones se actualizaron manualmente con posterioridad.

`verify -Pneon-storage` no se ejecutó: no se utilizó un entorno S3 de pruebas explícitamente autorizado. El perfil requiere `NEON_STORAGE_TEST_AUTHORIZED=true` y configuración de pruebas; no acredita un ciclo PUT/GET/DELETE en Neon. Tampoco se repitieron frontend, SMTP externo ni despliegue.

### Incidencias: revisión correctiva del 05/11/2026 (histórica)

Este registro conserva los resultados y migraciones de aquella fecha. La entrega vigente usa V1 consolidada, V2 de Tramitación y V5 de Mapas; estas referencias a V2/V3 no son instrucciones de ejecución actuales.

Entorno aislado: Java 25.0.4, Maven 3.9.16, Spring Boot 4.1.2/Hibernate 7.4.13.Final, Testcontainers 2.0.5 y PostgreSQL 18.6 efímero. No se ejecutó NeonConnectivityIT ni se utilizaron credenciales de Neon. El inventario Docker no ofrecía una base PostgreSQL persistente local. El historial remoto de Neon no se ha consultado; V1/V2 se conservan y V3 es aditiva.

`mvn -B -ntp test`: **BUILD SUCCESS, 163 pruebas unitarias/MVC**. `mvn -B -ntp clean verify -Ppostgres-it`: **BUILD SUCCESS, 261 pruebas**, 163 Surefire (26 suites) y 98 Failsafe (ocho suites), sin fallos, errores ni omisiones. No se suman ejecuciones parciales ni resultados históricos.

| Suite                          | Pruebas |
| ------------------------------ | ------: |
| Unitarias/MVC                  |     163 |
| AuthenticationPostgresIT       |       8 |
| EmailVerificationPostgresIT    |      12 |
| StaffInvitationPostgresIT      |      24 |
| DepartmentPostgresIT           |       6 |
| CategoryPostgresIT             |       6 |
| UserRepositoryPostgresIT       |      15 |
| DepartmentMembershipPostgresIT |       9 |
| IncidentPostgresIT             |      18 |
| **Total**                      | **261** |

La cifra anterior de 248 no era reproducible con el árbol recibido para esta revisión: `sqlPaginationFiltersStableOrderAndUnknownQueries` no creaba las incidencias del titular y falló con **esperado 2, obtenido 0**. Se restauró la preparación explícita, con control de otro titular, fechas fijas y limpieza independiente. No se modificó autorización para satisfacer el test. Esa medición sustituyó la cifra anterior en la revisión del 05/11/2026.

También se reprodujeron: cambios de perfil/categoría por sus APIs que alteraban el JSON conservando ETag `"0"`; aceptación con 201 de un cuerpo de 65537 bytes; y dos fallos unitarios por motivos/límites estructurales no comprobados. La revisión incorpora ETag opaca de la representación, 64 KiB con 413 y reglas Java/SQL coherentes mediante V3. Las fechas se truncan a microsegundos para conservar el JSON al releer PostgreSQL.

Las pruebas cubren JWT real, UUIDv7, Unicode/WGS84, propiedad y permisos globales/departamentales, categoría opcional/inactiva histórica, PATCH omitido/null, 428/412/409, no-op, ETag y datos relacionados, paginación/filtros SQL con empates y límites inclusivos, historial filtrado, rollback de auditoría y carrera real con un único ganador. Se comprueban límites de cuerpo en POST/PATCH, Unicode escapado, longitud declarada ausente/falsa y HTTP real por bloques. Las estructuras futuras se prueban en Java/PostgreSQL sin habilitar Tramitación, incluyendo límites exactos, nulos, blancos, CLOSED terminal y ausencia de REOPENED/transición inicial.

V3 se aplica sobre esquema vacío junto a V1/V2 y también sobre un esquema V2 con datos: rechaza una revisión REQUEST_INFORMATION sin motivo, conserva el historial y permite migrar tras corregir explícitamente el dato sintético. La primera regresión completa reveló dos expectativas de recuento de Flyway que debían adaptarse a V3 y distinguir su marcador de creación de esquema; se corrigieron y la repetición completa pasó. Las pruebas PostgreSQL de los módulos anteriores comprueban la limpieza y compatibilidad del nuevo esquema.

Los comandos se ejecutaron dentro de una copia temporal del código en el contenedor, aislando target del host. Desde la raíz, en PowerShell, la ejecución completa se reproduce así:

```powershell
docker run --rm --mount "type=bind,source=$((Get-Location).Path)\backend,target=/src,readonly" --mount type=volume,source=urbanpulse-auth-maven,target=/root/.m2 --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.16-eclipse-temurin-25 sh -c 'mkdir -p /tmp/incident-build; cp -a /src/pom.xml /src/src /tmp/incident-build/; cd /tmp/incident-build; mvn -B -ntp clean verify -Ppostgres-it'
```

Comandos de reproducciones previas y regresión dirigida, desde la copia de backend:

```bash
mvn -B -ntp verify -Ppostgres-it -Dtest=IncidentTest -Dit.test=IncidentPostgresIT#sqlPaginationFiltersStableOrderAndUnknownQueries
mvn -B -ntp verify -Ppostgres-it -Dtest=IncidentTest -Dit.test=IncidentPostgresIT#etagTracksChangesToProjectedResources
mvn -B -ntp test -Dtest=ProcessingStructuresTest
mvn -B -ntp verify -Ppostgres-it -Dtest=IncidentTest -Dit.test=IncidentPostgresIT#incidentBodiesAcceptUnicodeAtTheByteLimitAndRejectExcess
mvn -B -ntp verify -Ppostgres-it -Dtest=IncidentETagsTest -Dit.test=IncidentPostgresIT
mvn -B -ntp verify -Ppostgres-it -Dtest=IncidentETagsTest -Dit.test=IncidentPostgresIT#createDetailAndPatchExposeOnlyRealDataAndAtomicHistory
mvn -B -ntp javadoc:javadoc -Ddoclint=all
```

La verificación dirigida final (`-Dtest=IncidentETagsTest -Dit.test=IncidentPostgresIT`) pasó con 2 unitarias y 18 IT tras añadir aserciones de etiqueta antes válida ya obsoleta y cabeceras repetidas. La comprobación adicional dirigida a createDetailAndPatchExposeOnlyRealDataAndAtomicHistory también pasó (2 unitarias y 1 IT): el digest coincide con los bytes HTTP y Accept con charset UTF-16 obtiene 406, sin necesitar un cambio de implementación. Son repeticiones de las suites anteriores, no pruebas que se sumen al total de 261.

Javadoc con Maven Javadoc Plugin 3.12.0 y `-Ddoclint=all`: **BUILD SUCCESS**, cero errores y **57 avisos de completitud documental**, incluidos constructores generados por Lombok y comentarios incompletos del conjunto del backend. Se corrigió una etiqueta `@Version` interpretada como tag Javadoc y se ampliaron las descripciones del módulo; no se atribuye una ejecución sin avisos.

OpenAPI 0.6.0 se validó con Redocly 2.16.0: cero errores y dos avisos, `info-license` (licencia no declarada) y `no-server-example.com` (localhost). Bundle YAML y HTML se regeneraron con los comandos versionados de [API](api/README.md#7-validación-y-visualización). El bundle tiene 70 operaciones: 29 implemented y 41 future, sin operaciones referenciadas sin estado. La primera llamada npm quedó bloqueada por DNS del entorno restringido; la repetición autorizada completó las tres operaciones.

La revisión de las tres fuentes drawio verifica siete páginas, XML/IDs/conexiones válidos y conservación de geometría/estilos. No se modificaron ni regeneraron SVG durante esta revisión correctiva. Los enlaces Markdown locales y anclas se comprobaron; no hay informes temporales de pruebas incorporados a la rama. No se repitieron frontend, SMTP externo ni despliegue en Neon.

### Evidencia anterior a Incidencias

Evidencia histórica del **04/11/2026**, Java 25.0.4 y PostgreSQL 18.6 efímero: `mvn -B -ntp clean verify -Ppostgres-it`, **BUILD SUCCESS**, **228 pruebas**, sin fallos, errores ni omisiones. Los informes XML registran 21 suites Surefire y siete suites Failsafe. El resultado histórico previo a Usuarios fue de 148 pruebas el 03/11/2026; no se suma a las ejecuciones posteriores.

| Suite                          | Pruebas |
| ------------------------------ | ------: |
| Unitarias/MVC (Surefire)       |     148 |
| AuthenticationPostgresIT       |       8 |
| EmailVerificationPostgresIT    |      12 |
| StaffInvitationPostgresIT      |      24 |
| DepartmentPostgresIT           |       6 |
| CategoryPostgresIT             |       6 |
| UserRepositoryPostgresIT       |      15 |
| DepartmentMembershipPostgresIT |       9 |
| **Total**                      | **228** |

Desde la raíz en PowerShell, con Docker Desktop, el comando reproducible sin credenciales de Neon es:

```powershell
docker run --rm --mount "type=bind,source=$((Get-Location).Path)\backend,target=/app" --mount type=volume,source=urbanpulse-auth-maven,target=/root/.m2 --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /app maven:3.9.16-eclipse-temurin-25 mvn -B -ntp clean verify -Ppostgres-it
```

En esa ejecución histórica, Flyway aplicó solo V1 sobre cada base vacía y Hibernate valida los mapeos. Se comprueban las nueve tablas funcionales, cinco roles y ausencia de semillas de ambos catálogos. Category cubre UUIDv7, límites Unicode Java/SQL, valores por defecto, reserva del nombre inactivo, altas y renombrados concurrentes, rollback y espera real de bloqueo entre PATCH. Las suites anteriores conservan los escenarios de Departamentos e Invitaciones, incluido el canje frente a desactivación concurrente. `NeonConnectivityIT` está excluido; no se utilizan bases compartidas.

Usuarios añade regresiones de normalización previa a validación, límites Unicode Java/SQL, JSON estricto, permisos y paginación. La medición PostgreSQL del listado registra **12 consultas con acceso lazy frente a 4 con carga en lote** para diez usuarios; otra página mantiene cuatro y se comprueba la página vacía. Se verifica la conservación de cuentas vinculadas a todas las FK históricas incluso con lotes de tamaño uno, las bajas concurrentes de administradores, el bloqueo compartido con contraseña, altas/bajas departamentales concurrentes y reingresos con historial. Una comparación de fechas de prueba se ajustó a la precisión de microsegundos de PostgreSQL antes de la ejecución completa satisfactoria.

En frontend, `npm run lint` y `npx tsc --noEmit` finalizaron correctamente el mismo día, sin cambiar dependencias ni el lockfile. OpenAPI se validó y regeneró según la [guía de API](api/README.md#7-validación-y-visualización). Las advertencias de instrumentación Mockito/Byte Buddy y APIs Java de dependencias no impidieron el build. Estas comprobaciones no incluyen un nuevo recorrido SMTP externo, validación móvil ni despliegue en Neon.

## Verificación de Mapas (10/11/2026)

El perfil `postgres-it` incluye `MapPostgresIT`, `MapPerformancePostgresIT` y
`AttachmentServicePostgresIT`, además de las suites anteriores, con PostgreSQL 18
efímero. No incluye NeonConnectivityIT ni NeonAttachmentStorageIT. Tras la integración
del 16/11/2026, las pruebas verifican V1, V2 y V5, inventario de 15 tablas y
limpieza de attachments e incident_information_responses junto con incidents. Mapas aplica V5 sobre V1/V2 con datos
preexistentes en otro esquema, incluida la construcción de índices con search_path
restringido. Las cifras fechadas a continuación corresponden a ejecuciones históricas.

La ejecución de esta entrega usa una copia del backend en Docker, Java 25 y Maven
3.9.16. El intento inicial con Maven/JDK del host Windows no completó la compilación
de tests (acceso denegado y clases no resueltas); se utiliza el entorno aislado del
proyecto para la evidencia final, sin modificar fuentes ajenas para solventarlo.

Desde la raíz, en PowerShell, este comando reproduce la suite completa sin ejecutar
la aplicación con variables privadas del host:

```powershell
$mapsSource = (Resolve-Path backend).Path
docker run --rm --mount "type=bind,source=$mapsSource,target=/src,readonly" --mount type=volume,source=urbanpulse-auth-maven,target=/root/.m2 --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.16-eclipse-temurin-25 sh -c 'mkdir -p /tmp/maps-build; cp -a /src/pom.xml /src/src /tmp/maps-build/; cd /tmp/maps-build; mvn -B -ntp clean verify -Ppostgres-it'
```

En la ejecución de la entrega también se copian los informes Surefire/Failsafe y los
ejemplos capturados a `backend/target`. [Guía de Mapas](maps-backend.md),
[ADR-0017](adr/0017-mapas-provinciales-privacidad.md) y
[recurso oficial reproducible](../backend/src/main/resources/maps/README.md).

Resultado final: **BUILD SUCCESS**, 291 pruebas (175 Surefire y 116 Failsafe), cero
fallos, errores y omisiones. Incluye 12 pruebas unitarias y 18 de integración de
Mapas. Java 25.0.4.1, Maven 3.9.16, Spring Boot 4.1.2 y PostgreSQL 18.6. Se validan
geografía provincial y costa, permisos DB y pertenencias reales, privacidad por
viewport, agrupaciones, límites, migración con datos previos y respuestas de HTTP
al servidor en puerto efímero. La ejecución completa final dura 3 min 27 s.
Los intentos fallidos previos se corrigieron y no se suman a estos resultados.
Los informes de la ejecución final están aislados en `backend/target/maps-verification`
(29 suites Surefire y nueve Failsafe), para no mezclarlos con informes históricos
que ya existían en el target local.

OpenAPI 0.7.0: Redocly 2.16.0 sin errores y con los dos avisos conocidos
`info-license`/`no-server-example.com`. Bundle y HTML regenerados, ejemplos de
HTTP incorporados al contrato y validados con AJV; el verificador rechaza campos
privados adicionales. Comando reproducible:

```sh
npx --yes --package=@redocly/cli@2.16.0 -c "node backend/scripts/validate-map-contract.cjs"
```

Verificación offline del contorno IGN con `generate-map-boundary.mjs --check` y
`git diff --check` correctos. No se ejecutan ni se modifican funcionalidades frontend.
