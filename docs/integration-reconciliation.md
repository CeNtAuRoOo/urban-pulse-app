# Integración de Duplicados en Mapas — 16–17/11/2026

## Referencias y decisión

Auditoría de tres estados distintos, antes de iniciar el merge:

| Estado | SHA | Aportación |
|---|---|---|
| A, ancestro común | `27357ea1dc86479d4322028a5f63c6ea66f22177` | Tramitación fase 1, Evidencias y regresiones de privacidad |
| B, main auditado | `b08ad60845ae3200b8b751e30ecc1228438a641e` | Duplicados; un commit posterior a A |
| C, rama de Mapas auditada | `50d7ed703f1575b5d1f6437f0cd6a7180060c44f` | Backend provincial de Mapas; cinco commits posteriores a A |

Se verificaron genealogía, referencias remotas y árbol inicialmente limpio. Después del plan se ejecutó `git merge --no-ff --no-commit b08ad60845ae3200b8b751e30ecc1228438a641e` en `feature/implementacion-mapas`. Las correcciones forman parte del único commit local de merge; no hay push, cambios de main ni actuaciones sobre las PR. La PR #25 estaba fusionada y la #26 abierta al auditarlas.

El responsable eligió **las mejores veinte sugerencias entre todos los candidatos válidos a ±72 horas y 250 metros, con timeout y error explícito**. Se conserva confirmación humana, autorización global OPERATOR/ADMIN y cuenta activa/verificada. No se incorporan desvinculación, fusión, prohibición general de ciclos ni análisis textual. Se mantiene `/api/v1` y `info.version: 0.8.0`: ambas implementaciones se incorporan ahora sobre ese contrato; no se atribuyen a la publicación histórica 0.7.0.

La consolidación V1 se basa en la premisa del responsable de que las migraciones no se han aplicado a bases de destino. No se ha consultado ni modificado Neon. Una base con otro historial o checksum necesita una transición revisada; esta V1 no es una actualización compatible de aquella base. No se ejecutaron repair, baseline ni outOfOrder.

## Diferencias verificadas entre A, B y C

| Estado | Migraciones ejecutables entonces | Tablas de dominio | Operaciones modular / bundle (implementadas, futuras) |
|---|---|---|---|
| A | V1 + V2 Tramitación | 15 | 44/33 en ambos |
| B | V1 + V2 Tramitación + V3 Duplicados | 16 | 46/31 modular; 44/33 bundle desactualizado |
| C | V1 + V2 Tramitación + V5 Mapas | 15 | 45/32 en ambos |
| Integración Mapas/Duplicados (fotografía histórica) | Solo V1 consolidada | 16 | 47/30 |
| Árbol auditado 17/11/2026 | V1 + V2 PostGIS | 16 + `cartografia.contexto_urbano` | 48/29 |

Las tres bases conservan 57 rutas y 77 operaciones. La funcionalidad de A no se sustituye por versiones antiguas: el código de producción de C fuera de Duplicados y la protección adicional de limpieza de usuarios permanece intacto; no hay cambios en frontend. El bloque generado de Mapas coincide byte a byte con V5 de C. Los casos de test desaparecidos al comparar nombres son únicamente los renombrados/adaptados de instalación; el resto de métodos se conserva, incluidas las dos regresiones eliminadas en B.

