---
name: urbanpulse-api-integration
description: Integrate UrbanPulse frontend with the existing REST API, OpenAPI, DTOs, authentication, authorization, pagination, filters, incident workflows and API error handling.
---

# UrbanPulse: integración REST y arquitectura frontend

## Descubrimiento
1. Revisa README, AGENTS.md, ADR, OpenAPI y código real del backend (controladores, DTO, seguridad, tests); identifica stack, gestor de paquetes y convenciones del frontend.
2. Relaciona cada pantalla con el requisito funcional y las operaciones realmente disponibles. Si contrato y código difieren, documenta la discrepancia; no la resuelvas inventando rutas o campos.
3. Reutiliza stack y cliente HTTP existentes. Si no existen, plantea alternativas y una recomendación sin introducir cambios arquitectónicos no autorizados.

## Implementación
- Separa componentes visuales, acceso HTTP, lógica de presentación y tipos del contrato, siguiendo la estructura del proyecto. Evita peticiones duplicadas y dependencias innecesarias.
- Usa el mecanismo de autenticación existente. No guardes secretos en código, variables públicas del bundle o logs; no cambies el almacenamiento de credenciales ni el flujo de sesión sin acordarlo.
- Diferencia autenticación y autorización. Las restricciones de interfaz mejoran UX pero nunca sustituyen validación del servidor. Gestiona 401 y 403 según el contrato y evita bucles de redirección.
- Mantén tipos/DTO fieles al contrato; conserva enums y traduce solo las etiquetas visuales. Trata null, ausencia de campos, lista vacía y unidades según la API.
- Implementa filtros, paginación y ordenación con parámetros soportados. Controla respuestas obsoletas al cambiar filtros y refresca datos tras mutaciones según la estrategia de caché vigente.
- Gestiona errores por código HTTP y código de máquina cuando esté disponible. Conserva requestId para diagnóstico sin exponer detalles internos. Evita reintentar automáticamente escrituras no idempotentes; bloquea doble envío mientras está pendiente.
- No inventes roles, permisos, transiciones, categorías ni endpoints. El servidor es autoridad para el ciclo de vida. No modifiques backend solo para acomodar una vista sin autorización.
- Para RF26, muestra contexto externo no disponible como estado parcial si el contrato lo permite, sin bloquear el reporte por ese motivo. No confundas error real de creación con enriquecimiento pendiente.
- Parametriza configuración por entorno según el sistema existente; ninguna variable incorporada al código cliente puede contener secretos. Mantén dependencias explícitas y build reproducible.
- Si una API falta, informa del bloqueo. Solo usa mocks aislados, claramente etiquetados y en desarrollo/pruebas si están autorizados. No presentes una acción simulada como funcional.

## Verificación
Incluye prueba de integración del camino feliz y casos de error relevantes. Comprueba contrato, permisos y actualización de datos. Documenta archivos y endpoints verificados, supuestos, pruebas ejecutadas y limitaciones. Si hay cambio arquitectónico relevante, actualiza C4, ADR y pruebas según las convenciones acordadas.
