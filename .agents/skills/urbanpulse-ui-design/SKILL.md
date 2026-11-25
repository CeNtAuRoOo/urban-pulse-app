---
name: urbanpulse-ui-design
description: Design or implement UrbanPulse frontend screens, layouts, responsive components, design tokens and visual refinements; use for citizen and municipal interfaces.
---

# UrbanPulse: diseño visual y experiencia de usuario

## Antes de diseñar
1. Lee AGENTS.md, documentación de requisitos, ADR y frontend existente. Identifica el rol, tarea principal, restricciones y componentes reutilizables.
2. Si existe sistema visual, respétalo. Si no, propone una dirección visual y documenta los tokens antes de extenderla. No impongas framework ni instales dependencias sin justificarlo.
3. Define jerarquía, navegación, acción principal y estados de pantalla antes de implementar.

## Dirección visual propuesta (modificable)
- Identidad cívica contemporánea, sobria y cercana: fondos neutros, azul petróleo o azul profundo, acento verde azulado; contraste comprobado. No son colores obligatorios si ya hay identidad acordada.
- Tipografía legible, escala consistente, espaciado basado en 4/8 px y bordes discretos. Centraliza colores, tamaños y radios en tokens del sistema vigente.
- Evita gradientes ornamentales, exceso de tarjetas idénticas, iconos decorativos y animaciones gratuitas. Usa un conjunto de iconos consistente.
- Ciudadanía: prioridad móvil, lenguaje claro, formulario breve, acción de reportar visible, confirmación con identificador y seguimiento cuando estén soportados.
- Personal municipal: escritorio eficiente, navegación estable, tabla/listado con filtros, búsqueda y detalle de incidencia; adapta a pantallas pequeñas sin ocultar acciones esenciales. Técnico: cola de trabajo usable en móvil.
- La incidencia es el centro del flujo; utiliza etiquetas de estado con texto además de color. Presenta historial de forma cronológica y separa prioridad de estado.
- Mapas y estadísticas solo cuando corresponda al alcance y a datos disponibles. Proporciona alternativa textual a información geográfica importante.

## Accesibilidad y estados
- Objetivo propuesto WCAG 2.2 AA: HTML semántico, nombres accesibles, foco visible, teclado, contraste, mensajes asociados a campos, respeto de reduced motion.
- Diseña carga, vacío, error recuperable, falta de permisos y éxito; conserva entradas si falla un envío. No uses datos ficticios como reales.
- Verifica 360–390, 768 y 1440 px cuando haya navegador; evita scroll horizontal general y comprueba zoom al 200 %.

## Entrega
Enumera pantallas, componentes y tokens afectados. Revisa visualmente la interfaz renderizada, no solo el código; indica qué no se pudo comprobar. Para QA utiliza urbanpulse-frontend-qa.
