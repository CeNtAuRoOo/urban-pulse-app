# Guía de desarrollo de UrbanPulse

> **Estado a 05/11/2026:** la estructura Java es modular (`auth`, `identity`, `invitations`, `organization`, `categories`, `incidents`, `audit`, `shared`). Incidencias añade V2 y seis operaciones al núcleo anterior de V1. La conectividad Neon `development` verificada el 15/10 es anterior a las migraciones de auth; **no consta V1 consolidada aplicada ni probada allí**.

## 1. Requisitos y clonación

- Git, VS Code u otro editor, Docker Engine/Desktop y Docker Compose v2.
- Backend en el host: **JDK 25**, Maven 3.9.16 o compatible y Docker para Testcontainers.
- Frontend en el host: Node.js compatible con **Expo SDK 57** y npm; las imágenes usan Node.js 24.
- Secretos privados de Neon `development` solo para conectar a ese servicio. Las suites Maven `-Ppostgres-it` usan PostgreSQL efímero y **no** necesitan credenciales de Neon. El informe histórico menciona un `compose.test.yaml` para HTTP, que no aparece en el repositorio.

```bash
git clone https://github.com/CeNtAuRoOo/urban-pulse-app.git
cd urban-pulse-app
```

La lectura del repositorio público no requiere autenticación; el push sí requiere permisos. Se aplican `.editorconfig`, `.prettierrc.json` y ajustes de `.vscode/` para mantener coherencia entre Java, TypeScript y Markdown.

## 2. Estructura actual

```text
urban-pulse-app/
├── .github/                         # Plantillas; CI/CD pendiente
├── .vscode/
├── backend/
│   ├── src/main/java/es/grupo18/urbanpulse/
│   │   ├── BackendApplication.java
│   │   ├── auth/{api,config,dto,mail,model,repository,security,service}/
│   │   ├── identity/{api,dto,model,repository,service}/
│   │   ├── invitations/{api,config,dto,mail,model,repository,security,service}/
│   │   ├── organization/{api,dto,model,repository,service}/
│   │   ├── categories/{api,dto,model,repository,service}/
│   │   ├── incidents/{api,dto,model,repository,service}/
│   │   ├── audit/{model,repository}/
│   │   ├── incidents/{api,integration,model,service}/
│   │   └── shared/{error,mail,pagination,ratelimit,security,storage}/
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── db/migration/
│   │       ├── V1__initial_schema.sql
│   │       └── V2__incidents.sql
│   ├── src/test/java/es/grupo18/urbanpulse/
│   │   ├── auth/{api,integration,service}/
│   │   ├── identity/{api,dto,integration,service}/
│   │   ├── invitations/{api,integration,service}/
│   │   ├── organization/{api,integration,model,service}/
│   │   ├── categories/{api,integration,service}/
│   │   ├── incidents/{api,integration,model,service}/
│   │   └── shared/
│   │       ├── error/, mail/, ratelimit/, security/
│   │       └── storage/integration/NeonConnectivityIT.java
│   ├── Dockerfile, Dockerfile.dev
│   ├── .env.example, .env.development (local, ignorado)
│   └── pom.xml
├── frontend/
│   ├── assets/
│   ├── src/app/{_layout.tsx,index.tsx}
│   ├── src/{components,config,constants,features,services,types}/
│   ├── app.json, package.json, package-lock.json, tsconfig.json
│   ├── eslint.config.js, nginx.conf, Dockerfile, Dockerfile.dev
│   └── .env.example, .env (local, ignorado)
├── docs/
│   ├── api/{README.md,domain-openapi.md,openapi.yaml,openapi.html,openapi/}
│   ├── adr/, diagrams/, architecture.md, development.md
│   ├── deployment.md, domain-model.md, auth-design.md
│   └── README.md
├── compose.yaml                     # Neon development + Mailpit local
├── README.md, CONTRIBUTING.md, CHANGELOG.md, SECURITY.md
└── .editorconfig
```

Los tests de integración PostgreSQL están en `auth/integration`, `identity/integration`, `invitations/integration`, `organization/integration`, `categories/integration` e `incidents/integration`. No situar `NeonConnectivityIT` en la raíz del árbol de pruebas ni inferir que `mvn test` ejecuta suites `*IT` automáticamente. El proyecto sigue siendo **un solo backend Spring Boot**, con ocho módulos funcionales y transversales. Consultar [Arquitectura](architecture.md) para las responsabilidades de cada módulo.

## 3. Configuración y secretos

Los `.env.example` versionados documentan variables pero no contienen secretos reales. Copiar los ejemplos a archivos privados desde la raíz (PowerShell):

