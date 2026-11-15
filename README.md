# UrbanPulse

**UrbanPulse** es una plataforma cloud en desarrollo para gestionar incidencias urbanas: registro de avisos con ubicación y fotografías, validación, prioridad, asignación municipal y enriquecimiento posterior con contexto de la ciudad. El proyecto sigue un enfoque incremental de **diseño dominio–OpenAPI y desarrollo por tags**, documentando decisiones mediante ADR. El núcleo de identidad, los perfiles y administración de usuarios, las invitaciones, la administración de departamentos y categorías, y la detección y gestión de duplicados de incidencias ya tienen implementación backend; Incidencias incorpora seis operaciones de registro, consulta, corrección, historial y la vinculación de duplicados y Evidencias cinco rutas privadas JPEG/PNG; la tramitación operativa y las integraciones siguen en diseño.

## Estado del proyecto (14/11/2026)

| Área                 | Estado                                                                                                                                                                                             |
| -------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Frontend web         | Panel Expo SDK 57 conectado: autenticación, perfil, usuarios, invitaciones, catálogos, incidencias y vinculación de duplicados; UI de evidencias y validación móvil pendientes.                                               |
| Frontend Android/iOS | Código compartido; prueba funcional completa en dispositivo pendiente.                                                                                                                             |
| Backend              | Java 25/Spring Boot 4.1.2; **monolito modular** con `auth`, `identity`, `invitations`, `organization`, `categories`, `incidents`, `attachment`, `audit` y `shared`.                                |
| Autenticación        | Registro, login, cambio de contraseña, confirmación y reenvío de verificación de correo implementados.                                                                                             |
| Usuarios             | Perfil propio y administración ADMIN, listado paginado y pertenencias activas/históricas; protección del último administrador.                                                                     |
| Invitaciones         | Emisión/listado/consulta/revocación por ADMIN y canje por usuario verificado implementados; sin registro especial para personal.                                                                   |
| Departamentos        | Listado/detalle, creación y PATCH; escritura por ADMIN verificado y desactivación sin borrado.                                                                                                     |
| Categorías           | Listado/detalle, alta y PATCH; nombre Unicode único, desactivación/reactivación sin borrado.                                                                                                       |
| PostgreSQL           | V1 + V2 consolidadas, 15 tablas de dominio; evidencia aislada en la guía de desarrollo. Conectividad Neon `development` comprobada previamente; **no se acredita aplicación de V1+V2 consolidada en Neon**. |
| Object Storage       | Conectividad S3/`HeadBucket` de Neon verificada anteriormente; fotografías privadas JPEG/PNG implementadas con cinco rutas, 10 MiB y máximo cinco activas por incidencia.                          |
| Incidencias          | Seis endpoints backend con auditoría, titularidad y concurrencia optimista; vinculación y desvinculación de duplicados con prevención de ciclos y simetría; Tramitación, equipos y analítica siguen en diseño.                                                                     |
| CI / despliegue      | GitHub Actions verifica backend, frontend, OpenAPI y Compose. CD y despliegue productivo pendientes.                                                                                               |

Los [diagramas C4, ER y clases](docs/diagrams/README.md) incluyen los módulos implementados y el diseño futuro identificado como tal. El inventario distingue las fuentes de dominio y las exportaciones gráficas históricas; el [roadmap de Incidencias](docs/incidents-implementation-roadmap.md) recoge su alcance implementado y futuro.

## Tecnologías

- **Backend:** Java 25, Spring Boot 4.1.2, Maven, Spring Security, JWT HS256, JPA/Hibernate, Flyway, PostgreSQL 18, AWS SDK S3 y Spring Mail.
- **Frontend:** React Native/Expo SDK 57, Expo Router, React Native Web, TypeScript, Node.js 24 en Docker.
- **Infraestructura:** PostgreSQL 18 local y Mailpit en Compose, Neon PostgreSQL productivo y Neon Object Storage en ambos entornos, PostgreSQL Testcontainers, GitHub Actions y Redocly.

## Instalación

```bash
git clone https://github.com/CeNtAuRoOo/urban-pulse-app.git
cd urban-pulse-app
```

Se necesitan Docker Engine/Desktop y Docker Compose. La aplicación de desarrollo se ejecuta siempre en contenedores; los ejemplos versionados `backend/.env.example` y `frontend/.env.example` describen la configuración privada necesaria.

En PowerShell, solo si los destinos no existen (no sobrescribir archivos privados):

```powershell
Copy-Item frontend/.env.example frontend/.env
Copy-Item backend/.env.example backend/.env.development
```

