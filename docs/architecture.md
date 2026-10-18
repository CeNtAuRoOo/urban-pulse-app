# Arquitectura de UrbanPulse

> **Estado técnico comprobado (15/10/2026):** frontend web y arranque conjunto frontend/backend con Docker Compose; Neon PostgreSQL y Neon Object Storage accesibles desde Spring Boot en `development`. **Sin endpoints de negocio, entidades JPA, migraciones SQL del dominio ni flujo funcional de fotografías.** El **modelo de dominio v1** documentado el 18/10/2026 es un **borrador compartido**, no un diseño íntegramente validado ni código implementado. Véase [modelo de dominio](domain-model.md).

## 1. Alcance y principios

UrbanPulse permitirá reportar incidencias urbanas con ubicación, estado, fotografías e historial; validarlas, priorizarlas y distribuirlas a departamentos y personal municipal; y, progresivamente, enriquecerse con activos y observaciones de fuentes externas. La funcionalidad de negocio sigue pendiente de implementación.

Las aplicaciones web y móvil compartirán una base de código (Expo / React Native / React Native Web) y **las mismas autorizaciones según rol**, sin depender de si acceden por web o móvil. La aplicación nativa se probará en una fase posterior.

**Impulsores:** integridad/trazabilidad, privacidad, control de acceso, mantenibilidad, compatibilidad web/móvil, evolución cloud y minimización de costes universitarios. Partimos de un **monolito por capas**: no se añaden microservicios, colas ni cachés sin necesidad comprobada. El documento del proyecto mantiene algunas funciones avanzadas de IA, analítica predictiva y RAG **fuera del alcance de esta fase**; disponer de tags OpenAPI correspondientes no las convierte en trabajo ya aprobado.

## 2. Contenedores lógicos y tecnologías

| Componente               | Tecnología                                                     | Estado                                                                             |
| ------------------------ | -------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| Aplicación web           | Expo SDK 57, React Native Web, TypeScript, Expo Router         | Pantalla inicial y desarrollo web verificados                                      |
| Aplicaciones Android/iOS | Expo / React Native                                            | Código compartido, sin prueba funcional completa en dispositivo                    |
| API REST                 | Java 25 / Spring Boot 4.1.1 / Maven / Spring Security / JPA    | Proyecto y configuración existentes; sin endpoints de negocio                      |
| Base principal           | Neon PostgreSQL 18                                             | JDBC verificado en `development`; esquema de dominio pendiente                     |
| Fotografías              | Neon Object Storage, API compatible con S3                     | Bucket privado y conectividad `HeadBucket` comprobados; subida/descarga pendientes |
| Proveedores externos     | Mapas, geocodificación, inventario de activos y notificaciones | Selección e integración pendientes                                                 |

El cliente solo consume la API REST: no conoce ni utiliza credenciales de PostgreSQL o Object Storage. Las claves privadas se mantienen fuera del código y del frontend.

## 3. Arquitectura C4 y modelo de dominio

### 3.1. C4 nivel 1: contexto

![C4 - Contexto](diagrams/c4-context.svg)

### 3.2. C4 nivel 2: contenedores

![C4 - Contenedores](diagrams/c4-containers.svg)

Ambos diagramas se conservan como SVG y **también se incorpora** su fuente editable independiente [`diagrams/c4.drawio`](diagrams/c4.drawio). Un contenedor C4 representa una unidad lógica ejecutable/almacén, no necesariamente un Docker container. Los sistemas administrados de Neon pueden aparecer dentro del límite lógico del sistema.

**Pendiente:** `c4-containers.svg` todavía menciona vídeos/documentos; la etapa inicial solo considera **fotografías** (ADR-0004, aceptado). No se redibuja en este PR.

### 3.3. Modelo de dominio lógico (versión base v1)

- **[Relacional multipágina](diagrams/relacional.drawio):** tablas previstas, campos principales, PK, FK y cardinalidades, en cuatro páginas (incidencias; tramitación; identidad/organización; territorio/contexto).
- **[Catálogo de clases simplificado](diagrams/clases-simplificado.drawio):** clases Java previstas con campos/tipos, enumeraciones, objetos de valor `@Embeddable` y DTO; **una página sin relaciones intencionadamente**, puesto que estas se estudian en el ER.
- Las imágenes SVG exportadas están indexadas en [diagramas](diagrams/README.md).

