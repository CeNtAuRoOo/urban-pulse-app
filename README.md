# UrbanPulse

**UrbanPulse** es una plataforma cloud en desarrollo para gestionar incidencias urbanas: registro de avisos con ubicación y fotografías, validación, prioridad, asignación municipal y enriquecimiento posterior con contexto de la ciudad. El proyecto sigue un enfoque incremental de **diseño dominio–OpenAPI y desarrollo por tags**, documentando decisiones mediante ADR. El núcleo de identidad, los perfiles y administración de usuarios, las invitaciones y la administración de departamentos y categorías ya tienen implementación backend; el resto de funcionalidades sigue en diseño.

## Estado del proyecto (04/11/2026)

| Área                                         | Estado                                                                                                                                                                                        |
| -------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Frontend web                                 | Expo SDK 57/React Native Web con pantalla inicial, exportación web y desarrollo con Compose verificados. No hay integración real con los endpoints.                                           |
| Frontend Android/iOS                         | Código compartido; prueba funcional completa en dispositivo pendiente.                                                                                                                        |
| Backend                                      | Java 25/Spring Boot 4.1.2; **monolito modular** con `auth`, `identity`, `invitations`, `organization`, `categories`, `audit` y `shared`.                                                      |
| Autenticación                                | Registro, login, cambio de contraseña, confirmación y reenvío de verificación de correo implementados.                                                                                        |
| Usuarios                                     | Perfil propio y administración ADMIN, listado paginado y pertenencias activas/históricas; protección del último administrador.                                                                |
| Invitaciones                                 | Emisión/listado/consulta/revocación por ADMIN y canje por usuario verificado implementados; sin registro especial para personal.                                                              |
| Departamentos                                | Listado/detalle, creación y PATCH; escritura por ADMIN verificado y desactivación sin borrado.                                                                                                |
| Categorías                                   | Listado/detalle, alta y PATCH; nombre Unicode único, desactivación/reactivación sin borrado.                                                                                                  |
| PostgreSQL                                   | V1 consolidada para bases vacías; evidencia actual en la guía de desarrollo. Conectividad Neon `development` comprobada previamente; **no se acredita aplicación de V1 consolidada en Neon**. |
| Object Storage                               | Conectividad S3/`HeadBucket` de Neon verificada anteriormente; operaciones de fotografías pendientes.                                                                                         |
| Incidencias, tramitación, equipos, analítica | Contrato y modelo revisables, **sin endpoints funcionales implementados**.                                                                                                                    |
| Despliegue real/CI/CD                        | Pendientes. Compose de desarrollo y runner HTTP de pruebas no acreditan producción.                                                                                                           |

Los [diagramas C4, ER y clases](docs/diagrams/README.md) incluyen los módulos implementados y el diseño futuro identificado como tal. Sus SVG se han regenerado con draw.io Desktop desde las fuentes `.drawio`; el inventario detalla la validación XML y la inspección visual de las siete páginas.

## Tecnologías

- **Backend:** Java 25, Spring Boot 4.1.2, Maven, Spring Security, JWT HS256, JPA/Hibernate, Flyway, PostgreSQL 18, AWS SDK S3 y Spring Mail.
- **Frontend:** React Native/Expo SDK 57, Expo Router, React Native Web, TypeScript, Node.js 24 en Docker.
- **Infraestructura:** Neon PostgreSQL y Object Storage de desarrollo, Docker Compose, Mailpit local, PostgreSQL 18 Testcontainers y Redocly para OpenAPI.

## Instalación

```bash
git clone https://github.com/CeNtAuRoOo/urban-pulse-app.git
cd urban-pulse-app
```

Se necesitan Docker/Compose v2 y, para desarrollo sin contenedores, Node.js/npm y Java 25/Maven. Los ejemplos versionados `frontend/.env.example` y `backend/.env.example` indican las variables necesarias; los archivos con valores reales **no se versionan**.

En PowerShell:

```powershell
Copy-Item frontend/.env.example frontend/.env
Copy-Item backend/.env.example backend/.env.development
```

Rellenar únicamente con credenciales autorizadas de **Neon `development`**, nunca de `production`. El backend necesita secretos independientes para `JWT_SECRET`, `EMAIL_VERIFICATION_SECRET` y `STAFF_INVITATION_SECRET` (Base64 con al menos 32 bytes aleatorios). La configuración del almacenamiento S3 y SMTP también se inyecta por entorno.

## Ejecución en desarrollo

Frontend Expo Web en el host:

```bash
cd frontend
npm ci
npx expo start --web --port 7777
```

En Docker, desde la raíz:

```bash
docker compose up --build frontend
```

Para arrancar también el backend conectado a **Neon `development`** (solo después de comprobar URL, permisos y plan de migración Flyway):

```bash
docker compose up --build
```

