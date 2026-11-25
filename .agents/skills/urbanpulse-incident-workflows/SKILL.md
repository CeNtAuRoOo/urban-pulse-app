---
name: urbanpulse-incident-workflows
description: Implement or plan UrbanPulse incident views, citizen reporting, municipal validation, priority, assignment, technician queue, timeline, maps or analytics; map UI actions to supported role permissions and RF requirements.
---

# UrbanPulse: flujos de incidencias por rol

## Fuentes y alcance
Consulta el enunciado, la documentación del repositorio y el backend real. El enunciado describe el alcance objetivo, no garantiza que todas las funciones estén implementadas. Usa solo los RF de la iteración solicitada.

## Matriz de referencia del producto
- Ciudadano: registrar incidencia (RF03), localización (RF04), evidencias (RF05), consulta y seguimiento de las propias incidencias (RF06).
- Operador municipal: búsqueda y filtros (RF07), mapa (RF08), validar/solicitar información/rechazar con motivo (RF09), prioridad justificada (RF10), asignación (RF11), colaboración (RF13), contexto (RF24).
- Técnico: cola de trabajos autorizados, progreso y evidencias de resolución (RF12/RF13).
- Administrador: configuración y acceso según RF01/RF02, trazabilidad RF18.
- Analista: indicadores, análisis territorial e informes (RF15/RF17/RF19/RF25) según el alcance implementado.
- Los roles anteriores son conceptos del enunciado; verifica nombres y permisos exactos en la implementación.

## Flujos
1. Para cada vista identifica RF, rol, objetivo, entradas, operación API, confirmación, estados alternativos y pruebas.
2. Reporte: título, descripción, posición y categoría opcional en el enunciado; respeta validación real. Muestra precisión, dirección y evidencias si disponibles. Si la ubicación externa falla, permite alternativas compatibles con la API. No asumas que coordenadas sean opcionales.
3. Seguimiento: representa estados del contrato y muestra fechas e historial permitido. El enunciado enumera REPORTED, VALIDATED, REJECTED, ASSIGNED, IN_PROGRESS, RESOLVED, REOPENED, CLOSED; comprueba los valores reales. No confundas RESOLVED con CLOSED. Motivos y permisos según backend.
4. Operación municipal: conserva filtros, ofrece detalle, muestra acciones permitidas y pide motivo cuando corresponda. No alteres estados localmente como fuente de verdad.
5. Inteligencia asistida (cuando exista): distingue sugerencia y decisión confirmada; muestra confianza, procedencia y evidencia según contrato; nunca inventes resultados. Un duplicado sugerido no debe impedir aportar información.
6. Mapas y contexto: protege localizaciones y evidencias privadas; usa fuente y fecha si están disponibles, distingue dato ausente de cero, no inventes estadísticas ni coordenadas. Muestra degradación parcial RF26 sin bloquear acciones independientes.

## Salida
Entrega una tabla RF–pantalla–rol–operación real–prueba, con huecos explícitos. Para lógica HTTP usa urbanpulse-api-integration; para diseño y pruebas usa las skills correspondientes. No amplíes alcance sin autorización.
