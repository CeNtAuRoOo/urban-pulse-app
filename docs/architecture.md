# Arquitectura de UrbanPulse

> **Estado:** arquitectura objetivo inicial (11/10/2026). No implica que los servicios estén implementados o desplegados. Las decisiones vigentes se registran en [ADR](adr/README.md).

## 1. Propósito y alcance

UrbanPulse es una plataforma cloud para gestionar incidencias urbanas reportadas por la ciudadanía. Cada incidencia incluye descripción, localización, estado e historial; opcionalmente se asocia a fotografías, activos urbanos y datos contextuales. El sistema permite registrar, validar, priorizar, asignar, resolver y analizar incidencias.

La aplicación tiene dos presentaciones: **web y móvil**. Ambas ofrecen las mismas funcionalidades según los permisos del usuario; la preferencia de uso del ciudadano por móvil y del personal por web **no es una restricción de roles por plataforma**.

## 2. Impulsores arquitectónicos (architecture drivers)

- **Funcionales:** registro y seguimiento de incidencias; control de roles; evidencias; geolocalización; gestión municipal; auditoría; análisis y posibles integraciones externas.
- **Calidad:** seguridad, trazabilidad, mantenibilidad, compatibilidad web/móvil, integridad de datos, posibilidad de evolución a cloud y costes limitados.
- **Restricciones:** Spring Boot en backend, React Native/Expo en frontend, PostgreSQL administrado, herramientas gratuitas en lo posible y equipos pequeños.
- **Evolución:** comenzar con un monolito modular sencillo; añadir caché, colas o microservicios solo ante una necesidad medida y documentada.

## 3. Visión general

El sistema está formado por:

1. **Frontend universal:** una base de código React Native + Expo + React Native Web, compilada como aplicación web o móvil.
2. **Backend API:** aplicación Spring Boot que expone HTTPS/JSON, valida JWT, ejecuta reglas de negocio y autoriza operaciones.
3. **Base de datos principal:** PostgreSQL administrado por Neon, accesible solamente por backend.
4. **Almacén de evidencias:** Neon Object Storage propuesto, compatible con S3 y configurado inicialmente como privado.
5. **Sistemas externos:** geocodificación, cartografía y fuentes urbanas; servicio de notificaciones pendiente de elección.

Las credenciales de PostgreSQL y Object Storage solo deben existir en entornos de ejecución autorizados; no se entregan al cliente web ni móvil.

## 4. Modelo C4

### 4.1. Nivel 1: contexto del sistema

![C4 - Contexto](diagrams/c4-context.svg)

### 4.2. Nivel 2: contenedores

![C4 - Contenedores](diagrams/c4-containers.svg)

Los contenedores C4 **no son sinónimos de contenedores Docker**. Web y móvil son unidades ejecutables distintas aunque compartan un repositorio de código. PostgreSQL y evidencias pueden permanecer dentro del límite **lógico** del sistema aunque su infraestructura sea administrada por Neon.

### 4.3. Diagramas adicionales

- **C4 Deployment:** pendiente hasta seleccionar hosting de frontend web y backend. No inventar nodos ni redes cloud.
- **C4 Dynamic o UML de secuencia:** recomendable al modelar el flujo de creación de incidencias con evidencias y enriquecimiento externo.
- **ER/base de datos:** en `docs/diagrams/er-database.drawio`, al estabilizar el modelo relacional.
- **UML de clases:** documentación de diseño detallado; opcional en `docs/diagrams/` o en un futuro directorio `docs/design/`.
- **Máquina de estados:** recomendable para las transiciones de `Incident` y su auditoría.

## 5. Arquitectura interna objetivo del backend

Se propone comenzar con un **monolito modular**, organizado por responsabilidades o dominios. La organización concreta de paquetes Java aún no está creada.

Flujo conceptual habitual: `Controller -> Service -> Repository -> PostgreSQL`. Las integraciones externas se aislarán tras interfaces/adaptadores, evitando dependencias directas desde los controladores. Los DTO evitarán exponer las entidades JPA.

La API será el punto de entrada de web y móvil. Mantendrá validación de entrada y de autorización, y utilizará códigos de estado HTTP y errores estructurados.

