# Investigación de requisitos, experiencia de usuario y seguridad

Fecha de consulta: 2026-10-10. Base local: `develop`,
`6f7ae796af30dc10ccb6307c482097a24e3c53de`.
Estado: investigación conservada; **método aprobado el 2026-10-10** e incorporado
al [harness versionado](../../harness/README.md), sin cambios funcionales.
La aprobación corresponde a las prácticas de trabajo, no a ejecutar el backlog,
a políticas sintéticas del ejemplo ni a declarar controles ya verificados.

El propietario pidió profundizar en especificaciones completas, UX/UI y seguridad,
con criterios correctos y una inversión proporcional al estado del negocio.
Se consultaron fuentes primarias y se contrastaron puntos concretos del código.
No es una auditoría independiente, una certificación ni una investigación de todas
las publicaciones existentes. Su alcance es el producto web de Ondexia y sus
flujos, datos e integraciones actuales.

## Documentos y uso

1. [Requisitos y pruebas](requisitos-y-pruebas.md): cómo pasar de una necesidad a
   historias, casos de uso completos y pruebas; criterios de revisión.
2. [Ejemplo de cajero automático](ejemplo-cajero.md): flujo, condiciones, respuestas,
   estados, decisiones y pruebas. Es un modelo didáctico con políticas sintéticas,
   no una especificación bancaria real.
3. [UX/UI](ux-ui.md): tareas, interacción, accesibilidad, sistema visual y evaluación.
4. [Seguridad](seguridad.md): referencias OWASP actuales, controles, evidencias,
   incertidumbres y prioridades técnicas/económicas.
5. [Plantillas](plantillas.md): formatos reutilizables de historia, caso de uso,
   prueba, estudio de uso y control de seguridad.
6. [Backlog de aplicación](backlog.md): actividades acotadas, resultados y relación
   con el [plan de fundamentos](../../planes/2026-10-10-fundamentos-arquitectura-sdlc.md).

La investigación no reemplaza `CLAUDE.md` ni las decisiones aprobadas del negocio.
Una fuente externa informa una recomendación; no concede autorización ni define
por sí sola políticas de Ondexia. Los ejemplos nuevos no son requisitos aprobados.
Los estándares completos de pago, tributación y privacidad requieren fuentes y
revisión propias; aquí no se afirma cumplimiento jurídico.

## Fuentes primarias y función en la investigación

Los enlaces se consultaron en la fecha indicada. Las guías vivas pueden cambiar;
las versiones especificadas deben conservarse al registrar requisitos y pruebas.
Se sintetizan conceptos y se crean ejemplos propios, sin copiar los textos completos.