Frontend web: <http://localhost:7777>; backend: <http://localhost:8888>; bandeja Mailpit local: <http://localhost:8025>. Arrancar el backend con Flyway habilitado puede aplicar V1 consolidada a la base configurada; **no** es un comando inocuo de conectividad. La guía completa y la distinción entre pruebas aisladas y recursos reales están en [Desarrollo](docs/development.md).

## API REST disponible

La fuente editable es el [OpenAPI modular 0.5.0](docs/api/openapi/base-openapi.yaml), con [bundle YAML](docs/api/openapi.yaml) y [HTML](docs/api/openapi.html) regenerados mediante Redocly. El prefijo de las rutas es `/api/v1`.

Autenticación implementa cinco POST:

- `/auth/register` y `/auth/login`.
- `/users/me/password-changes`.
- `/auth/email-verifications/confirm` y `/auth/email-verifications/resend`.

Usuarios implementa cinco operaciones con correo verificado: `GET/PATCH /users/me`, `GET /users` y `GET/PATCH /users/{userId}`. Las tres administrativas requieren ADMIN global. El perfil solo modifica `displayName`; la administración solo `active`, protegiendo al último ADMIN activo y verificado. Los DTO incluyen roles globales y pertenencias activas e históricas. [Reglas y límites](docs/adr/0012-gestion-usuarios-perfiles-y-administracion.md).

Invitaciones implementa otras cinco operaciones:

- `POST /staff-invitations`, `GET /staff-invitations` y `GET /staff-invitations/{invitationId}`.
- `POST /staff-invitations/{invitationId}/revocations`.
- `POST /users/me/staff-invitation-redemptions`.

Solo un `ADMIN` activo y verificado administra invitaciones; un `CITIZEN` activo y verificado las canjea con correo coincidente. La emisión crea **solo la invitación**, nunca un usuario; el código se entrega exclusivamente por correo después del commit y está protegido con HMAC. Los permisos globales son `OPERATOR`/`ADMIN` y los departamentales `TECHNICIAN`/`DEPARTMENT_COORDINATOR`. Ninguna respuesta administrativa contiene el código o su hash. Ver [ADR-0007](docs/adr/0007-invitaciones-personal.md) y [contrato API](docs/api/README.md).

