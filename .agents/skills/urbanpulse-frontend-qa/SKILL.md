---
name: urbanpulse-frontend-qa
description: Review, test and debug UrbanPulse frontend changes, rendered UI, accessibility, responsive behavior, real API integration, role flows and regression risks before declaring work complete.
---

# UrbanPulse: revisión y pruebas del frontend

## Procedimiento
1. Lee AGENTS.md, scripts del proyecto y criterios de aceptación. Comprueba git diff y cambios previos; no borres trabajo ajeno.
2. Ejecuta los scripts existentes de lint, comprobación de tipos, pruebas y build que correspondan al cambio. No inventes comandos ni instales herramientas sin autorización. Reporta comandos y resultados exactos.
3. Arranca el frontend en entorno de desarrollo autorizado y, si es necesario, el backend siguiendo README. Usa Browser/Playwright si está disponible; no pruebes mutaciones en producción ni con datos reales sin permiso.
4. Inspecciona consola y red, captura evidencia de vistas relevantes si se puede y prueba móvil (360–390 px), tablet (768 px) y escritorio (1440 px). Corrige defectos del alcance y repite comprobaciones.

## Casos según funcionalidad
- Camino feliz: navegación, formulario, listado, detalle y actualización real cuando corresponda.
- Carga, vacío, error, sesión expirada, permiso denegado, fallo de red, campo inválido y doble clic de envío.
- Roles: verifica que acciones no autorizadas no se presentan como disponibles; comprueba que backend las deniega con pruebas aisladas si el alcance lo permite.
- Incidencias: estados y transiciones reales, motivo requerido, distinción resuelta/cerrada, historial visible y permisos; no inventes datos esperados.
- RF26: si existe una forma soportada de simular una fuente externa fallida, verifica que no impida el reporte válido. Si no, marca no comprobado.
- Accesibilidad: teclado, foco, etiquetas, contraste, mensajes de error, zoom y reducción de movimiento. Un escaneo automatizado no prueba conformidad WCAG completa.
- Filtros y paginación: cambios rápidos, resultados vacíos, sincronización de datos y navegación de vuelta.

## Informe final
Indica alcance, archivos, pruebas pasadas/fallidas/no ejecutadas, comandos, evidencia y defectos por severidad. Distingue mocks de integración real y no declares una prueba superada si no se ejecutó. No hagas commit, push, merge o despliegue sin que se solicite. Si el cambio es arquitectónico, verifica C4/ADR/pruebas según enunciado.