| ID | Fuente | Qué sustenta y qué no |
|---|---|---|
| R01 | [Agile Alliance: historias de usuario](https://agilealliance.org/glossary/user-stories/) | Incrementos de valor; historia distinta de caso de uso y componente; no prescribe todas las políticas de requisitos |
| R02 | [Agile Alliance: tres C](https://agilealliance.org/glossary/three-cs/) | Tarjeta, conversación y confirmación; no sustituye acuerdos versionados |
| R03 | [Bill Wake: INVEST](https://xp123.com/invest-in-good-stories-and-smart-tasks/) | Calidad de historias e independencia deseable; no impone independencia absoluta |
| R04 | [Alistair Cockburn: plantilla original, alojada por la Universidad de Otago](https://www.cs.otago.ac.nz/coursework/cosc461/uctempla.htm) | Objetivo, alcance, actores, garantías, flujo y extensiones; documento histórico del autor con aclaración de términos posteriores |
| R05 | [Alistair Cockburn: historias, casos de uso y mapas, 2024](https://alistaircockburn.com/Unifying%20us%20uc%20sm.pdf) | Compatibilidad parcial de instrumentos; no relación obligatoria uno a uno |
| R06 | [IREB: glosario CPRE](https://cpre.ireb.org/en/downloads-and-resources/glossary) | Vocabulario de requisitos, verificación y validación; no certifica nuestras especificaciones |
| R07 | [ISTQB CTFL 4.0.1, capítulos 1, 3, 4 y 5](https://istqb.org/wp-content/uploads/2024/11/ISTQB_CTFL_Syllabus_v4.0.1.pdf) | Revisión, técnicas de pruebas y riesgo; no demuestra completitud por cantidad de tests |
| R08 | [Cucumber: referencia de Gherkin](https://cucumber.io/docs/gherkin/reference/) | Contexto, acción y resultado observable; el texto no es ejecutable sin implementación |
| R09 | [Cucumber: Example Mapping](https://cucumber.io/blog/bdd/example-mapping-introduction/) | Descubrimiento mediante reglas, ejemplos y preguntas |
| U01 | [Nielsen Norman Group: heurísticas de usabilidad](https://www.nngroup.com/articles/ten-usability-heuristics/) | Revisión de interacción; heurísticas generales, no evidencia de uso real |
| U02 | [W3C: WCAG 2.2, recomendación de 2024-12-12](https://www.w3.org/TR/2024/REC-WCAG22-20241212/) | Criterios de accesibilidad verificables; no determina la mejor experiencia comercial |
| U03 | [W3C APG: diálogo modal](https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/) | Semántica y manejo de teclado/foco; no reemplaza pruebas con tecnología de asistencia |
| U04 | [GOV.UK: pruebas moderadas de usabilidad](https://www.gov.uk/service-manual/user-research/using-moderated-usability-testing) | Tareas neutrales, usuarios pertinentes y observación |
| U05 | [GOV.UK Design System: resumen de errores](https://design-system.service.gov.uk/components/error-summary/) | Patrón de errores vinculados a campos; referencia, no obligación de copiar su estética |
| U06 | [Design Council: Double Diamond](https://www.designcouncil.org.uk/resources/the-double-diamond/) | Explorar/definir y desarrollar/entregar; no exige un proceso largo para cada ajuste |
| U07 | [Google Research: métricas HEART, CHI 2010](https://research.google/pubs/measuring-the-user-experience-on-a-large-scale-user-centered-metrics-for-web-applications/) | Relacionar objetivos con medidas centradas en personas; no garantiza mejoras ni retorno |
| S01 | [OWASP Top 10:2025](https://top10.owasp.org/2025/) | Riesgos de sensibilización actuales; no lista exhaustiva de controles |
| S02 | [OWASP ASVS, versión estable 5.0.0](https://owasp.org/projects/asvs) | Base de requisitos verificables; versionar identificadores |
| S03 | [Índice de Cheat Sheets para ASVS 5.0.x](https://cheatsheetseries.owasp.org/IndexASVS.html) | Relación de capítulos con guías; no sustituye el texto del requisito numerado |
| S04 | [OWASP API Security Top 10:2023](https://api-security.owasp.org/editions/2023/en/0x11-t10/) | Riesgos por objeto, propiedad, operación, recursos e integraciones |
| S05 | [OWASP WSTG 4.2](https://wstg.owasp.org/v4.2/) | Procedimientos de comprobación versionados; un índice leído no equivale a tests ejecutados |
| S06 | [OWASP SAMM 2.0: modelo](https://owaspsamm.org/model/) | Madurez de prácticas por funciones; no certificación de Ondexia |
| S07 | [NIST SSDF: marco y SP 800-218, versión 1.1 final](https://csrc.nist.gov/projects/ssdf) | Desarrollo seguro según riesgo, costo, viabilidad y recursos |
| S08 | [OWASP: autorización](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html) | Mínimo privilegio, denegación por defecto y controles por solicitud |
| S09 | [OWASP: carga de archivos](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html) | No confiar solamente en Content-Type; controles de contenido, límites y publicación |
| S10 | [OWASP: prevención de XXE](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html) | Endurecimiento específico de procesadores XML y transformaciones |
| S11 | [OWASP: registros](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html) | Eventos útiles sin secretos; integridad, acceso y monitoreo |
| S12 | [OWASP: modelado de amenazas](https://cheatsheetseries.owasp.org/cheatsheets/Threat_Modeling_Cheat_Sheet.html) | Activos, fronteras y amenazas del diseño |
| S13 | [OWASP: aplicaciones multiempresa](https://cheatsheetseries.owasp.org/cheatsheets/Multi_Tenant_Security_Cheat_Sheet.html) | Aislamiento más allá de la base: almacenamiento, caché y contexto |
| S14 | [OWASP: sesiones](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html) | Ciclo de vida y protección de sesión |
| S15 | [Angular: seguridad](https://angular.dev/best-practices/security) | Saneamiento, fronteras de confianza y protecciones del navegador |
| S16 | [Spring Security: CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html) | Evaluar credenciales adjuntadas automáticamente, no solo si el servicio es stateless |
| S17 | [AWS Cognito: verificar JWT](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-verifying-a-jwt.html) | Firma, uso, audiencia, claves rotadas y límites de verificación offline |

## Evidencia local y límites

Se leyeron `CLAUDE.md`, los documentos pertinentes de interfaz, ventas, emisión,
especificaciones previas y seguridad del DTE, y archivos concretos de configuración,
autenticación, carga de marca, XML, sesión y CI. Se usaron búsquedas estáticas para
ubicar controles y posibles brechas; sus resultados son un inventario parcial.

En esta investigación **no se ejecutaron nuevas pruebas funcionales, de penetración,
mutaciones, scanners ni sesiones de usabilidad**. Las nueve pruebas ArchUnit de la
revisión anterior pasaron, pero no prueban los controles investigados aquí.
Las pruebas históricas y el CI verde anterior son evidencia de sus versiones y
alcances, no una certificación de seguridad o accesibilidad.

No se verificaron cabeceras servidas en AWS, políticas IAM efectivas, Cognito real,
contenido de S3, restauración productiva ni SUNAT. No se utilizaron datos personales
reales para los ejemplos. La investigación normativa de privacidad/tributación y
una revisión independiente permanecen pendientes.
