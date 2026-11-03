# UrbanPulse

**UrbanPulse** es una plataforma cloud en desarrollo para gestionar incidencias urbanas: registro de avisos con ubicación y fotografías, validación, prioridad, asignación municipal y enriquecimiento posterior con contexto de la ciudad. El proyecto sigue un enfoque incremental de **diseño dominio–OpenAPI y desarrollo por tags**, documentando decisiones mediante ADR. El núcleo de identidad, las invitaciones y la administración de departamentos ya tienen implementación backend; el resto de funcionalidades sigue en diseño.

## Estado del proyecto (02/11/2026)

| Área | Estado |
| --- | --- |
| Frontend web | Expo SDK 57/React Native Web con pantalla inicial, exportación web y desarrollo con Compose verificados. No hay integración real con los endpoints. |
| Frontend Android/iOS | Código compartido; prueba funcional completa en dispositivo pendiente. |
| Backend | Java 25/Spring Boot 4.1.2; **monolito modular** con `auth`, `identity`, `invitations`, `organization`, `audit` y `shared`. |
| Autenticación | Registro, login, cambio de contraseña, confirmación y reenvío de verificación de correo implementados. |
| Invitaciones | Emisión/listado/consulta/revocación por ADMIN y canje por usuario verificado implementados; sin registro especial para personal. |
| Departamentos | Listado/detalle, creación y PATCH; escritura por ADMIN verificado y desactivación sin borrado. |
| PostgreSQL | V1 consolidada para bases vacías; evidencia actual en la guía de desarrollo. Conectividad Neon `development` comprobada previamente; **no se acredita aplicación de V1 consolidada en Neon**. |
| Object Storage | Conectividad S3/`HeadBucket` de Neon verificada anteriormente; operaciones de fotografías pendientes. |
| Incidencias, tramitación, categorías, equipos, analítica | Contrato y modelo revisables, **sin endpoints funcionales implementados**. |
| Despliegue real/CI/CD | Pendientes. Compose de desarrollo y runner HTTP de pruebas no acreditan producción. |

Las fuentes de los [diagramas C4, ER y clases](docs/diagrams/README.md) se integran con el modelo definitivo. Los SVG conservan el contenido anterior de esta rama y quedan pendientes de exportación manual.

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

Invitaciones implementa otras cinco operaciones:

- `POST /staff-invitations`, `GET /staff-invitations` y `GET /staff-invitations/{invitationId}`.
- `POST /staff-invitations/{invitationId}/revocations`.
- `POST /users/me/staff-invitation-redemptions`.

Solo un `ADMIN` activo y verificado administra invitaciones; un `CITIZEN` activo y verificado las canjea con correo coincidente. La emisión crea **solo la invitación**, nunca un usuario; el código se entrega exclusivamente por correo después del commit y está protegido con HMAC. Los permisos globales son `OPERATOR`/`ADMIN` y los departamentales `TECHNICIAN`/`DEPARTMENT_COORDINATOR`. Ninguna respuesta administrativa contiene el código o su hash. Ver [ADR-0007](docs/adr/0007-invitaciones-personal.md) y [contrato API](docs/api/README.md).

Departamentos añade `GET /departments`, `GET /departments/{departmentId}`, `POST /departments` (201 con Location) y `PATCH /departments/{departmentId}`. Las consultas requieren correo verificado; las escrituras, además, ADMIN global vigente. Véase [contrato y límites](docs/departments-finalization.md).

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

**Evidencias históricas, anteriores a esta integración:** frontend/Compose/Neon se comprobaron el 13–15/10/2026. La regresión de Autenticación y verificación alcanzó 55 pruebas; la entrega de Invitaciones registró **`BUILD SUCCESS` con 90 pruebas superadas** (48 unitarias/MVC y 42 de integración con PostgreSQL 18), además de un recorrido HTTP aislado con Mailpit y resultado satisfactorio. La secuencia de commits es **refactor (`c393418`) → Invitaciones (`81d8d85`) → pruebas (`0bb0ca3`) → documentación (`56fc44d`)**: las pruebas forman parte de la implementación ya verificada, no de una tarea pendiente para este PR. Véase [Desarrollo](docs/development.md) para alcance y entorno.

El informe original también describe un runner HTTP con JAR, PostgreSQL en `tmpfs` y Mailpit (`127.0.0.1:18888` y `127.0.0.1:18025`). **Los archivos `compose.test.yaml`, `backend/Dockerfile.test` y `backend/scripts/invitations_http_test.py` no figuran en el estado local adjunto**; por tanto, el flujo no se presenta como reproducible desde este árbol. Sí puede ejecutarse la suite Maven de Testcontainers indicada arriba. `NeonConnectivityIT` es una prueba distinta, bajo demanda y solo con permisos para Neon `development`. Consulta [Desarrollo](docs/development.md) para evidencias y comandos disponibles.

La revisión de Departamentos de 30–31/10/2026 registró 80 pruebas (55 unitarias/MVC y 25 IT) en su rama. Los resultados actuales de la suite combinada se recogen en [Desarrollo](docs/development.md).

## Configuración, despliegue y límites

La API usa JWT de acceso de 7200 s y roles vigentes consultados en PostgreSQL por petición; el cambio de contraseña revoca tokens previos mediante `tokenVersion`. La verificación de correo bloquea rutas de negocio hasta confirmar el código. Los limitadores de tasa son locales por instancia; su distribución entre réplicas queda pendiente. Las respuestas de errores utilizan `ProblemDetail`.

El SMTP local con Mailpit no acredita entrega en Internet; faltan bootstrap del primer ADMIN, retirada de permisos, categorías/equipos, outbox/reintentos, rotación coordinada de secretos, política de retención de invitaciones/auditoría, HTTPS/CI/CD y despliegue productivo. El C4 Deployment definitivo también está pendiente, y la exportación SVG de los diagramas actuales se realizará manualmente. Ver [Despliegue](docs/deployment.md) y [Seguridad](SECURITY.md).

## Documentación y contribución

- [Índice de documentación](docs/README.md), [arquitectura](docs/architecture.md) y [modelo de dominio](docs/domain-model.md).
- [Diseño de autenticación](docs/auth-design.md), [ADR-0007: Invitaciones](docs/adr/0007-invitaciones-personal.md) y [API](docs/api/README.md).
- [Diagramas](docs/diagrams/README.md), [ADR](docs/adr/README.md), [CHANGELOG](CHANGELOG.md) y [CONTRIBUTING](CONTRIBUTING.md).

Para modificar un tag, mantener coherentes OpenAPI, dominio, diagramas, migraciones, pruebas y ADR. Emplear ramas de trabajo y Conventional Commits, sin compartir secretos ni modificar experimentalmente Neon `production`.

## Licencia

La licencia de distribución está pendiente de decisión; no inferir una licencia solo por el carácter público del repositorio.
