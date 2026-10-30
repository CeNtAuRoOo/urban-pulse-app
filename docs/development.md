# Guía de desarrollo de UrbanPulse

> **Estado (15/10/2026):** frontend y backend arrancan juntos con Docker Compose. Las conexiones del backend con Neon PostgreSQL y Object Storage se han comprobado mediante un test manual de integración. Desde la revisión de 30/10/2026 están implementados autenticación y departamentos, con V1/V2 y pruebas PostgreSQL aisladas; siguen pendientes los demás módulos y las operaciones completas con fotografías. Se distinguen comandos **comprobados** de procedimientos **pendientes**.

## 1. Requisitos

- Git, VSCode o editor equivalente, Docker Engine/Desktop y Docker Compose v2.
- Frontend sin Docker: Node.js compatible con Expo SDK 57, npm; los Dockerfiles utilizan **Node.js 24**.
- Backend sin Docker: JDK 25 y Maven 3.9.16 o compatibles.
- Acceso privado a credenciales de **Neon PostgreSQL y Object Storage** de la rama `development` para arrancar el backend. No utilizar secretos ni endpoints de producción en pruebas locales.

El repositorio es **público**. Clonarlo no requiere autenticación; enviar cambios sí exige los permisos o el flujo de contribución correspondiente.

```bash
git clone https://github.com/CeNtAuRoOo/urban-pulse-app.git
cd urban-pulse-app
code .
```

`.editorconfig`, `.prettierrc.json` y `.vscode/` establecen convenciones compartidas. Para Java se usa el formateador de la extensión de Java; para TypeScript, Prettier.

## 2. Organización del repositorio

```text
urban-pulse-app/
├── .github/                      # Plantillas; workflows pendientes
├── .vscode/                      # Recomendaciones/configuración compartida
├── backend/
│   ├── src/main/java/es/grupo18/urbanpulse/
│   │   ├── BackendApplication.java
│   │   └── config/, controller/, dto/, entity/, repository/, service/
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── db/migration/         # Incluye Flyway V1 de autenticación y email
│   ├── src/test/java/es/grupo18/urbanpulse/NeonConnectivityIT.java
│   ├── .env.development          # Privado e ignorado por Git (crear localmente)
│   ├── .env.example
│   ├── pom.xml
│   ├── Dockerfile
│   └── Dockerfile.dev
├── frontend/
│   ├── assets/
│   ├── src/app/_layout.tsx        # Expo Router
│   ├── src/app/index.tsx          # Pantalla inicial
│   ├── src/components/, config/, constants/, features/, services/, types/
│   ├── app.json, package.json, package-lock.json, tsconfig.json
│   ├── eslint.config.js, nginx.conf
│   ├── Dockerfile
│   └── Dockerfile.dev
├── docs/
│   ├── api/
│   │   ├── README.md            # Guía y revisión por tags
│   │   ├── openapi.yaml         # Copia completa; no editar manualmente
│   │   └── openapi/
│   │       ├── base-openapi.yaml # Entrada modular
│   │       ├── paths/          # Endpoints en 16 archivos por tag
│   │       └── components/     # Esquemas, parámetros, respuestas, seguridad
│   ├── architecture.md
│   ├── development.md
│   └── deployment.md
├── compose.yaml                  # Solo entorno de desarrollo
└── README.md
```

`package-lock.json` debe conservarse bajo control de versiones para que `npm ci` y Docker sean reproducibles. Los directorios vacíos pueden contener `.gitkeep`.

## 3. Variables de entorno

Cada servicio dispone de un `.env.example` versionado. Las variables **reales** de entorno se guardan en archivos privados ignorados por Git (`frontend/.env` y `backend/.env.development`). Los archivos de ejemplo no contienen secretos.

Desde la raíz en PowerShell:

```powershell
Copy-Item frontend/.env.example frontend/.env
Copy-Item backend/.env.example backend/.env.development
```

En Bash, usar `cp` de manera equivalente.

### Frontend (`frontend/.env`)

```dotenv
EXPO_PUBLIC_API_URL=http://localhost:8888
```

Es la dirección prevista para **el navegador en el mismo ordenador**. `EXPO_PUBLIC_*` queda disponible en el JavaScript cliente y **no puede almacenar contraseñas, tokens privados o claves de Neon**. Todavía no existe una integración HTTP en el frontend; esta URL está preparada para cuando se implemente.