**Estado: primera propuesta completa de representación, no validación completa del dominio.** Una decisión de que una clase aparezca en el dibujo **no** obliga a implementarla ahora ni significa que su persistencia/campos/DTO ya estén aprobados. El equipo revisará el modelo **por tag** junto con el OpenAPI modular, empezando por los casos de uso que efectivamente construya cada compañero; cada PR ajustará diagramas, documentación, contrato, pruebas y migraciones pertinentes. Detalles en [modelo de dominio y proceso de revisión](domain-model.md).

### 3.4. Diagramas todavía pendientes

- C4 Deployment: definir proveedor real, HTTPS, dominio y redes.
- Secuencia/dinámico de subida autorizada de fotos y registro de incidencias.
- Máquina de estados explícita (matriz de transiciones); el enum ya aparece en el diagrama, pero **no** se han validado todas las transiciones.

## 4. Frontend universal: estructura y estado

`frontend/` incluye Expo Router con `src/app/_layout.tsx`, `src/app/index.tsx`, `src/components`, `src/features`, `src/services` y `src/types`, además de configuración de Expo, TypeScript y ESLint. Se utiliza **Metro**, no Vite. `web.output="single"` genera una SPA exportable y se ha verificado `npx expo export --platform web`; el Dockerfile de producción utiliza Nginx, pero **la imagen final de producción no se ha validado**.

El frontend aún no dispone de formularios de incidencias, cliente API funcional, inicio de sesión ni reglas de permisos visuales. Esas reglas no sustituirán a la autorización del backend.

## 5. Backend: estructura existente

Clase principal: `es.grupo18.urbanpulse.BackendApplication`. Subpaquetes `config`, `controller`, `dto`, `entity`, `repository` y `service`. En `config` existen `StorageConfig` y `StorageProperties`; `controller`, `dto`, `entity`, `repository` y `service` **siguen vacíos para negocio**.

Organización objetivo: `Controller → Service → Repository → PostgreSQL`, con DTO de intercambio independientes de entidades JPA. El backend escucha en el puerto **8888** (configurable), utiliza HikariCP, `spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=validate`, Flyway y configuración externa de servicios. El SDK S3 configura el acceso al bucket privado.

**Persistencia:** Flyway gestionará migraciones versionadas dentro de `backend/src/main/resources/db/migration/`; el script inicial del dominio **todavía no existe**. `ddl-auto=validate` no crea tablas. No ejecutar migraciones experimentales sobre la rama `production` de Neon. Los futuros tests de persistencia deben aislarse de la base compartida, mientras `NeonConnectivityIT` seguirá como prueba manual de humo.

**Contrato REST:** existe un borrador OpenAPI 3.0.3 **modular** en [`api/openapi/base-openapi.yaml`](api/openapi/base-openapi.yaml), con operaciones por tag y esquemas reutilizables. [`api/openapi.yaml`](api/openapi.yaml) es una copia monolítica; si se modifica el contrato habrá que **regenerarla** desde la fuente modular. El contrato no demuestra la existencia de endpoints.

## 6. Dominio operativo previsto y decisiones aceptadas

**Incidencia central (`Incident`):** informante, categoría opcional, coordenadas y datos geográficos derivados (opcionales), fotografías, historial de estados, prioridad justificada y departamento responsable opcional. La ausencia de datos externos **no bloquea** la creación.

**Roles y ámbito (ADR-0009):** `User` común con catálogo `Role`; roles globales `CITIZEN`, `OPERATOR`, `ADMIN` mediante `UserRole`, y roles departamentales `DEPARTMENT_COORDINATOR`, `TECHNICIAN` mediante `DepartmentMembership`. `OPERATOR` es municipal/transversal y **no necesita departamento**; coordinadores y técnicos solo operan sobre departamentos autorizados. Equipos `Team` reutilizables y desactivables conservan sus `TeamMembership` históricos.

