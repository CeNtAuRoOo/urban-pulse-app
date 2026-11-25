## Desarrollo frontend de UrbanPulse

- Antes de tocar el frontend, revisa las instrucciones existentes, requisitos de la iteración, contrato OpenAPI, backend y convenciones del repositorio. Mantén la arquitectura y stack acordados.
- Para diseño o cambios visuales, lee y aplica `.agents/skills/urbanpulse-ui-design/SKILL.md`.
- Para conectar vistas con la API, autenticación o permisos, lee y aplica `.agents/skills/urbanpulse-api-integration/SKILL.md`.
- Para funcionalidades de incidencias o vistas por rol, lee y aplica `.agents/skills/urbanpulse-incident-workflows/SKILL.md`.
- Para cualquier cambio funcional o visual de frontend, lee y aplica `.agents/skills/urbanpulse-frontend-qa/SKILL.md` antes de terminar.
- Usa las skills externas disponibles de diseño, accesibilidad y testing cuando sean pertinentes; no introduzcas servicios externos o dependencias solo porque una skill los recomienda.
- No inventes rutas, DTO, roles, permisos, estados ni estadísticas. Si falta una API, informa del bloqueo. No cambies el backend ni el alcance sin autorización.
- Sigue el flujo de Git del proyecto; no realices commits, push, merge o despliegues sin solicitud. Documenta decisiones relevantes, C4, ADR y pruebas cuando corresponda.
- Al terminar, indica qué skills aplicaste, cambios, pruebas ejecutadas, resultados y limitaciones. No afirmes verificaciones que no realizaste.

## Coordinación de skills para el frontend

- Las cuatro skills `urbanpulse-*` referenciadas arriba son propias del proyecto. Las skills con prefijo `build-web-apps:` proceden del plugin Build Web Apps; utiliza sus instrucciones disponibles en la sesión, sin fijar rutas locales de caché del plugin en el repositorio.
- Para diseñar e implementar nuevas interfaces, utiliza `build-web-apps:frontend-app-builder` junto con `urbanpulse-ui-design`.
- Para verificar cambios funcionales y visuales, utiliza `build-web-apps:frontend-testing-debugging` junto con `urbanpulse-frontend-qa`.
- Para revisar accesibilidad, usabilidad, diseño responsive y calidad de las interfaces, utiliza `web-design-guidelines`, instalada localmente en `.agents/skills/web-design-guidelines/SKILL.md` y basada en las directrices de Vercel. Lee sus reglas incluidas en `.agents/skills/web-design-guidelines/references/command.md` antes de cada revisión; no descargues reglas de la red.
- Aplica `build-web-apps:react-best-practices` únicamente si el frontend utiliza React; limita las recomendaciones específicas de Next.js a proyectos que lo utilicen.
- Aplica `build-web-apps:shadcn` únicamente si el frontend utiliza shadcn/ui. Su archivo en el plugin está en `skills/shadcn-best-practices/SKILL.md`, pero el nombre declarado de la skill es `shadcn`.
- Antes de aplicar una skill, comprueba que está disponible y lee sus instrucciones. Si alguna no está disponible, indícalo explícitamente; no afirmes haberla utilizado ni la instales automáticamente.

## Prioridades y límites de las skills externas

- Respeta las particularidades del dominio, la arquitectura, los requisitos funcionales y la API REST establecidas en las skills propias de UrbanPulse y en la documentación del proyecto.
- Si una skill externa contradice una decisión arquitectónica del proyecto, prevalece la decisión del proyecto. Comunica el conflicto, la recomendación afectada y la decisión aplicada; no cambies la arquitectura para acomodar la skill.
- Busca una identidad visual moderna, profesional, diferenciada y coherente con UrbanPulse, evitando interfaces genéricas generadas por IA y respetando el sistema visual acordado.
- Prioriza la reutilización de componentes y la mantenibilidad; conserva las convenciones y la separación de responsabilidades del repositorio.
- No inventes endpoints, funcionalidades, permisos ni datos. Las recomendaciones de prototipos o interacciones simuladas de una skill externa no autorizan ampliar el alcance ni presentar una simulación como integración real; los mocks siguen sujetos a las restricciones de `urbanpulse-api-integration`.
- No introduzcas nuevas tecnologías, servicios o dependencias únicamente porque una skill los recomiende. Sus preferencias de framework, librerías o herramientas no sustituyen el stack ni las decisiones del proyecto.
- No utilices las skills de Stripe o Supabase, incluidas `build-web-apps:stripe-best-practices` y `build-web-apps:supabase-postgres-best-practices`, sin autorización expresa del usuario.

## Verificación y cierre de tareas frontend

- Antes de dar por terminada cualquier implementación frontend, realiza las comprobaciones funcionales, visuales, responsive y de accesibilidad pertinentes, combinando las skills anteriores según el alcance y las tecnologías utilizadas.
- Verifica la interfaz renderizada y los flujos afectados, además de ejecutar los scripts de lint, tipos, pruebas y build que correspondan. Comprueba móvil, tablet y escritorio según `urbanpulse-frontend-qa`; un build correcto no sustituye la revisión visual ni la interacción real.
- Al finalizar cada tarea, indica qué skills has utilizado, las pruebas realizadas, sus resultados y las posibles limitaciones. Distingue comprobaciones pasadas, fallidas y no ejecutadas; comunica cualquier bloqueo o conflicto pendiente y no declares verificado lo que no has comprobado.