En un móvil físico `localhost` designa el propio teléfono; debe utilizarse una dirección del equipo accesible por LAN o la API HTTPS publicada. En el emulador Android habitual puede emplearse `10.0.2.2` para acceder al equipo anfitrión, sujeto a configuración de red y seguridad de la plataforma.

### Backend (`backend/.env.development`)

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://HOST-DEVELOPMENT:5432/urban-pulse?sslmode=require
SPRING_DATASOURCE_USERNAME=backend
SPRING_DATASOURCE_PASSWORD=PASSWORD_DEVELOPMENT

AWS_ENDPOINT_URL_S3=https://BRANCH-DEVELOPMENT.storage.c-7.eu-central-1.aws.neon.tech
AWS_ACCESS_KEY_ID=ACCESS_KEY_DEVELOPMENT
AWS_SECRET_ACCESS_KEY=SECRET_KEY_DEVELOPMENT

JWT_ISSUER=urbanpulse-development
JWT_SECRET=BASE64_OF_32_OR_MORE_RANDOM_BYTES
CORS_ALLOWED_ORIGINS=http://localhost:7777
```

Los valores `HOST-DEVELOPMENT`, `BRANCH-DEVELOPMENT` y los secretos son **marcadores ficticios**; obtener los valores reales de Neon y no incorporarlos a commits ni PR. La base se llama exactamente `urban-pulse` (con guion) y el rol es `backend`. Los endpoints se corresponden con la rama Neon `development`; para `production`, utilizar un conjunto **diferente** de conexiones y secretos inyectados al desplegar (opcionalmente un archivo local privado `backend/.env.production`, que no debe entrar en imágenes ni Git).

`compose.yaml` carga `backend/.env.development` mediante `env_file`. `application.properties` consume `SPRING_DATASOURCE_*`, `JWT_*` y CORS, y mapea las variables S3 a `storage.s3.*`; `StorageProperties` las vincula y valida, y `StorageConfig` configura un cliente S3 con direccionamiento **path-style** y la cadena estándar de credenciales AWS. Spring Boot lanzado directamente con Maven **no carga automáticamente** un archivo `.env`: hay que exportar las variables o ejecutarlo desde Compose.

## 4. Frontend: instalación y ejecución local (comprobado)

Desde `frontend/`:

```bash
npm ci
npx expo start --web --port 7777
```

Acceder a <http://localhost:7777>. Expo utilizará **Metro**, Expo Router y React Native Web. El código de arranque está en `src/app/index.tsx`; el layout de navegación, en `src/app/_layout.tsx`. `Ctrl+C` detiene el servidor.

La aplicación dispone de scripts `npm start`, `npm run web`, `npm run android`, `npm run ios`, `npm run lint`. `npm run web` no fija el puerto 7777 por sí mismo; para respetarlo se ha validado el comando explícito anterior.

Para pruebas de Android/iOS, se podrá usar `npm start` con Expo Go o un cliente de desarrollo. **No se ha verificado aún una sesión completa en un dispositivo**. El código Android sí llegó a empaquetarse durante una prueba de Expo; no equivale a validar la aplicación en Android/iOS.

## 5. Frontend: desarrollo web con Docker Compose (comprobado)

Desde la raíz del repositorio:

```bash
docker compose up --build frontend
```

El servicio `frontend` usa `frontend/Dockerfile.dev` y expone **7777:7777**. Monta el código con `./frontend:/app` y las dependencias Linux con el volumen `node-modules:/app/node_modules`. Abre <http://localhost:7777>.

Otros comandos útiles:

```bash
docker compose ps
docker compose logs -f frontend
docker compose down
```

Los cambios de los archivos locales se propagan mediante el volumen; Expo normalmente realiza **Fast Refresh**, aunque el comportamiento de edición continua no se ha medido formalmente en todos los entornos Windows/Docker. Si cambian las dependencias, además del lockfile puede ser necesario actualizar el volumen:

```bash
docker compose exec frontend npm ci
docker compose restart frontend
```

Se puede iniciar únicamente el frontend con `docker compose up --build frontend` o ejecutar **ambos servicios** con `docker compose up --build`, una vez preparado `backend/.env.development`. Este último comando se ha verificado el 15/10/2026.

La prueba Docker del 13/10/2026 terminó con compilación y ejecución del frontend. El aviso de descarga de React Native DevTools usó una versión de respaldo y no impidió el arranque.

## 6. Frontend: comprobaciones de calidad (comprobado)

Desde `frontend/`:

```bash
npx expo install --check
npx expo-doctor
npx tsc --noEmit
npm run lint
npx expo export --platform web
```

**Resultados del 13/10/2026:** dependencias compatibles; Expo Doctor **21/21**; TypeScript y ESLint sin errores; exportación web correcta a `dist/`. La exportación es una **compilación** de archivos estáticos, no prueba de la imagen Nginx ni de un despliegue externo. `dist/` está ignorado por Git.

Las herramientas de Expo generan archivos temporales como `expo-env.d.ts`, ignorado en `frontend/.gitignore` por Expo CLI. Las versiones concretas de las bibliotecas se consultan en `frontend/package.json` y `frontend/package-lock.json`.

**Seguridad de dependencias:** `npm install` había informado de avisos de `npm audit` en una instalación anterior. No se ha adjuntado un informe actualizado tras los cambios; por tanto no debe declararse resuelto. Revisar:

```bash
npm audit
npm audit --omit=dev
npm explain uuid
```

Evitar `npm audit fix --force` sin comprobar compatibilidad con Expo SDK 57.

**No hay aún pruebas unitarias o E2E de funcionalidades**, ni clientes API reales. Estas pruebas se incorporarán con los primeros casos de uso.

## 7. Backend: ejecución e integración con Neon (comprobado)

`backend/pom.xml` usa Spring Boot 4.1.2 y Java 25, Web MVC, Validation, JPA/Hibernate, controlador PostgreSQL, Flyway, soporte de validación JWT de Spring Security, Lombok y dependencias de tests. Para Object Storage incorpora AWS SDK for Java 2.x **2.56.0** (BOM y módulo S3) y el procesador de metadatos de `@ConfigurationProperties`.

- `StorageProperties` vincula y valida `storage.s3.endpoint`, `storage.s3.region` y `storage.s3.bucket`.
- `StorageConfig` registra `S3Client` contra el endpoint Neon, usa direccionamiento **path-style** y obtiene credenciales desde las variables estándar `AWS_*`.
- `application.properties` fija el puerto 8888, JDBC, HikariCP (máximo 5 conexiones), JWT 7200 s, políticas de rate limiting, `spring.jpa.hibernate.ddl-auto=validate`, Flyway y `spring.sql.init.mode=never`. No existe perfil Spring de desarrollo adicional.
- El módulo de identidad crea `users`, `roles`, `user_roles` mediante `V1__create_authentication.sql` (probada en PostgreSQL efímero). `flyway_schema_history` ya existía en Neon `development`, pero **no consta aplicada todavía esta V1 allí**. Un arranque normal del backend con la nueva versión y Flyway activo puede aplicar la migración a la base a la que apunten las variables.

Desde la raíz, con `backend/.env.development` configurado:

```bash
docker compose up --build
```

**Comprobado el 15/10/2026:** frontend y backend arrancan mediante Docker Compose. Esta comprobación de 15/10 era anterior al módulo auth y **no valida aún el nuevo despliegue/migración de autenticación en Neon**, ni la imagen Docker de producción. Para backend solo: `docker compose up --build backend`.

### 7.1. Test de conectividad real (comprobado; ejecución explícita)

Desde la raíz:

```bash
docker compose run --rm backend mvn -B -Dtest=NeonConnectivityIT test
```

**Comprobado el 15/10/2026:** `NeonConnectivityIT` supera dos pruebas en `development`: una consulta JDBC que verifica `current_database() = 'urban-pulse'` y `current_user = 'backend'`, y `HeadBucket` sobre el bucket privado `incident-evidence`. Durante ese test se desactivan Flyway y la validación de Hibernate exclusivamente en su contexto; **no modifica tablas ni objetos**. `HeadBucket` no comprueba subida/descarga/borrado, y el test no prueba un despliegue en producción. **Atención:** el nombre de base, el rol y el bucket son iguales en las dos ramas de Neon. El test no verifica por sí mismo el ID de rama; antes de ejecutarlo hay que confirmar que las URL efectivas de JDBC y S3 apuntan a `development`.

### 7.2. Política de tests y consumo del plan gratuito

**Ejecución recomendada (Java 25, Maven 3.9, Docker):**

```bash
cd backend
mvn -B -ntp clean verify -Ppostgres-it
```

Codex registró el **25/10/2026** 16 pruebas unitarias/MVC y 7 de integración PostgreSQL, todas superadas. La última ronda de cambios (503, CITIZEN y límites de cuerpo) añade o modifica pruebas; **repetir el comando y reflejar su resultado real antes del commit de código**. No confundir esa evidencia histórica con una verificación del nuevo estado.

**Configuración de autenticación:** `JWT_SECRET` debe decodificarse a al menos 32 bytes aleatorios y jamás usar el valor ficticio de `auth-test.properties` fuera de tests. HS256, emisor configurable, 7200 s; BCrypt 12. `security.rate-limit`: registro/IP 5/300 s, login/IP 20/1200 s, login/email 5/300 s, máximo 10 000 claves y TTL propio de cada política. Los cinco POST auth admiten un cuerpo máximo de 16 KiB; registro/login consumen sus buckets, mientras confirmación/reenvío consumen buckets propios. Cambio de contraseña no consume buckets. `auth-test.properties` declara los parámetros de prueba y no debe copiarse al despliegue.

**Puesta en marcha:** `server.forward-headers-strategy=none` toma la IP de la conexión; revisar proxies antes de producción. El frontend debe renovar login al recibir `401` por JWT revocado, pero no debe cerrar sesión ante un `503` de base indisponible. `503` se emite con `ProblemDetail` y sin `WWW-Authenticate`. Antes de ejecutar `docker compose up` con el nuevo backend, revisar que el destino Flyway es **development** y respaldar/planificar la reversión.

- **Unitarios (`*Test`, `*Tests`, `Test*`, `*TestCase`):** deben ser rápidos, deterministas y no necesitar Neon; utilizar mocks cuando proceda.
- **Integración local de persistencia:** disponibles `AuthenticationPostgresIT`, `EmailVerificationPostgresIT` y `DepartmentPostgresIT` en PostgreSQL 18 efímero (Testcontainers), nunca `development` compartido ni `production`. Activar expresamente `-Ppostgres-it`.
- **Integración real con Neon (`*IT`):** `NeonConnectivityIT` se ejecuta únicamente bajo demanda en `development`, con credenciales autorizadas; no participa en `postgres-it`, que usa solo Testcontainers.

Maven Surefire **no incluye `*IT` por defecto** con la configuración actual, aunque `-Dtest=NeonConnectivityIT` permite seleccionarlo expresamente. `mvn test`, `mvn package` y `mvn verify` ejecutan los tests de Surefire que corresponden, **no** esa prueba `*IT` por defecto. El perfil Maven `postgres-it` ya configura Failsafe para `AuthenticationPostgresIT`, `EmailVerificationPostgresIT` y `DepartmentPostgresIT`; `mvn clean verify -Ppostgres-it` añade esas suites con contenedores efímeros y **no ejecuta `NeonConnectivityIT`**. `mvn spring-boot:run`, reiniciar el backend o `docker compose up --build` con `Dockerfile.dev` **no ejecutan los tests** automáticamente. La imagen de producción utiliza `mvn package` durante el build, por lo que sí ejecutará los tests unitarios normales. No introducir tests que escriban contra la base compartida en el ciclo rutinario o en CI sin aislamiento.

Desde el contenedor, ejecutar solo los tests habituales (cuando existan):

```bash
docker compose run --rm backend mvn -B test
```

La suite auth dispone de pruebas unitarias/MVC y pruebas de integración aisladas. Una vez repetidas las pruebas sobre el último código, aplicar la V1 de forma controlada a `development`, no a `production`.

## 8. Producción web (configuración creada, prueba pendiente)

El `frontend/Dockerfile` exporta una SPA de Expo y copia `dist/` a Nginx; `frontend/nginx.conf` escucha en el puerto 7777 y redirige rutas de Expo Router a `index.html`.

Para comprobar **localmente** esa imagen en una fase posterior, desde la raíz, en una sola línea compatible con PowerShell:

```powershell
docker build -f frontend/Dockerfile --build-arg EXPO_PUBLIC_API_URL=http://localhost:8888 -t urbanpulse-frontend:local frontend
docker run --rm -p 7777:7777 urbanpulse-frontend:local
```

Esto **no figura en las pruebas adjuntadas**. El argumento `EXPO_PUBLIC_API_URL` queda integrado en el cliente en tiempo de compilación. La URL debe ser pública/HTTPS en producción y el backend deberá autorizar el origen mediante su política CORS.

## 9. Colaboración y PR

Se sigue el flujo [CONTRIBUTING.md](../CONTRIBUTING.md): una rama por cambio, commits descriptivos, pruebas y Pull Request hacia `main`. Para un cambio mixto backend/frontend:

```bash
git status
git diff --check
git add -- backend/ docs/ README.md CHANGELOG.md CONTRIBUTING.md SECURITY.md
git diff --cached --name-only
git commit -m "docs: actualizar guías de desarrollo"
```

**Para tareas de API (design-first):** tratar cada tag como una tarea independiente. Antes del código, revisar como **una única especificación** los endpoints/DTO del tag y ambas vistas del dominio en `docs/diagrams/`; concretar conjuntamente los cambios y valorar impacto en otros tags. La fuente de verdad es `docs/api/openapi/base-openapi.yaml` más sus archivos referenciados. `docs/api/openapi.yaml` es la copia monolítica actual: regenerarla con Redocly y no editarla a mano. Regenerar también `docs/api/openapi.html` con `build-docs`. Los comandos de validación y la lista de comprobaciones para cada tag están en el [contrato dominio–API](api/domain-openapi.md) y en la [guía de la API](api/README.md). La revisión documental no equivale a disponer de endpoints implementados.

Comprobar que no se han añadido secretos, datos personales reales ni directorios generados (`node_modules`, `dist`, `target`, `.env`). Confirmar además que `frontend/package-lock.json` esté versionado: `git ls-files frontend/package-lock.json`.

## 10. Problemas frecuentes

- `expo/tsconfig.base` no encontrado: comprobar `node_modules` y `npm ci`, abrir `frontend/` en VSCode y reiniciar el servidor TypeScript.
- Error JSX tras el anterior: normalmente desaparece cuando TypeScript resuelve la configuración Expo.
- `localhost` falla en móvil: utilizar la IP de la máquina alcanzable desde el dispositivo o una URL pública; comprobar firewall/HTTPS.
- Puerto 7777 ocupado: detener el servidor Expo/Docker anterior antes de lanzar otro.
- Docker actualiza el código pero no las dependencias: sincronizar el volumen `node-modules` con `npm ci`.
- Backend falla sin Neon: comprobar `backend/.env.development`, conexión JDBC/TLS, credenciales S3, `storage.s3.*`, permisos y migraciones Flyway; no confundir con un error del frontend.
- `NeonConnectivityIT` no aparece en `mvn test`: es normal; invocarlo con `-Dtest=NeonConnectivityIT` y **solo** contra `development`.
- `HeadBucket` correcto no implica subida/descarga: esas operaciones se probarán al desarrollar el servicio de fotografías.
- Clonación: la lectura del repositorio público no requiere permisos; para enviar cambios sí hay que autenticar y respetar GitHub Flow.

## 11. Próximas pruebas y referencias

Pendientes: imagen Nginx de producción, modelo y migraciones de los módulos restantes, operaciones completas S3, aplicación móvil en dispositivo, Fast Refresh sistemático, funcionalidad de negocio y workflows GitHub Actions.

- [Arquitectura](architecture.md)
- [Despliegue](deployment.md)
- [API](api/README.md)
- [ADR](adr/README.md)

## Verificación del correo en desarrollo con Mailpit

Para probar el registro y la confirmación localmente, el backend necesita los secretos JWT habituales y `EMAIL_VERIFICATION_SECRET` **Base64 de al menos 32 bytes aleatorios, diferente del JWT**. No versionar valores reales. `backend/.env.example` identifica `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS`, `MAIL_FROM` y el secreto HMAC. Para Mailpit, establecer `MAIL_HOST=mailpit` dentro de Docker Compose (el `environment` del backend ya lo fija si no hay otro valor), `MAIL_PORT=1025`, `MAIL_USERNAME=`/`MAIL_PASSWORD=` vacíos, `MAIL_SMTP_AUTH=false`, `MAIL_STARTTLS=false`, `MAIL_FROM=no-reply@urbanpulse.test` y un `EMAIL_VERIFICATION_SECRET` privado. Si se ejecuta Java en el host, usar `MAIL_HOST=localhost`. No se necesita registrar ninguna cuenta de Brevo ni comprar dominio.

Desde la raíz:

```bash
docker compose up -d mailpit
```

La bandeja de pruebas se consulta en <http://localhost:8025> y el SMTP expone al host `127.0.0.1:1025`. Para probar la aplicación completa, arrancar también el backend **solo tras comprobar a qué base apunta** `backend/.env.development`: Flyway contiene la V1 ampliada y puede migrar esa base al iniciar. No utilizar nunca Neon `production` para pruebas. En el registro recibirás `201 TokenDTO` con acceso limitado y un código en Mailpit. Confirmar por `POST /api/v1/auth/email-verifications/confirm` con Bearer y `{ "code": "01234567" }` (sustituir por el código real); reenvío `POST /api/v1/auth/email-verifications/resend` sin cuerpo. El correo no se entrega fuera de Mailpit.

El job `EmailVerificationCleanup` se programa por defecto a la hora en punto con `security.email-verification.cleanup-cron=0 0 * * * *`, 100 filas por lote y máximo diez lotes por fase. En tests el cron vale `-` y no se ejecuta. **Detalle de configuración pendiente de homogeneizar:** `.env.example` declara `EMAIL_VERIFICATION_CLEANUP_CRON`, pero `application.properties` no interpola esa variable; para usarla debe configurarse realmente la propiedad `security.email-verification.cleanup-cron` o cambiar el placeholder del archivo de configuración. Las cuentas no verificadas vencen tras 24 h y se eliminan cuando pase el siguiente barrido; no ejecutar esta limpieza sobre datos compartidos sin revisar su política.

Tests declarados por Codex: `mvn -B -ntp clean verify -Ppostgres-it`, **55 superados**, mediante Testcontainers PostgreSQL 18; la prueba real backend+Mailpit en Compose sigue pendiente. El perfil `postgres-it` incluye actualmente `AuthenticationPostgresIT`, `EmailVerificationPostgresIT` y `DepartmentPostgresIT`; el resultado de 55 corresponde a la ejecución histórica del módulo de correo.

## Departamentos municipales — revisión 30/10/2026

**Verificación final 31/10/2026:** `mvn -B -ntp verify -Ppostgres-it` ejecutado dentro de Docker Compose con target aislado y socket Docker: **55 pruebas unitarias/MVC + 25 PostgreSQL, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**. Comando reproducible completo y límites de la comprobación en el [informe de cierre](departments-finalization.md#6-pruebas-y-verificaciones-realizadas).

El tag Catálogos implementa exclusivamente `Department`: GET listado/detalle en `/api/v1/departments`, POST y PATCH. GET exige identidad y correo verificado; escritura añade rol global ADMIN, consultado en PostgreSQL por la seguridad existente. Desactivar mediante `active=false` conserva el registro y su UUID; no existe DELETE.

Nombre (2–120) y código ASCII (2–20) se recortan con `trim()` antes de validar; se conservan espacios interiores del nombre y se convierte el código a mayúsculas. Unicidad sin distinguir mayúsculas. Descripción opcional hasta 500; alta activa por defecto. PATCH omite para conservar, rechaza null explícito (400), permite descripción vacía y exige algún campo (422). El listado incluye activos/inactivos ordenados por nombre según la collation de PostgreSQL. Los parches concurrentes bloquean la fila durante la transacción.

`Department` usa UUIDv7 Hibernate según ADR-0011. V2 crea `departments`, índices únicos y semillas fijas; se conserva V2 sin modificar su checksum. El default SQL uuidv7() cubre inserciones externas. Las fechas son internas, `createdAt` inmutable y `updatedAt` cambia al modificar datos. No se crean FK hacia módulos todavía no implementados.

Errores comunes ProblemDetail con `code`, `title`, `status`, `detail`, `instance`: 400 entrada no interpretable/UUID inválido, 401 JWT, 403 permisos/verificación, 404 DEPARTMENT_NOT_FOUND, 409 DEPARTMENT_DUPLICATE, 422 validación, 500 fallo interno y 503 indisponibilidad tipada. Solo SQLSTATE 23505 de los dos índices conocidos se traduce a duplicado. Consulte [informe de cierre](departments-finalization.md) para resultados y limitaciones.