**Tramitación (ADR-0010):** `IncidentStatus` propone `REPORTED`, `VALIDATED`, `REJECTED`, `ASSIGNED_TO_DEPARTMENT`, `WORK_ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `REOPENED`, `CLOSED`. Un operador puede dejar una incidencia `VALIDATED` sin asignarle todavía departamento. La derivación se refleja en `Incident.assignedDepartment` y `StatusChange`; **`Assignment` solo aparece al distribuir trabajo a un equipo o técnico**, con historial de reasignaciones y como máximo una activa. `StatusChange` audita los cambios de estado; `AuditEvent` otros cambios importantes (por ejemplo una transferencia departamental). La **matriz completa de transiciones y los permisos por operación quedan pendientes**.

**Prioridades:** escala `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`; decisiones `PriorityDecision` auditables. **Duplicados:** el sistema ofrece `DuplicateSuggestionDTO` calculadas, nunca decisiones automáticas; solo un operador autorizado confirma `DuplicateLink` conservando ambos reportes. La detección semántica con IA queda para una fase futura.

**Identidad/seguridad:** autenticación por credenciales/JWT de Spring Security (ADR-0005) aún sin implementar. Invitaciones controladas de personal **aprobadas como mecanismo** (ADR-0007); un usuario con cuenta existente podrá incorporar un nuevo rol sin crear una segunda cuenta, tras verificar identidad/autorización. `UserRole` y `DepartmentMembership` diferencian roles globales de departamentales; se aplicarán permisos por rol, departamento, titularidad y estado.

**Identificadores:** UUIDv7 como convención de diseño de PK (`Role.code` como clave natural estable), pendiente de validar generador JPA/PostgreSQL (ADR-0011).

**Contexto urbano:** `UrbanAsset`, `UrbanAssetAssociation`, `ExternalSource` y `ExternalObservation` son un **esqueleto provisional** a completar cuando se investiguen datos y proveedores. `UrbanContextViewDTO` no necesita tabla propia; no se eligen integraciones ni PostGIS de forma anticipada.

## 7. Seguridad, almacenamiento y resiliencia

- Las decisiones de roles/autorización deben aplicarse **en Spring Security y los servicios**, no exclusivamente en el frontend ni mediante la extensión documental `x-roles` de OpenAPI.
- JWT: definir emisión, vencimiento, renovación/revocación y almacenamiento seguro; no se han aprobado todos sus parámetros.
- Evidencias: bucket S3 privado `incident-evidence`; metadatos en PostgreSQL, binarios fuera. Autorizar acceso incluso si los datos de la incidencia son visibles; decidir MIME/tamaño, EXIF, retención, limpieza de huérfanos y tratamiento de fallos entre SQL y objeto.
- Información contextual: un proveedor externo puede fallar sin impedir la creación de una incidencia (degradación controlada). No se deduce disponibilidad de proveedores de que aparezcan en un diagrama.
- Secretos fuera de Git; `EXPO_PUBLIC_*` solo para valores públicos del cliente; revisar CORS/HTTPS antes del despliegue.

## 8. Verificaciones técnicas comprobadas y próximas etapas

- Frontend web: `npx expo install --check`, `npx expo-doctor`, `npx tsc --noEmit`, `npm run lint`, exportación web y Compose de desarrollo comprobados el 13/10/2026.
- Docker Compose conjunto: frontend + backend ejecutados con éxito el 15/10/2026.
- Backend + Neon `development`: prueba explícita `docker compose run --rm backend mvn -B -Dtest=NeonConnectivityIT test` con JDBC y `HeadBucket` superados el 15/10/2026 (no verifica subida/descarga ni ID de rama por sí misma).
- **Pendiente:** pruebas funcionales/contrato/seguridad, entidades y migraciones, Android/iOS en dispositivo, imagen Nginx de producción, CI/CD y despliegue real.

El próximo trabajo **no** será implementar todas las entidades: cada PR funcional comienza revisando el [tag de API](api/README.md) y las [dos vistas del dominio](domain-model.md), pacta las decisiones concretas, implementa su parte, la prueba y documenta el resultado. La rama `main` debe conservar el estado realmente verificado.

## 9. Referencias

- [Modelo de dominio y proceso por tag](domain-model.md)
- [Diagramas](diagrams/README.md)
- [ADR y decisiones](adr/README.md)
- [Diseño de API](api/README.md)
- [Guía de desarrollo](development.md)
- [Despliegue](deployment.md)