## 6. Modelo de dominio y ciclo de vida

La incidencia (`Incident`) es la entidad central. El modelo inicial considera `User`, `UrbanAsset`, `UrbanContext`, `Attachment`, `Assignment`, `StatusChange`, `ExternalObservation`, `Notification` y `KnowledgeDocument`. Estas entidades proceden del documento de alcance y podrán ajustarse durante el diseño.

Estados previstos para incidencia: `REPORTED`, `VALIDATED`, `REJECTED`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `REOPENED` y `CLOSED`. Las transiciones y acciones deberán quedar auditadas. No todas las combinaciones serán transiciones válidas.

La base de datos conserva datos estructurados y metadatos de fotografías; Object Storage contiene el binario de la evidencia. La coordinación entre ambos almacenes requerirá controlar fallos y objetos huérfanos.

## 7. Autenticación y autorización

- Registro ordinario de ciudadanos con correo y contraseña.
- Acceso del personal municipal mediante invitaciones administradas (**ADR-0007 propuesto**).
- JWT firmados, validados por Spring Security en la API.
- Autorización por rol y, cuando corresponda, por relación con el recurso o su estado.
- Las credenciales de servicio con Neon no representan identidades de usuarios finales.
- Contraseñas almacenadas con algoritmos de derivación adecuados; nunca en texto plano.

Pendiente: duraciones y renovación/revocación de tokens, claves, CORS, recuperación de contraseñas y reglas detalladas de roles.

## 8. Evidencias y privacidad

Se proponen solo imágenes en una primera fase. El bucket estará privado; el backend autorizará su asociación y consulta. No se considerará pública una imagen únicamente porque los metadatos de la incidencia sean visibles.

Se deberá validar tipo real de archivo y tamaño en backend (además de la validación UX en frontend), limitar permisos, evitar exponer credenciales, tratar metadatos EXIF y establecer cómo revisar o anonimizar fotografías con información personal. Las URLs firmadas o subidas directas se definirán en implementación.

## 9. Persistencia y migraciones

- PostgreSQL administrado por Neon.
- Spring Data JPA/Hibernate para la persistencia habitual; SQL directo si un caso lo justifica.
- Migraciones de esquema con Flyway en `backend/src/main/resources/db/migration/` (aún no creado).
- `spring.jpa.hibernate.ddl-auto=validate` **cuando exista el esquema y la conexión**; Hibernate no creará automáticamente tablas.
- Credenciales mediante variables de entorno. Permisos mínimos para la cuenta de aplicación.

## 10. Integraciones y resiliencia

Las fuentes de geolocalización, información urbana y notificaciones se consideran dependencias externas. **La falta de una fuente contextual no debe impedir registrar una incidencia**, aunque la operación de enriquecimiento quede pendiente. Se contemplarán límites de espera, manejo de errores y trazabilidad de resultados.

La tecnología concreta de mapas, las fuentes geográficas y el proveedor de notificaciones aún no se han aprobado. No se incorpora Redis, mensajería ni microservicios en la estructura inicial.

## 11. Entornos, observabilidad y pruebas

- **Desarrollo:** `compose.yaml` orquesta dos servicios, web/Expo y Spring Boot; Neon permanece remoto.
- **Producción (objetivo):** backend y frontend web independientes; las apps Android/iOS se distribuyen como binarios nativos.
- **Observabilidad:** logs por salida estándar, códigos de error claros, sin exposición de secretos; métricas/health checks, cuando se implemente la plataforma.
- **Pruebas:** unitarias, integración, seguridad, contrato API y flujo de registro de incidencias; GitHub Actions se añadirá cuando exista código ejecutable.

## 12. Decisiones y evolución

Consultar [ADR](adr/README.md) para justificaciones. No se ha adoptado la proyección dinámica por `fields`: deberá evaluarse por complejidad de contrato, coste de consultas y control por campo antes de documentarse como ADR.

## 13. Referencias internas

- [Desarrollo](development.md)
- [Despliegue](deployment.md)
- [Documentación de API](api/README.md)
- [Diagramas](diagrams/README.md)