```powershell
Copy-Item frontend/.env.example frontend/.env
Copy-Item backend/.env.example backend/.env.development
```

En Bash usar `cp`. **Spring Boot ejecutado por Maven fuera de Compose no carga automáticamente estos `.env`**: exportar las variables en el entorno o iniciar con Docker Compose.

### Frontend

```dotenv
EXPO_PUBLIC_API_URL=http://localhost:8888
```

`EXPO_PUBLIC_*` se incluye en el JavaScript del navegador; no puede contener tokens privados, passwords ni claves Neon. Aún no hay un cliente HTTP funcional. Desde un móvil, `localhost` se refiere al dispositivo; usar una IP LAN accesible o URL HTTPS. El emulador Android suele utilizar `10.0.2.2` para acceder al host, según la configuración.

### Backend y Neon

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://HOST-DEVELOPMENT:5432/urban-pulse?sslmode=require
SPRING_DATASOURCE_USERNAME=backend
SPRING_DATASOURCE_PASSWORD=PASSWORD_DEVELOPMENT
AWS_ENDPOINT_URL_S3=https://BRANCH-DEVELOPMENT.storage.c-7.eu-central-1.aws.neon.tech
AWS_REGION=eu-central-1
STORAGE_BUCKET=incident-evidence
AWS_ACCESS_KEY_ID=ACCESS_KEY_DEVELOPMENT
AWS_SECRET_ACCESS_KEY=SECRET_KEY_DEVELOPMENT
JWT_ISSUER=urbanpulse-development
JWT_SECRET=BASE64_OF_32_OR_MORE_RANDOM_BYTES
EMAIL_VERIFICATION_SECRET=DIFFERENT_BASE64_OF_32_OR_MORE_RANDOM_BYTES
STAFF_INVITATION_SECRET=ANOTHER_DIFFERENT_BASE64_OF_32_OR_MORE_RANDOM_BYTES
CORS_ALLOWED_ORIGINS=http://localhost:7777
```

Son **marcadores ficticios**: no copiar valores reales a Git. La BD histórica se llama `urban-pulse`, el rol es `backend` y el bucket S3 privado es `incident-evidence`. La rama Neon debe confirmarse **por los endpoints efectivos** y no solo por nombres de base/rol/bucket. `compose.yaml` carga `backend/.env.development`; `StorageProperties` y `StorageConfig` configuran S3 con credenciales AWS estándar y direccionamiento path-style. La configuración de producción debe ser distinta y suministrarse externamente.

`application.properties` mantiene los ajustes comunes: puerto **8888**, HikariCP, `spring.jpa.open-in-view=false`, `ddl-auto=validate`, Flyway, caducidades y cuotas; Hibernate **no** genera tablas. El token HS256 dura **7200 s** y las invitaciones **7 días**. Los `.env` contienen conexiones, credenciales, emisor JWT, orígenes CORS y transporte SMTP de cada entorno; los secretos JWT/HMAC deben ser distintos. El archivo de propiedades de tests (`auth-test.properties`) nunca se utilizará como configuración productiva.

### Correo local

`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS` y `MAIL_FROM` configuran el transporte. En Compose, Mailpit usa SMTP `mailpit:1025` dentro de la red y publica en el host `127.0.0.1:1025`; su bandeja web está en <http://localhost:8025>. Un ejemplo local usa `MAIL_FROM=no-reply@urbanpulse.test`, sin autenticación ni STARTTLS. No se necesita contratar un proveedor externo para esta fase. La captura en Mailpit **no** equivale a entrega real. Ambos eventos de correo de auth/Invitaciones usan listener `AFTER_COMMIT`.

## 4. Frontend: ejecutar y verificar

En `frontend/`:

```bash
npm ci
npx expo start --web --port 7777
```

Acceder a <http://localhost:7777>. La pantalla de arranque está en `src/app/index.tsx` y el layout en `_layout.tsx`. También se dispone de `npm start`, `npm run web`, `npm run android`, `npm run ios` y `npm run lint`. `npm run web` no garantiza por sí solo el puerto 7777.

Desde la raíz, Docker usa `frontend/Dockerfile.dev` y mantiene las dependencias Linux en volumen propio:

```bash
docker compose up --build frontend
docker compose logs -f frontend
docker compose down
```

Cambios de dependencias pueden requerir `docker compose exec frontend npm ci` y reinicio. El 13/10/2026 se documentó ejecución/exportación web correcta; el 15/10/2026, arranque de frontend y backend en Compose **antes de las migraciones actuales**.

Para calidad:

```bash
cd frontend
npx expo install --check
npx expo-doctor
npx tsc --noEmit
npm run lint
npx expo export --platform web
```

**Evidencias del 13/10/2026:** Expo Doctor 21/21, TypeScript/ESLint sin errores y exportación SPA correcta a `dist/` (ignorado). No prueba Nginx productivo, Android/iOS completo ni rutas de negocio. Si npm informa de vulnerabilidades, revisar `npm audit` y `npm audit --omit=dev`; no usar `npm audit fix --force` sin verificar compatibilidad Expo.

## 5. Backend: ejecución y migraciones

V1 crea nueve tablas funcionales y cinco roles; V2 añade cuatro tablas de Incidencias y estructuras base. No se precargan cuentas ADMIN, departamentos ni categorías. Está destinada a una base vacía; el historial y las decisiones de consolidación se recogen en [ADR-0006](adr/0006-flyway.md).

**Precaución:** el Compose principal conecta con Neon **development** y un backend nuevo puede ejecutar **Flyway V1, V2 y V3** al arrancar. No iniciar a ciegas con secretos de producción ni sobre bases de datos compartidas. Hacer backup/plan de migración y verificar `SPRING_DATASOURCE_URL`.

```bash
# Desde la raíz, con backend/.env.development correctamente configurado:
docker compose up --build
# O únicamente el servicio de backend:
docker compose up --build backend
```

No consta una ejecución de ese Compose con la migración V1 consolidada actual en Neon. `backend/Dockerfile.dev` está orientado al desarrollo, y `backend/Dockerfile`/`frontend/Dockerfile` no constituyen despliegues de producción verificados.

Para conectividad **solo de lectura** histórica con Neon `development`, desde la raíz:

```bash
docker compose run --rm backend mvn -B -Dtest=NeonConnectivityIT test
```

`NeonConnectivityIT` está bajo `shared/storage/integration`, pero Maven lo selecciona por nombre; la ejecución de 15/10/2026 comprobó JDBC (`current_database()='urban-pulse'` y `current_user='backend'`) y `HeadBucket` sobre el bucket privado. Para ese contexto desactiva Flyway y validación Hibernate; no prueba migraciones, escritura, fotos completas ni que el endpoint pertenezca a la rama adecuada sin comprobar su URL.

## 6. Pruebas automáticas aisladas

Desde `backend/`, con Java 25, Maven y Docker:

```bash
mvn -B -ntp test                       # Surefire: pruebas unitarias y MVC
mvn -B -ntp clean verify -Ppostgres-it # Surefire + Failsafe: IT con PostgreSQL efímero
```

El perfil `postgres-it` incluye explícitamente `AuthenticationPostgresIT`, `EmailVerificationPostgresIT`, `StaffInvitationPostgresIT`, `UserRepositoryPostgresIT`, `DepartmentPostgresIT`, `CategoryPostgresIT`, `DepartmentMembershipPostgresIT` e `IncidentPostgresIT`, cada una con PostgreSQL 18 de Testcontainers. **No ejecuta** `NeonConnectivityIT`. Las invocaciones normales `mvn test`, `mvn package`, `mvn verify` no ejecutan `*IT` por defecto; `mvn spring-boot:run` y `docker compose up --build` con `Dockerfile.dev` tampoco lanzan las suites automáticamente.

### 6.1. Evidencia histórica de SMTP

La entrega de Invitaciones verificó un recorrido HTTP con JAR real, PostgreSQL en tmpfs y Mailpit: registro/verificación, emisión y canje global/departamental, revocación, filtros, cuotas, limpieza de cuenta pendiente y ausencia de secretos en respuestas. El resultado fue satisfactorio y solo acredita captura SMTP local. Los archivos de aquel runner no están versionados; la comprobación reproducible del árbol actual es Maven/Testcontainers. No acredita entrega en Internet ni despliegue en Neon.

### 6.2. Validación del contrato OpenAPI

Editar únicamente la fuente modular y regenerar bundle/HTML siguiendo los [comandos de OpenAPI](api/README.md#7-validación-y-visualización).

## 7. Flujos de auth, Invitaciones y catálogos

Category dispone de su módulo independiente y cuatro operaciones; véase [contrato de categorías](api/README.md#contrato-de-categorías).

El catálogo de departamentos se administra mediante cuatro operaciones de `organization`, con las reglas de [contrato de Departamentos](api/README.md#51-departamentos-implementados-catálogos). Ya permite crear unidades reales para emitir invitaciones, sin SQL manual. La auditoría específica de escrituras de Departamentos queda pendiente de especificación (RF18).

Las cinco rutas `POST` de auth bajo `/api/v1` son registro, login, cambio de contraseña, confirmación de correo y reenvío. El registro emite JWT limitado hasta verificar correo. El mismo JWT funciona después de confirmar y, si se canjea una invitación global, puede reconocer los nuevos roles consultados en PostgreSQL. La limpieza horaria de `EmailVerificationCleanup` elimina cuentas aún sin verificar después de 24 horas; `EMAIL_VERIFICATION_CLEANUP_CRON` se puede configurar (`-` desactiva el job en tests).

Invitaciones añade **cinco rutas** administrativas/de canje, sin crear cuentas al emitir. `STAFF_INVITATION_SECRET` protege HMAC-SHA256 de 256 bits, independiente de JWT y verificación. La caducidad son siete días por defecto y las cuotas de canje utilizan buckets independientes. La estructura Java está bajo `invitations/`, `identity/`, `organization/` y `audit/`; el correo SMTP y la seguridad comunes están en `shared/`. Las decisiones de seguridad, códigos y concurrencia están en [ADR-0007](adr/0007-invitaciones-personal.md); las rutas, DTO, filtros y errores, en [API](api/README.md). Esta guía conserva las evidencias históricas y los comandos de verificación.

## 8. Imagen web productiva prevista (sin ejecución acreditada)

`frontend/Dockerfile` exporta la SPA y copia `dist/` a Nginx (`frontend/nginx.conf`, puerto 7777 y fallback a `index.html`). Para una comprobación **futura**, desde la raíz:

```bash
docker build -f frontend/Dockerfile --build-arg EXPO_PUBLIC_API_URL=http://localhost:8888 -t urbanpulse-frontend:local frontend
docker run --rm -p 7777:7777 urbanpulse-frontend:local
```

No está documentada una ejecución de esta imagen. En producción `EXPO_PUBLIC_API_URL` debe ser HTTPS y CORS debe permitir el origen específico. Antes de desplegar también faltan dominio/TLS, SMTP real, rotación y custodia de secretos, administración inicial, retención y respaldo de datos, y CI/CD.

## 9. Colaboración, problemas habituales y referencias

Para cada tag, revisar como una unidad el OpenAPI modular, ambas vistas de dominio y los ADR, implementar el subconjunto necesario y regenerar diagramas/exports si cambian. Consultar [CONTRIBUTING](../CONTRIBUTING.md), [Modelo de dominio](domain-model.md) y [Contrato dominio–API](api/domain-openapi.md). Comprobar `git diff --check`, secretos, rutas relativas y cambios en artefactos derivados antes del PR.

- Si fallan tipos Expo/JSX, ejecutar `npm ci`, revisar `expo/tsconfig.base` y reiniciar el servidor de TypeScript.
- Si `localhost` no funciona en el móvil, usar IP de host/HTTPS y comprobar firewall.
- Si Docker no refleja nuevas dependencias, actualizar el volumen de `node_modules` con `npm ci`.
- Si falla backend por credenciales, comprobar JDBC/TLS, variables S3, permisos y destino de Flyway; no confundir con error de frontend.
- Si `NeonConnectivityIT` no aparece con `mvn test`, es normal: invocarlo expresamente, **solo** con Neon `development` autorizado.
- `HeadBucket` correcto no garantiza subida/descarga/borrado; esas pruebas corresponden al futuro módulo de evidencias.

Pendientes funcionales: tramitación de incidencias, fotografías reales, equipos, API de retirada de permisos, móvil en dispositivos, Nginx, proveedor SMTP real, migración Neon de V1 consolidada y workflows de CI/CD. Referencias: [Arquitectura](architecture.md), [Despliegue](deployment.md), [API](api/README.md), [ADR-0007 (Invitaciones)](adr/0007-invitaciones-personal.md).

## Verificación del backend

### Incidencias: revisión correctiva del 05/11/2026

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

La cifra anterior de 248 no era reproducible con el árbol recibido para esta revisión: `sqlPaginationFiltersStableOrderAndUnknownQueries` no creaba las incidencias del titular y falló con **esperado 2, obtenido 0**. Se restauró la preparación explícita, con control de otro titular, fechas fijas y limpieza independiente. No se modificó autorización para satisfacer el test. Esta medición sustituye aquella cifra como evidencia vigente.

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

El perfil `postgres-it` incorpora `MapPostgresIT` con PostgreSQL 18 efímero y mantiene
excluido `NeonConnectivityIT`. No se usan secretos ni bases compartidas. Las cinco
suites antiguas que enumeraban todas las versiones Flyway se acotan a sus versiones
base V1–V3; la prueba de actualización de Incidencias apunta expresamente a V3.
Mapas comprueba por separado la nueva migración, incluidos datos preexistentes en
un esquema distinto de public y construcción de índices con search_path restringido.

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
[ADR-0015](adr/0015-mapas-provinciales-privacidad.md) y
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