Departamentos añade `GET /departments`, `GET /departments/{departmentId}`, `POST /departments` (201 con Location) y `PATCH /departments/{departmentId}`. Las consultas requieren correo verificado; las escrituras, además, ADMIN global vigente. Véase [contrato y límites](docs/api/README.md#51-departamentos-implementados-catálogos).

Categorías ofrece las mismas cuatro operaciones en `/categories`, con `CategoryDTO` sin código ni fechas; nombre único reservado incluso estando inactiva. [Contrato HTTP](docs/api/README.md#contrato-de-categorías).

Los demás recursos del contrato son **diseño**, no endpoints ya desplegados.

## Pruebas y validación

Frontend desde `frontend/`:

```bash
npx expo install --check
npx expo-doctor
npx tsc --noEmit
npm run lint
npx expo export --platform web
```

Backend desde `backend/`, con Java 25/Maven/Docker:

```bash
mvn -B -ntp clean verify -Ppostgres-it
```

Las suites cubren Autenticación, correo, Invitaciones, Departamentos y Categorías sobre PostgreSQL 18 efímero. [Desarrollo](docs/development.md#verificación-del-backend) documenta los resultados, comandos y límites. Las pruebas aisladas no acreditan un despliegue en Neon.

## Configuración, despliegue y límites

La API usa JWT de acceso de 7200 s y roles vigentes consultados en PostgreSQL por petición; el cambio de contraseña revoca tokens previos mediante `tokenVersion`. La verificación de correo bloquea rutas de negocio hasta confirmar el código. Los limitadores de tasa son locales por instancia; su distribución entre réplicas queda pendiente. Las respuestas de errores utilizan `ProblemDetail`.

El SMTP local con Mailpit no acredita entrega en Internet; faltan bootstrap del primer ADMIN, API de retirada de permisos, equipos, outbox/reintentos, rotación coordinada de secretos, política de retención de invitaciones/auditoría, HTTPS/CI/CD y despliegue productivo. El C4 Deployment definitivo también está pendiente. Ver [Despliegue](docs/deployment.md) y [Seguridad](SECURITY.md).

## Documentación y contribución

- [Índice de documentación](docs/README.md), [arquitectura](docs/architecture.md) y [modelo de dominio](docs/domain-model.md).
- [Diseño de autenticación](docs/auth-design.md), [ADR-0007: Invitaciones](docs/adr/0007-invitaciones-personal.md) y [API](docs/api/README.md).
- [Diagramas](docs/diagrams/README.md), [ADR](docs/adr/README.md), [CHANGELOG](CHANGELOG.md) y [CONTRIBUTING](CONTRIBUTING.md).

Para modificar un tag, mantener coherentes OpenAPI, dominio, diagramas, migraciones, pruebas y ADR. Emplear ramas de trabajo y Conventional Commits, sin compartir secretos ni modificar experimentalmente Neon `production`.

## Configuración

**API de autenticación disponible en el código:** `POST /api/v1/auth/register` (`201`), `POST /api/v1/auth/login` (`200`), `POST /api/v1/users/me/password-changes` (`204`), `POST /api/v1/auth/email-verifications/confirm` (`204`) y `POST /api/v1/auth/email-verifications/resend` (`202`/`204`). JWT HS256 de 7200 segundos: si el correo no está verificado, solo se permiten operaciones de autenticación limitadas. La confirmación activa el acceso con el JWT existente. Cambio de contraseña revoca JWT previos; fallos de PostgreSQL responden `503`. Ver [diseño e implementación](docs/auth-design.md).

- `frontend/.env`: `EXPO_PUBLIC_API_URL=http://localhost:8888` para desarrollo web local. Las variables `EXPO_PUBLIC_*` **son públicas** dentro del cliente; nunca incluir secretos.
- `backend/.env.development` (privado): JDBC (`SPRING_DATASOURCE_*`), acceso S3 (`AWS_ENDPOINT_URL_S3`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`), `JWT_SECRET` (Base64 de al menos 32 bytes aleatorios), `JWT_ISSUER`, `CORS_ALLOWED_ORIGINS`, `EMAIL_VERIFICATION_SECRET` y variables SMTP `MAIL_*` (sin secretos reales en Git). Los límites comunes se declaran en `application.properties`. Para producción, inyectar secretos y URLs de `production` mediante el proveedor de despliegue.
- `backend/src/main/resources/application.properties`: puerto 8888, JDBC, HikariCP, JPA, Flyway, `storage.s3.*`, JWT, SMTP, limpieza programada y políticas de rate limiting. `StorageProperties` configura el cliente S3. Flyway crea tablas mediante migraciones y `ddl-auto=validate` solo verifica el mapeo.
- `.editorconfig`, `.prettierrc.json` y `.vscode/`: convenciones del proyecto.

**API de evidencias disponible en el código:** `POST /api/v1/incidents/{incidentId}/attachments` (`201` + `Location`), `GET /api/v1/incidents/{incidentId}/attachments` (`200`), `GET /api/v1/incidents/{incidentId}/attachments/{attachmentId}` (`200`), `GET /api/v1/incidents/{incidentId}/attachments/{attachmentId}/content` (`200` con `image/jpeg|png|webp`) y `DELETE /api/v1/incidents/{incidentId}/attachments/{attachmentId}` (`204`, soft delete). Solo se admiten JPEG, PNG y WebP verificados por **firma binaria** (ADR-0004); el binario se sanitiza y se guarda en el bucket privado de Neon Object Storage, mientras los metadatos viven en PostgreSQL. Fallo de persistencia → compensación borrando el objeto del bucket. El binario se sirve siempre a través del backend; nunca se exponen claves internas ni URLs públicas. `DELETE` está declarado para `OPERATOR`/`TECHNICIAN`/`ADMIN` en el contrato (`x-roles`), pero la restricción debe aplicarse en la capa web/seguridad. Pendientes: tamaño máximo de subida, pertenencia `incidentId` ↔ `attachmentId`, tests y documento `docs/attachments-design.md`. Ver [contrato de evidencias](docs/api/README.md).

**Nota de redes:** `localhost` en un teléfono no apunta al ordenador; hay que utilizar la IP accesible del host o una API desplegada. Para web, el navegador no resuelve automáticamente el nombre Docker `backend`.

## Despliegue

**No hay un despliegue de producción validado.** La existencia del código de autenticación no demuestra el arranque con la nueva migración sobre Neon `development`, ni su despliegue en producción. La estrategia acordada es desplegar la web SPA y la API REST en contenedores **independientes**, mientras Android/iOS se distribuyen como aplicaciones nativas. `compose.yaml` es exclusivamente de desarrollo.

El `frontend/Dockerfile` prepara una compilación web con Expo y Nginx; todavía debe probarse como imagen de producción y recibir `EXPO_PUBLIC_API_URL` al construir. El backend dispone de un Dockerfile multietapa; el arranque previo **en desarrollo** y la conectividad con Neon están comprobados, pero **la puesta en marcha de la nueva V1 en Neon `development` y la imagen/despliegue de producción están pendientes de comprobación**. El proveedor de hosting de ambos contenedores sigue sin decidirse. Más detalles: [docs/deployment.md](docs/deployment.md).

## Contribución

Consulta [CONTRIBUTING.md](CONTRIBUTING.md) para ramas, commits, PR y revisiones. La documentación de arquitectura, desarrollo y ADR se encuentra en [docs/](docs/README.md).

## Licencia

La licencia de distribución está pendiente de decisión; no inferir una licencia solo por el carácter público del repositorio.
