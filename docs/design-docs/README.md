# Documentos de diseño

Los cuatro diseños describen la implementación presente. Los ADR conservan los motivos de elección. Para proponer un cambio se utiliza la [plantilla del curso](design-docs-template.md), identificando la hipótesis, alternativas y validación. La incorporación de estas descripciones retrospectivas no registra una aprobación nueva.

| Diseño                                    | Límite                                                            |
| ----------------------------------------- | ----------------------------------------------------------------- |
| [DD-001 Identidad](DD-001-identidad.md)   | JWT, autorización vigente, correo y canje de invitaciones         |
| [DD-002 Expediente](DD-002-expediente.md) | Versiones, tramitación, auditoría y duplicados                    |
| [DD-003 Evidencias](DD-003-evidencias.md) | Autorización, saneamiento y compensación entre SQL y S3           |
| [DD-004 Territorio](DD-004-territorio.md) | Geografía y privacidad provincial, consultas e índices espaciales |
| [DD-005 Contexto Urbano](DD-005-contexto-urbano.md) | Desarollo del microservicio de contexto urbano |

[Arquitectura y C4](../architecture.md) · [Decisiones](../adr/README.md) · [Cuestiones abiertas](../pending.md).