Fuentes SQL históricas, fuera de la instalación actual: [V1 de A](https://github.com/CeNtAuRoOo/urban-pulse-app/blob/27357ea1dc86479d4322028a5f63c6ea66f22177/backend/src/main/resources/db/migration/V1__initial_schema.sql), [V2 de A](https://github.com/CeNtAuRoOo/urban-pulse-app/blob/27357ea1dc86479d4322028a5f63c6ea66f22177/backend/src/main/resources/db/migration/V2__processing_phase_one.sql), [V3 de B](https://github.com/CeNtAuRoOo/urban-pulse-app/blob/b08ad60845ae3200b8b751e30ecc1228438a641e/backend/src/main/resources/db/migration/V3__duplicate_links.sql) y [V5 de C](https://github.com/CeNtAuRoOo/urban-pulse-app/blob/50d7ed703f1575b5d1f6437f0cd6a7180060c44f/backend/src/main/resources/db/migration/V5__maps_geography.sql).

## Matriz de cobertura

Los endpoints de la tabla son relativos a `/api/v1`; el inventario exacto por método figura al final. Hay **13 paquetes superiores de producción**, incluidos `audit` y `shared`, **14 tags**, **57 rutas y 77 operaciones: 48 implementadas y 29 futuras**. Tags, paquetes y rutas son conceptos distintos.

| Módulo | Casos de uso | Endpoints implementados | Endpoints futuros | Backend | Frontend | Persistencia | Restricciones | Pruebas | ADR | Fuente de diagrama | Pendientes | Evidencia |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Auth / Usuarios | Registro, correo, sesión, perfil y administración | 5 auth + 5 usuarios | 0 en estos tags | auth, identity | Formularios/panel conectados | users, roles, user_roles, email_verifications | JWT, correo verificado, permisos actuales, último ADMIN, Unicode | AuthenticationPostgresIT, EmailVerificationPostgresIT, UserRepositoryPostgresIT y unitarias | 0005, 0012 | C4, ER03, clases | Administrador inicial y operación productiva | Código y suites preservados de A/C |
| Invitaciones | Emisión, consulta, canje y revocación | 5 | 0 | invitations | Panel conectado | staff_invitations | HMAC, caducidad, ámbito, transacción/auditoría | StaffInvitationPostgresIT | 0007 | ER03, clases | Entrega SMTP externa | Suite completa, sin pérdida de escenarios |
| Departamentos / Categorías | Catálogos y pertenencias | 4 por catálogo | 0 | organization, categories | Panel conectado | departments, department_memberships, categories | ADMIN para escribir, unicidad, historial de pertenencias | DepartmentPostgresIT, DepartmentMembershipPostgresIT, CategoryPostgresIT | 0009, 0012, 0013 | ER01/03, clases | API de retirada de permisos | Unicode, roles, SQL y auditoría conservados |
| Incidencias | Alta, listados, detalle, PATCH e historial | 6 | 0 del núcleo | incidents | Formularios/listados conectados | incidents; audit_events | Titularidad, ETag/If-Match, concurrencia, historial institucional | IncidentPostgresIT y unitarias/MVC | 0014 | ER01/02, clases | Evolución del resto de tags | Dos regresiones de privacidad/N+1 conservadas |
| Evidencias | Subir, listar, consultar, borrar y descargar foto | 5 | 0 | attachment | Pendiente | attachments; S3 privado | JPEG/PNG, 10 MiB, cinco activas, permisos/estado, baja lógica | AttachmentServicePostgresIT y unitarias | 0015 | ER01, clases | UI y ciclo real de almacenamiento productivo | Dobles S3, PostgreSQL efímero |
| Tramitación | Revisión, información adicional, prioridad e historiales | 10 | 9 | processing, fase 1 | Pendiente | incident_reviews, incident_information_responses, priority_decisions, status_changes | Snapshot obligatorio; FK compuesta; transición, rol, autor, versión y auditoría | ProcessingPostgresIT, ProcessingMigrationPostgresIT, IncidentPostgresIT | 0010, 0014 | ER02/05, clases | Derivación, equipos, trabajo y comentarios | Reglas de A conservadas en V1 |
| Duplicados | Sugerencia y confirmación manual | 2 | 0 en el contrato actual | duplicates | Pendiente | duplicate_links; sugerencias sin persistencia | ±72 h/250 m inclusivos, top 20 global, timeout 5 s/503, par UUID único, FK RESTRICT | DuplicatePostgresIT y tres clases unitarias | Decisión del responsable en este informe | C4, ER01, clases | RF16 parcial: semántica textual | HTTP real, ranking, límites, concurrencia, rollback y timeout |
| Mapas | Consulta provincial, viewport, filtros y clustering | 1 | 0 en el tag | maps; consulta JDBC | Visor pendiente | Sin tabla Map; siete funciones y tres índices | WGS84, IGN, VALIDATE histórico, punto público 100 m, 1000 representaciones, cuota local, timeout | MapPostgresIT, MapPerformancePostgresIT y unitarias | 0017 | C4, nota de DTO en clases | RF08 parcial: visor, capas, títulos aprobados | Consulta y matemáticas conservadas de C |
| Contexto urbano | Consulta de catálogo de activos por tipo/proximidad | 1 (`GET /urban-assets`) | 5 | `cartografia.contexto_urbano` (V2/PostGIS) | Backend de solo lectura; sin UI | `UrbanAsset` / `UrbanAssetRepository` | SRID 25830; `ST_DWithin`; límites del servicio | `UrbanAssetControllerIT` existe, pero no está incluido en `postgres-it`; unitarias de service/mapper | ADR-0001 + guía PostGIS | ER04 / clases | Alinear Compose, Hibernate Spatial y SQL schema-qualified; asociaciones/proveedores futuros | Código presente; ejecución de V2 pendiente de acreditar |
| CI / Despliegue | Verificar código, contrato y configuración | No aplica | No aplica | Tres jobs CI | Test, tipos, lint y export | Testcontainers PostgreSQL 18 | Sin credenciales ni base persistente | 14 suites Failsafe, Surefire y scripts | 0016 | C4 | Despliegue y carga productiva | Resultados de esta ejecución abajo |

## Esquema y generación

En la fotografía de la integración Mapas/Duplicados solo se había consolidado V1. En el árbol auditado el 17/11/2026 también está versionada V2 (`V2__create_cartografia.sql`), que habilita PostGIS y crea `cartografia.contexto_urbano` e índices. V1 contiene las 16 tablas de dominio: users, roles, user_roles, email_verifications, departments, department_memberships, staff_invitations, audit_events, categories, incidents, status_changes, incident_reviews, incident_information_responses, priority_decisions, attachments y duplicate_links. Flyway crea aparte flyway_schema_history. Los únicos datos iniciales son los cinco roles legítimos.

V1 incorpora directamente AWAITING_INFORMATION, resulting_status obligatorio, CHECK validado de decisión/snapshot/motivo y la FK compuesta de respuestas. Los índices y restricciones anteriores permanecen. DuplicateLink conserva sus FK restrictivas, par canónico único, autor, fecha y motivo opcional. El orden canónico usa UUID como PostgreSQL, sin la comparación Java con signo.

La región delimitada por `BEGIN/END GENERATED MAP GEOGRAPHY` contiene exactamente la salida geográfica de C: map_xy_v1, map_lat_v1, map_lon_v1, map_outline_v1, map_vertices_v1, map_in_malaga_v1 y map_public_point_v1; índices maps_exact_location_idx, maps_public_location_idx y maps_validated_review_idx. No se simplifica la geometría ni se crean tablas cartográficas.

El GeoJSON conserva SHA-256 `eb15c7b0de3b648bedc52202a79af47c8887841dce2914986cfca774d8d101ba`, 12.339 vértices, licencia y procedencia IGN/CNIG. `.gitattributes` evita que un checkout Windows convierta sus bytes a CRLF y rompa la verificación. El generador solo sustituye su región; `--check` es de lectura. Tres pruebas detectan cambios, marcadores inválidos y alteración del checksum, y comprueban la conservación del resto de V1.

## Cobertura de informes y hallazgos nuevos

Se leyeron íntegramente `informe_revision_PR26_mapas.md` e `informe_revision_PR26_mapas_27357ea1.md`. Sus comparaciones A→C se contrastaron con Git y con B. Ambos sostienen que no había un defecto funcional bloqueante demostrado de Mapas; no se reescriben sus políticas ni su geometría.

| Hallazgo | Reconciliación y evidencia |
|---|---|
| H-01 / B1 / C-01 | Modelo de dominio incluye Mapas y Duplicados backend, V1 y límites RF08/RF16 |
| H-02 / B1 / C-02 | Roadmap conserva RF01–RF26 y distingue backend de visor/capas/semántica pendientes |
| H-03 / B1 / C-03 | CHANGELOG añade integración/Mapas y preserva las entradas históricas, incluida Duplicados |
| H-04 / B1 / C-04 | README y esta matriz distinguen 57 rutas, 77 operaciones, módulos y alcance frontend |
| H-05 / B1 / C-05 | domain-openapi describe implementaciones y pendientes por separado |
| H-06 / B4 / C-06 | Se mantiene 0.8.0 sin atribuir implementaciones actuales a 0.7.0 histórica |
| B2 / C-07 | C4 conserva un monolito; PostgreSQL representa funciones/índices; ER y clases actualizados; SVG intactos |
| B3 / C-08 | Despliegue/pom/guías recogen catorce suites Failsafe y V1 |
| R1 / C-09 | CI reproduce YAML/HTML, verifica paridad, generador y respuestas Mapas contra contrato |
| R2 / C-10 | Carga concurrente productiva, percentiles, coste de escrituras y cuota distribuida siguen pendientes; no se inventan garantías |
| R3 / C-10 | ADR-0017 explicita alternativas PostGIS, caché y desplazamiento aleatorio con sus costes |
| R4 / C-10 | Se añade timeout PostgreSQL real de Mapas; suites geográficas y equivalencia conservadas |
| R5 / C-10 | Títulos aprobados y visor siguen futuros; no se amplía producto |
| B-01 | V1 integra 16 tablas y geografía; V2/V3/V5 dejan de ser ejecutables; antecedentes por SHA |
| B-02 | Dos operaciones Duplicados y Mapas implementadas en fuente/bundle/HTML; prueba contra divergencias |
| B-03 | Se conserva IncidentPostgresIT de C y sus dos regresiones perdidas en B; se integra cobertura adicional de SQL |
| B-04 | No se anuncia UI de Duplicados, desvinculación ni prevención general de ciclos |
| B-05 | Se elimina el corte inicial de cien recientes; ranking SQL de todos los válidos, corte final 20, 503 explícito |
| B-06 y revisión transversal | HTTP real, Unicode, par canónico, concurrencia, auditoría/rollback, FK, limpieza de cuentas y coherencia REST |
| Nuevo: UUID firmado | UUID.compareTo de Java podía discrepar del CHECK PostgreSQL; orden textual canónico y prueba 000…/fff… |
| Nuevo: NUL oculto por trim | Se valida Unicode original antes de normalizar; NUL y sustitutos aislados producen 422 |
| Nuevo: limpieza de usuarios | Las FK de respuestas y duplicate_links deben excluir al usuario de la limpieza aun sin evento auditado |
| Nuevo: checkout CRLF | Los bytes IGN se preservan y el bloque SQL se verifica de forma determinista en Windows/Linux |
| Nuevo: fixtures e inventarios | Truncates incluyen duplicate_links y catálogo incluye la tabla; los fallos iniciales se reparan sin quitar casos |

La recomendación de exportar SVG del informe no se ejecuta: prevalece la instrucción explícita del responsable de no modificar esos archivos.

## Conservación de pruebas e historia

`municipalCorrectionsNeverExposeEmployeeInPublicHistory` y `publicHistoryQueryCountDoesNotGrowWithDistinctEmployeeActors` permanecen en IncidentPostgresIT. La comparación de DepartmentPostgresIT distingue formato de semántica; conserva escenarios Unicode, roles, SQL, auditoría y transacciones. auth-test.properties mantiene ambos orígenes CORS de las ramas.

Los ensayos incrementales antiguos tenían un propósito que ya no es ejecutable con una única instalación inicial: preservar revisiones/fotos y comprobar coexistencia de geografía/Tramitación en otro orden. ProcessingMigrationPostgresIT los sustituye por instalación limpia, rechazo de filas inválidas, conservación de datos válidos, validación e idempotencia; MapPostgresIT mantiene importación/consulta, reconstrucción de índices con datos y search_path distinto. No se simulan filas históricas inválidas ni CHECK NOT VALID en el esquema nuevo. Los procedimientos originales permanecen identificados como históricos en ADR-0006 y Desarrollo, y el SQL original se conserva en Git por los SHA A/B/C. Los nombres actuales equivalentes son `schemaRecordsConsolidatedV1AndIndexesFinalStructures`, `flywayV1PersistsCanonicalPairsAndRejectsRepeatedOrSelfLinks`, `installsFinalV1AndPreservesReviewsPhotosAndResponseInvariants`, `reinstallIsIdempotentAndAllConstraintsAreValidated` y `consolidatedSchemaRebuildsIndexesOverImportedDataAndWorksOutsideItsSchemaSearchPath`.

Duplicados añade pruebas HTTP de roles actuales, correo/actividad, errores, original intacto, Unicode, más de cien candidatos, ruido espacial, empates, categorías ausentes, límites temporales/espaciales y antimeridiano. Confirma atomicidad en carrera inversa y rollback de auditoría; prueba timeout SQL real y conservación de referencias históricas. Mapas añade timeout real sin alterar su publicación por VALIDATE histórico.

## Validación de esta integración

Auditoría y decisión iniciadas el 16/11; cierre y repetición final el 17/11/2026. Los registros locales están en `backend/target/merge-verification/` (ignorados por Git). Las ejecuciones iniciales fallidas se conservan como diagnóstico; no se contabilizan como éxito. Una primera ejecución sin clean recogió migraciones antiguas de target; las siguientes usan una copia de pom/src dentro de un contenedor nuevo y `clean verify`. Se corrigieron fixtures que omitían la nueva FK, un nombre de rol incorrecto y un caso temporal que adelantaba created_at sin actualizar updated_at. No se relajaron las restricciones para hacerlos pasar. Solo Testcontainers PostgreSQL 18 efímero; sin volúmenes de datos ni arranque de Compose.

| Comando / comprobación | Entorno | Resultado de esta ejecución | Evidencia local |
|---|---|---|---|
| `mvn -B -ntp clean verify -Ppostgres-it` | Maven 3.9.16, Temurin 25, PostgreSQL 18 efímero | **PASS: 369 pruebas; 194 Surefire en 34 suites y 175 Failsafe en 14 suites, cero fallos/errores/omisiones; 4 min 23 s** | backend-complete.log; complete/surefire-reports y failsafe-reports |
| `mvn -B -ntp javadoc:javadoc -Ddoclint=all` | Mismo ejecutor aislado | PASS; cero errores, 100 avisos de documentación emitidos; 16,368 s | javadoc-complete.log |
| `npm ci` | Copia de archivos frontend versionados, Node 24, sin dotenv privado | PASS; 840 paquetes instalados. Auditoría informa 21 vulnerabilidades: 1 baja, 14 moderadas y 6 altas | frontend-install.log |
| `npm test` | Frontend aislado | PASS: 5 tests, cero fallos/omisiones | frontend-tests.log |
| `npx tsc --noEmit` | Frontend aislado | PASS | frontend-types.log |
| `npm run lint` | Frontend aislado | PASS | frontend-lint.log |
| `npx expo export --platform web` | API sintética localhost; EXPO_NO_DOTENV=1 | PASS: dist generado, sin desplegar | frontend-export.log |
| Redocly 2.16.0 `lint` modular y bundle | Node 24; versión fijada | PASS sin errores; 2 avisos de fuente y 5 de bundle (licencia/localhost y componentes futuros sin uso) | openapi-source-lint.log, openapi-bundle-lint.log |
| Redocly `bundle` + `build-docs --template docs/api/redoc-template.hbs`, repetidos | Misma fuente conjunta | PASS: SHA-256 idénticos entre generaciones | openapi-reproduce.log/exit |
| `validate-openapi.cjs` y `node --test ...validate-openapi.test.cjs` mediante npx Redocly | Parser YAML de paths/métodos y referencias | PASS histórico: 57 rutas, 77 operaciones, 47/30; 5 tests de paridad/Unicode (antes de añadir GET /urban-assets al estado implementado) | openapi-parity.log |
| `node backend/scripts/generate-map-boundary.mjs --check` | Node, fuente IGN local | PASS: 12.339 vértices y región exacta de V1 | Salida del comando y checksum |
| `node --test backend/scripts/generate-map-boundary.test.mjs` | Copias temporales aisladas | PASS: 3 tests | Salida TAP; sin modificar la V1 real |
| `validate-map-contract.cjs` | Respuestas HTTP de la ejecución final | PASS: respuestas INCIDENT/CLUSTER conformes; ambos rechazan campos privados añadidos | map-contract.log |
| `docker compose ... config --quiet` | Copia de compose y plantillas .env.example | PASS; sin arrancar servicios ni modificar configuración privada | Código de salida 0 |
| Markdown, drawio, SVG, Git | Árbol reconciliado | Archivos y anclas locales válidos; XML/IDs/conexiones correctos; 8 hashes SVG iniciales idénticos; diff --check limpio | Auditoría local; inventario de diagramas |

No se actualizan dependencias de amplio impacto: los avisos de npm corresponden al lockfile existente, que no cambia. Requieren una revisión de dependencias separada. La CI remota de este commit no se ejecuta porque no se hace push; se reproducen localmente sus comandos aplicables. S3/SMTP se prueban con dobles, no contra servicios productivos.

### Desglose PostgreSQL de la ejecución final

| Suite Failsafe | Pruebas ejecutadas | Resultado |
|---|---:|---|
| AttachmentServicePostgresIT | 19 | PASS |
| AuthenticationPostgresIT | 8 | PASS |
| EmailVerificationPostgresIT | 12 | PASS |
| CategoryPostgresIT | 6 | PASS |
| DuplicatePostgresIT | 10 | PASS |
| UserRepositoryPostgresIT | 15 | PASS |
| IncidentPostgresIT | 20 | PASS |
| StaffInvitationPostgresIT | 24 | PASS |
| MapPerformancePostgresIT | 4 | PASS |
| MapPostgresIT | 21 | PASS |
| DepartmentMembershipPostgresIT | 9 | PASS |
| DepartmentPostgresIT | 6 | PASS |
| ProcessingMigrationPostgresIT | 2 | PASS |
| ProcessingPostgresIT | 19 | PASS |

Los XML confirman la ejecución satisfactoria de `municipalCorrectionsNeverExposeEmployeeInPublicHistory`, `publicHistoryQueryCountDoesNotGrowWithDistinctEmployeeActors`, `actualDatabaseLockTimeoutReturns503WithoutPartialMap` y `temporalBoundariesDistanceAndAntimeridianRemainInclusive`. Temurin 25.0.4.1, PostgreSQL 18.7, Node 24.15.0 y npm 12.2.2 fueron las versiones observadas. El directorio ejecutable de migraciones contiene exclusivamente V1; el catálogo comprueba las 16 tablas, siete funciones, tres índices Mapas, cinco roles y restricciones validadas.

### Rendimiento: medianas SQL medidas en esta ejecución

Cada celda compara tres repeticiones de la consulta anterior y la conservada en C. Las cuatro pruebas comprueban equivalencia de resultados y límites, además de guardar planes. Los datos siguientes son sintéticos locales, no percentiles productivos ni comparación del rendimiento de ramas completas.

| Escenario | Consulta anterior, ms | Consulta actual, ms |
|---|---:|---:|
| coast-1000 | 4343.707 | 9.109 |
| coast-1000-filtered | 1257.272 | 5.369 |
| mixed-1000-province | 16.465 | 25.903 |
| mixed-1000-city | 11.163 | 13.556 |
| mixed-1000-filtered | 1.784 | 1.933 |
| mixed-1000-zoom18 | 4.053 | 1.844 |
| mixed-10000-province | 144.457 | 205.141 |
| mixed-10000-city | 118.479 | 104.359 |
| mixed-10000-filtered | 14.261 | 6.527 |
| mixed-10000-zoom18 | 75.542 | 51.451 |
| mixed-50000-province | 943.710 | 123.394 |
| mixed-50000-city | 569.597 | 86.811 |
| mixed-50000-filtered | 83.102 | 30.120 |
| mixed-50000-zoom18 | 206.456 | 43.450 |

Hay regresiones en escenarios pequeños y en la consulta provincial de 10.000 avisos; las mejoras de costa y mayor volumen no justifican una garantía universal. No se alteró la consulta de Mapas para ocultar estos resultados.


## Fuentes gráficas y pendientes

Se mantienen las tres fuentes y sus ocho páginas, IDs/conexiones/estilos y geometrías existentes salvo las adiciones necesarias. C4 incluye processing/maps/duplicates en la API y V1/funciones/GiST en PostgreSQL; ER mantiene DuplicateLink con nombres reales y revisión con snapshot obligatorio; clases añade una nota de DTO Mapas en espacio libre, sin entidad Map ni visor supuesto.

**Los ocho SVG permanecen byte a byte intactos y pendientes de regeneración manual y revisión visual.** Validar XML/IDs/geometría no equivale a haber actualizado visualmente esas exportaciones. El [inventario](diagrams/README.md) identifica cada salida.

Quedan fuera de esta integración la UI de Evidencias/Tramitación/Mapas/Duplicados, capas urbanas, semántica textual, títulos aprobados, flujo completo de trabajo, despliegue productivo, SMTP externo, prueba real de almacenamiento Neon, carga concurrente/percentiles y rate limiting distribuido. Las mediciones sintéticas son evidencia de regresión, no una promesa universal de rendimiento.

## Inventario de operaciones

| Tag | Método y ruta | Estado |
|---|---|---|
| Autenticación | POST /auth/register | implemented |
| Autenticación | POST /auth/login | implemented |
| Autenticación | POST /auth/email-verifications/confirm | implemented |
| Autenticación | POST /auth/email-verifications/resend | implemented |
| Autenticación | POST /users/me/password-changes | implemented |
| Usuarios | GET /users/me | implemented |
| Usuarios | PATCH /users/me | implemented |
| Usuarios | GET /users | implemented |
| Usuarios | GET /users/{userId} | implemented |
| Usuarios | PATCH /users/{userId} | implemented |
| Invitaciones | POST /staff-invitations | implemented |
| Invitaciones | GET /staff-invitations | implemented |
| Invitaciones | GET /staff-invitations/{invitationId} | implemented |
| Invitaciones | POST /users/me/staff-invitation-redemptions | implemented |
| Invitaciones | POST /staff-invitations/{invitationId}/revocations | implemented |
| Catálogos | GET /categories | implemented |
| Catálogos | POST /categories | implemented |
| Catálogos | GET /categories/{categoryId} | implemented |
| Catálogos | PATCH /categories/{categoryId} | implemented |
| Catálogos | GET /departments | implemented |
| Catálogos | POST /departments | implemented |
| Catálogos | GET /departments/{departmentId} | implemented |
| Catálogos | PATCH /departments/{departmentId} | implemented |
| Incidencias | POST /incidents | implemented |
| Incidencias | GET /incidents | implemented |
| Incidencias | GET /incidents/mine | implemented |
| Incidencias | GET /incidents/{incidentId} | implemented |
| Incidencias | PATCH /incidents/{incidentId} | implemented |
| Incidencias | GET /incidents/{incidentId}/history | implemented |
| Evidencias | POST /incidents/{incidentId}/attachments | implemented |
| Evidencias | GET /incidents/{incidentId}/attachments | implemented |
| Evidencias | GET /incidents/{incidentId}/attachments/{attachmentId} | implemented |
| Evidencias | DELETE /incidents/{incidentId}/attachments/{attachmentId} | implemented |
| Evidencias | GET /incidents/{incidentId}/attachments/{attachmentId}/content | implemented |
| Tramitación | POST /incidents/{incidentId}/reviews | implemented |
| Tramitación | GET /incidents/{incidentId}/reviews | implemented |
| Tramitación | POST /incidents/{incidentId}/priority-decisions | implemented |
| Tramitación | GET /incidents/{incidentId}/priority-decisions | implemented |
| Tramitación | PUT /incidents/{incidentId}/department | future |
| Tramitación | POST /incidents/{incidentId}/assignments | future |
| Tramitación | GET /incidents/{incidentId}/assignments | future |
| Tramitación | POST /incidents/{incidentId}/assignments/{assignmentId}/acceptance | future |
| Tramitación | POST /incidents/{incidentId}/status-changes | future |
| Tramitación | GET /incidents/{incidentId}/status-changes | future |
| Tramitación | POST /incidents/{incidentId}/comments | future |
| Tramitación | GET /incidents/{incidentId}/comments | future |
| Tramitación | GET /incidents/assigned-to-me | future |
| Duplicados | GET /incidents/{incidentId}/duplicate-candidates | implemented |
| Duplicados | POST /incidents/{incidentId}/duplicate-links | implemented |
| Contexto urbano | GET /incidents/{incidentId}/urban-context | future |
| Contexto urbano | GET /incidents/{incidentId}/asset-candidates | future |
| Contexto urbano | PUT /incidents/{incidentId}/asset-association | future |
| Contexto urbano | GET /urban-assets | implemented (consulta de catálogo) |
| Contexto urbano | GET /urban-assets/{assetId} | future |
| Contexto urbano | GET /external-observations | future |
| Mapas | GET /map/incidents | implemented |
| Notificaciones | GET /notifications | future |
| Notificaciones | PATCH /notifications/{notificationId} | future |
| Notificaciones | GET /users/me/notification-preferences | future |
| Notificaciones | PUT /users/me/notification-preferences | future |
| Analítica | GET /analytics/statistics | future |
| Analítica | GET /zones/{zoneId}/analysis | future |
| Analítica | GET /analytics/correlations | future |
| Informes | POST /reports | future |
| Informes | GET /reports | future |
| Informes | GET /reports/{reportId} | future |
| Informes | GET /reports/{reportId}/file | future |
| Administración | GET /external-sources | future |
| Administración | POST /external-sources | future |
| Administración | PATCH /external-sources/{sourceId} | future |
| Administración | GET /audit-events | future |
| Tramitación | GET /incidents/{incidentId}/reviews/{reviewId} | implemented |
| Tramitación | GET /incidents/{incidentId}/priority-decisions/{decisionId} | implemented |
| Tramitación | POST /incidents/{incidentId}/information-responses | implemented |
| Tramitación | GET /incidents/{incidentId}/information-responses | implemented |
| Tramitación | GET /incidents/{incidentId}/information-responses/{responseId} | implemented |
| Tramitación | GET /incidents/{incidentId}/review-outcome | implemented |


## Actualización documental: PostGIS y Contexto urbano (17/11/2026)

La matriz anterior se elaboró para la integración de Mapas/Duplicados y no reflejaba el módulo `urban_context` incorporado posteriormente en el árbol auditado. La inspección de los fuentes actuales confirma que `V2__create_cartografia.sql` está versionada y que existen `UrbanAsset`, `UrbanAssetRepository`, `UrbanAssetService`, `UrbanAssetController` y `UrbanAssetControllerIT`. Por tanto, `GET /api/v1/urban-assets` deja de clasificarse como futuro: consulta el catálogo unificado por `source_table` o proximidad PostGIS.

Esta actualización **no** convierte en implementadas las asociaciones a incidencias, las observaciones externas ni el contexto agregado. Tampoco acredita ejecución de V2 en Neon o Compose. Se detectan discrepancias operativas que se documentan en [PostGIS y contexto urbano](postgis-urban-context.md): Compose usa `postgres:18-alpine` sin PostGIS; el SQL nativo de proximidad no califica `cartografia.contexto_urbano`; el test `UrbanAssetControllerIT` no está incluido en el perfil `postgres-it`; y `hibernate-spatial` no aparece como dependencia explícita en `backend/pom.xml`.

La matriz de migraciones de la sección histórica («solo V1 ejecutable») debe leerse como fotografía de la integración de Mapas/Duplicados, no como inventario del árbol actual. En el árbol actual hay V1 y V2. Antes de afirmar que el esquema completo arranca, hay que validar ambas migraciones sobre una base PostgreSQL 18 con PostGIS y reconciliar la configuración de Compose/Neon.