El desarrollo utiliza exclusivamente **PostgreSQL local**: configurar el JDBC del backend a `postgres:5432` en `backend/.env.development`, junto con tres secretos JWT/HMAC independientes, Mailpit y Neon Object Storage de desarrollo. `frontend/.env` contiene `EXPO_PUBLIC_API_URL=http://localhost:8080`; CORS autoriza `http://localhost:8888`. Compose carga esos valores sin sobrescribirlos. Neon PostgreSQL y los secretos productivos pertenecen al despliegue independiente. [Configuración completa](docs/development.md#1-configuración-privada).

## Ejecución en desarrollo

Desde la raíz, después de completar los archivos privados:

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs -f backend
docker compose down
```

Frontend web: <http://localhost:8888>; backend: <http://localhost:8080>; Mailpit: <http://localhost:9999>. Todos los puertos publicados se vinculan a `127.0.0.1`. El backend usa `postgres:5432` y `mailpit:1025` dentro de Docker. Flyway aplica las migraciones pendientes y Hibernate valida el esquema.

`down` conserva los datos; cambiar de rama también conserva el historial del volumen local. Cada integrante usa su propia base Docker, pero debe coordinar las versiones Flyway antes de integrar ramas. [Desarrollo](docs/development.md) detalla persistencia, correo y pruebas.

## API REST disponible

La fuente editable es el [OpenAPI modular 0.7.0](docs/api/openapi/base-openapi.yaml), con [bundle YAML](docs/api/openapi.yaml) y [HTML](docs/api/openapi.html) regenerados mediante Redocly. El prefijo de las rutas es `/api/v1`.

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

Incidencias implementa `POST/GET /incidents`, `GET /incidents/mine`, `GET/PATCH /incidents/{incidentId}` y `GET /incidents/{incidentId}/history`. PATCH exige If-Match y solo admite correcciones en REPORTED; detalle e historial son privados al titular o personal global autorizado. [Contrato y evolución](docs/incidents-implementation-roadmap.md).

Los demás recursos del contrato son **diseño**, no endpoints ejecutables.

## Pruebas y validación

El [workflow CI](.github/workflows/ci.yml) se ejecuta en PR hacia `main`, push a `main` y manualmente. Las ramas de desarrollo se validan por PR/manual para evitar ejecuciones duplicadas. Jobs independientes, permisos de lectura, cachés y PostgreSQL efímero sin secretos Neon; no despliega.

Comandos del job frontend desde `frontend/` (Node.js 24); en el entorno Docker se pueden ejecutar mediante `docker compose exec frontend <comando>`:

```bash
npm ci
npm test
npx tsc --noEmit
npm run lint
npx expo export --platform web
```

Como mantenimiento opcional, `npx expo install --check` consulta las recomendaciones actuales de Expo; puede pedir parches nuevos y no forma parte de los checks obligatorios de CI.

Comando del job backend desde `backend/`, con Java 25/Maven/Docker; la [guía](docs/development.md#backend-unitariasmvc-e-integración-postgresql) incluye su ejecución en un contenedor de pruebas:

```bash
mvn -B -ntp clean verify -Ppostgres-it
```

Las suites cubren Autenticación, correo, Invitaciones, Usuarios, pertenencias, Departamentos, Categorías, Incidencias y Evidencias sobre PostgreSQL 18 efímero. [Desarrollo](docs/development.md#verificación-del-backend) documenta los resultados, comandos y límites. Las pruebas aisladas no acreditan un despliegue en Neon.

## Configuración, despliegue y límites

La API usa JWT de acceso de 7200 s y roles vigentes consultados en PostgreSQL por petición; el cambio de contraseña revoca tokens previos mediante `tokenVersion`. La verificación de correo bloquea rutas de negocio hasta confirmar el código. Los limitadores de tasa son locales por instancia; su distribución entre réplicas queda pendiente. Las respuestas de errores utilizan `ProblemDetail`.

El SMTP local con Mailpit no acredita entrega en Internet; faltan bootstrap del primer ADMIN, API de retirada de permisos, equipos, outbox/reintentos, rotación coordinada de secretos, política de retención de invitaciones/auditoría, HTTPS/CD y despliegue productivo. El C4 Deployment definitivo también está pendiente. Ver [Despliegue](docs/deployment.md) y [Seguridad](SECURITY.md).

## Documentación y contribución

- [Índice de documentación](docs/README.md), [arquitectura](docs/architecture.md) y [modelo de dominio](docs/domain-model.md).
- [Diseño de autenticación](docs/auth-design.md), [ADR-0007: Invitaciones](docs/adr/0007-invitaciones-personal.md) y [API](docs/api/README.md).
- [Diagramas](docs/diagrams/README.md), [ADR](docs/adr/README.md), [CHANGELOG](CHANGELOG.md) y [CONTRIBUTING](CONTRIBUTING.md).

Para modificar un tag, mantener coherentes OpenAPI, dominio, diagramas, migraciones, pruebas y ADR. Emplear ramas de trabajo y Conventional Commits, sin compartir secretos ni modificar experimentalmente Neon `production`.

## Licencia

La licencia de distribución está pendiente de decisión; no inferir una licencia solo por el carácter público del repositorio.
