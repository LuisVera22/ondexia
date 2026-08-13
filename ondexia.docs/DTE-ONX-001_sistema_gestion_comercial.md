# DTE-ONX-001 — Documento Técnico de Especificación
## Ondexia — Sistema de gestión comercial con facturación electrónica

---

## §1 Identificación

| Campo | Valor |
|---|---|
| Código | DTE-ONX-001 |
| Versión | 0.11 |
| Estado | **Preliminar** |
| Fecha | 2026-08-11 |
| Producto | Ondexia — Almacén, Compras, Ventas + Facturación Electrónica SUNAT |
| Fase COE QE | Fase 2 · Etapa 2 · Actividad 1 |
| Tech Lead | Desarrollador único (R-13) |
| Arquitecto | Desarrollador único (R-13) |
| Peer review | **PENDIENTE PR — ver 12.1** |

### 1.1 Bloque IORA

| | |
|---|---|
| **Insumos** | [01-dominios](01-dominios.md) · [02-alcance-modulos](02-alcance-modulos.md) |
| **Objetivo** | Definir **cómo** se construye Ondexia: arquitectura, stack, modelo de datos, integraciones SUNAT, seguridad e infraestructura |
| **Resultado** | Decisiones técnicas trazables (DT-01…DT-13) que habilitan el inicio del desarrollo |
| **Alcance** | Los módulos marcados V1 en el documento de alcance §1. No cubre estrategia de pruebas, diseño UX ni planificación de sprints |

### 1.2 Nota de desviación doctrinal

> El proceso COE QE exige un **PRD en estado "Aprobado"** como precondición bloqueante de este artefacto. **No existe PRD**: no se ejecutó la Etapa 1 de descubrimiento (AS-IS, Mapa de Stakeholders, Journeys, Personas, Escenarios, TO-BE).
>
> **Decisión:** se avanza en estado **Preliminar** para no bloquear las decisiones de infraestructura, que tienen plazo propio por el registro de dominios y la homologación ante SUNAT.
>
> **Consecuencias asumidas:**
> - Este DTE **no puede pasar a "Aprobado"** sin PRD y sin al menos una bitácora de peer review formal (§12).
> - Los NFRs de §9 son **propuestos por el equipo técnico**, no derivados de un PRD §7 validado con negocio. Están marcados `[PROPUESTO]`.
> - Los flujos técnicos de §6 se derivan del inventario de módulos del sistema de referencia, no de user stories Must priorizadas con MoSCoW.
> - **Riesgo residual:** construir sobre requisitos no validados. Mitigado parcialmente porque el alcance replica un sistema ya en operación.

---

## §2 Contexto técnico — restricciones heredadas

| ID | Restricción | Origen | Impacto en el diseño |
|---|---|---|---|
| R-01 | Región AWS `us-east-1` | Dominios §7 | Todo el cómputo y datos en una región. Latencia ~60-80 ms a Perú |
| R-02 | Subdominios `app` / `api` / `auth` / `cdn` / `ver` | Dominios §2 | Cinco superficies con políticas de caché, WAF y despliegue distintas |
| R-03 | `ver.ondexia.com` es público, **sin autenticación** | Dominios §5.1 | Superficie anónima aislada del resto. Token opaco de 128 bits por comprobante |
| R-04 | Multiempresa desde V1 | Decisión de negocio | `empresa_id` en toda tabla transaccional. Un certificado digital por RUC |
| R-05 | 5 tipos de comprobante electrónico en V1 | Alcance §3 | Factura, Boleta, Resumen Diario, Liquidación de Compra, GRE |
| R-06 | La GRE usa **API REST con OAuth2**, no el canal CPE | Alcance §3 | Dos integraciones SUNAT distintas, dos modelos de autenticación |
| R-07 | El Resumen Diario es **asíncrono con ticket** | Alcance §3 | Obliga a colas y trabajos en segundo plano desde V1. No es opcional |
| R-08 | Backend Java / Spring Boot | Decisión de equipo | Apache Santuario para XAdES. Ver DT-01 |
| R-09 | Cómputo Lambda + API Gateway | Decisión de equipo | Arranque en frío de la JVM: ver DT-02, es el riesgo técnico principal |
| R-10 | IP de salida fija hacia SUNAT | Dominios §5 | Elastic IP sobre instancia NAT (DT-14). Lambdas en subred privada |
| R-11 | Certificado digital `.pfx` por empresa emisora | Normativa SUNAT | Secrets Manager. Nunca en repositorio ni en imagen |
| R-12 | **Costo de infraestructura mínimo** | Decisión de negocio | Prioridad explícita sobre alta disponibilidad en V1. Determina DT-03 y DT-14. Ver §4.6 |
| R-13 | **Equipo de un solo desarrollador, asistido por IA** | Realidad del proyecto | Reabre DT-12 (ver §7.1). Invalida el peer review convencional (§12). Bus factor = 1 es el riesgo principal del proyecto, por encima de cualquier riesgo técnico |

---

## §3 Arquitectura del sistema

### 3.1 Decisión estructural

**El motor de facturación electrónica es un componente separado del núcleo comercial**, no un módulo dentro de él.

**Decisión técnica DT-13: Separación del motor de facturación**

| Campo | Valor |
|---|---|
| Opciones evaluadas | A) Facturación como paquete dentro del monolito · B) Componente separado con contrato por cola · C) Microservicio independiente con su propia base de datos |
| Criterio de elección | R-06 y R-07 (dos canales SUNAT, uno asíncrono) + la emisión no puede bloquear la venta + el ciclo de vida de la normativa SUNAT es independiente del de negocio |
| **Decisión** | **B — Componente separado, misma base de datos, comunicación por SQS** |
| Consecuencias | El núcleo comercial nunca llama a SUNAT de forma síncrona: publica un evento y sigue. Permite reintentar, reprocesar y absorber caídas de SUNAT sin afectar la venta. Se evita la complejidad de C (transacciones distribuidas, consistencia eventual sobre datos contables) manteniendo una sola base de datos transaccional. **Deuda:** el acoplamiento por base de datos compartida dificulta extraerlo a microservicio si el volumen lo exige |

Razón de fondo: **SUNAT se cae, y con frecuencia**. Si la emisión fuera síncrona dentro de la transacción de venta, cada caída de SUNAT sería una caída de Ondexia. Con la cola de por medio, la venta se registra y el comprobante se emite cuando SUNAT responda.

### 3.2 Diagrama de componentes

> El diagrama de infraestructura vive en [`arquitectura-aws.drawio`](arquitectura-aws.drawio),
> con dos páginas: la **v1** que se despliega ahora y la **arquitectura objetivo** con SUNAT.
> El diagrama de abajo describe componentes lógicos y se mantiene por separado; si divergen,
> manda el `.drawio`.

```coeqe-flow
direction: TB
node CF "CloudFront" [shape=rounded color=gestion]
node APP "App Angular SPA" [shape=box color=gestion]
node VER "Portal publico ver" [shape=box color=gestion]
node AGW "API Gateway" [shape=rounded color=primario]
node COG "Cognito" [shape=box color=primario]
node API "Lambda API Core Spring Boot" [shape=box color=primario]
node DB "RDS PostgreSQL" [shape=box color=fase1]
node SQS "SQS cola de emision" [shape=rounded color=producto]
node EMI "Lambda Emisor firma XAdES" [shape=box color=producto]
node POL "Lambda Poller de ticket" [shape=box color=producto]
node SEC "Secrets Manager certificados" [shape=box color=fase2]
node S3 "S3 XML CDR PDF" [shape=box color=fase1]
node NAT "Instancia NAT IP fija" [shape=rounded color=fase2]
node SUN "SUNAT CPE y GRE" [shape=box color=fase2]
edge CF -> APP
edge CF -> VER
edge APP -> AGW
edge AGW -> COG "autoriza"
edge AGW -> API
edge API -> DB
edge API -> SQS "publica evento"
edge SQS -> EMI "consume"
edge EMI -> SEC "carga certificado"
edge EMI -> S3 "guarda XML firmado"
edge EMI -> NAT
edge NAT -> SUN
edge SUN -> POL "ticket"
edge POL -> DB "actualiza estado"
edge VER -> S3 "lee PDF y XML"
```

### 3.3 Responsabilidades y boundaries

| Componente | Responsabilidad | No hace |
|---|---|---|
| **App Angular** | Interfaz de los tres módulos | No firma, no habla con SUNAT |
| **Portal público** | Consulta anónima de un comprobante por token | No accede a la base transaccional: lee solo de S3 y una tabla de consulta |
| **API Core** | Reglas de negocio, correlativos, stock, persistencia | **No llama a SUNAT.** Publica en SQS y responde |
| **Emisor** | Genera UBL 2.1, firma XAdES, envía a SUNAT, procesa CDR | No conoce reglas de negocio |
| **Poller** | Consulta tickets pendientes del Resumen Diario | No emite |
| **RDS PostgreSQL** | Verdad transaccional | No almacena XML ni PDF (van a S3) |
| **S3** | XML firmado, CDR, PDF. Inmutables | — |

El boundary que importa: **el API Core nunca abre una conexión hacia SUNAT**. Toda comunicación con el exterior fiscal pasa por el Emisor. Eso concentra el manejo de certificados, reintentos e IP fija en un solo componente auditable.

---

## §4 Stack tecnológico

**Decisión técnica DT-01: Lenguaje y framework de backend**

| Campo | Valor |
|---|---|
| Opciones evaluadas | A) Java 21 LTS + Spring Boot · B) Node.js + NestJS · C) C# + .NET · D) Python + FastAPI |
| Criterio de elección | Madurez de XAdES-BES sobre XML UBL 2.1 + experiencia del equipo |
| **Decisión** | **A — Java 21 LTS + Spring Boot** (sustento de la versión en 4.3) |
| Consecuencias | Apache Santuario es la implementación de referencia de XML-DSig; la firma digital deja de ser riesgo técnico y pasa a ser trabajo conocido. Existe ecosistema UBL en Java. **Contra:** es el peor runtime para Lambda por arranque en frío — ver DT-02, que existe únicamente para compensar esto |

**Decisión técnica DT-02: Modelo de cómputo y mitigación de arranque en frío**

| Campo | Valor |
|---|---|
| Opciones evaluadas | A) Lambda + API Gateway con SnapStart · B) ECS Fargate · C) EC2 |
| Criterio de elección | Decisión de equipo (Lambda) + R-07 (trabajos asíncronos) |
| **Decisión** | **A — Lambda + API Gateway, con SnapStart obligatorio en la función API** |
| Consecuencias | Sin SnapStart, un Spring Boot en Lambda arranca en **6-10 s**: inaceptable para una interfaz de punto de venta. SnapStart toma un snapshot del microVM ya inicializado y lo restaura en **200-400 ms**, **sin costo adicional en runtimes Java gestionados**. **SnapStart no es opcional en esta arquitectura, es un requisito.** Exige publicar versiones y apuntar el alias — el `$LATEST` no lo soporta. Ver 4.2 por sus implicancias criptográficas |

### 4.1 Naturaleza del arranque en frío

Conviene precisar el problema, porque determina por qué la solución es SnapStart y no otra:

**Java no es lento en régimen** — una vez caliente, la JVM supera en rendimiento a Node o Python. Lo lento es el **encendido**, y son dos capas que se suman:

1. **La JVM arranca interpretando.** El compilador JIT necesita ejecuciones para optimizar; las primeras invocaciones corren en el modo más lento. Súmese la carga y verificación de clases.
2. **Spring inicializa el contexto en el arranque.** Escaneo del classpath, resolución de dependencias e instanciación de beans, todo antes de atender la primera solicitud.

En una aplicación de larga vida esto se paga una vez y no importa. En Lambda, donde el proceso muere y renace, se paga en cada instancia nueva: 6-10 s.

SnapStart no acelera Java — **se salta ambas capas** tomando una fotografía de la memoria ya inicializada y restaurándola. Por eso la ganancia es tan grande.

**Alternativa descartada:** compilar a imagen nativa con GraalVM (Quarkus, Micronaut o Spring Native) arranca en ~50 ms con menor huella de memoria. Se descarta porque Apache Santuario (DT-06) depende intensamente de reflexión, y la configuración de reflexión para imagen nativa en una ruta criptográfica es frágil y difícil de depurar. SnapStart entrega un beneficio comparable sin ese riesgo.

### 4.2 SnapStart y criptografía — la trampa

SnapStart restaura **el mismo snapshot de memoria** en cada instancia nueva. En una aplicación de firma digital eso es peligroso:

| Riesgo | Por qué | Mitigación obligatoria |
|---|---|---|
| **`SecureRandom` con semilla clonada** | Todas las instancias restauradas comparten el estado del generador capturado en el snapshot. Números "aleatorios" idénticos entre instancias | Re-sembrar en `afterRestore()` del hook CRaC. Nunca cachear una instancia de `SecureRandom` antes del checkpoint |
| **Certificado `.pfx` cacheado en el snapshot** | El material privado quedaría persistido en el snapshot de Lambda | **Cargar el certificado siempre después del restore**, nunca en la inicialización. Es además la política correcta de rotación |
| **Conexiones a base de datos muertas** | Las conexiones del pool no sobreviven al snapshot | Cerrar el pool en `beforeCheckpoint()` y reabrir en `afterRestore()`. **Sin RDS Proxy (DT-03) este hook es crítico**, no una optimización |
| **Tokens OAuth de GRE vencidos** | Un token capturado en el snapshot puede restaurarse ya expirado | Tratar el token como caché con TTL, revalidando siempre tras el restore |

Estos cuatro puntos se implementan vía la interfaz `org.crac.Resource`. **Van al Definition of Done de la primera historia de facturación**, no al final.

**Decisión técnica DT-03: Base de datos** · *revisada en v0.2 por restricción de costo*

| Campo | Valor |
|---|---|
| Opciones evaluadas | A) Aurora Serverless v2 + RDS Proxy · B) RDS PostgreSQL `db.t4g.micro` sin proxy · C) DynamoDB |
| Criterio de elección | Correlativos sin saltos + integridad contable + **costo fijo mínimo (R-12)** |
| **Decisión** | **B — RDS PostgreSQL en `db.t4g.micro`, sin RDS Proxy** |
| Consecuencias | C sigue descartada: los correlativos exigen bloqueo transaccional (F-02) y los reportes exigen `JOIN`. **A se descarta por costo:** Aurora Serverless v2 factura desde 0.5 ACU (~44 USD/mes de piso) y RDS Proxy añade ~22 USD/mes, contra ~14 USD/mes de `t4g.micro` — que además entra en la capa gratuita 12 meses. **Contra:** una sola instancia sin réplica ni Multi-AZ; el respaldo es PITR. Sin proxy, el pool se controla por límite de concurrencia (ver 4.6). Migrar a Aurora después es un cambio de endpoint, no de modelo: la deuda es reversible |

**Decisión técnica DT-14: Salida a internet hacia SUNAT**

| Campo | Valor |
|---|---|
| Opciones evaluadas | A) NAT Gateway administrado · B) Instancia NAT `t4g.nano` con Elastic IP · C) Lambdas fuera de la VPC, sin salida controlada |
| Criterio de elección | R-12 (costo) + mantener IP fija disponible para un eventual allowlist de OSE |
| **Decisión** | **B — Instancia NAT `t4g.nano` con Elastic IP** |
| Consecuencias | El NAT Gateway cuesta ~32 USD/mes de piso: es la línea individual más cara de la arquitectura original y no aporta nada que a este volumen no cubra una instancia de ~3 USD/mes. Conserva la IP fija de R-10. **Contra:** punto único de falla y parcheo a cargo del equipo; si cae, la emisión se detiene (los comprobantes se acumulan en SQS y drenan al restablecerse, sin pérdida). Añadir *endpoint* de tipo gateway para S3 —gratuito— para que el tráfico de documentos no pase por el NAT |

**Decisión técnica DT-04: Framework de la aplicación** · *revisada en v0.4*

| Campo | Valor |
|---|---|
| Opciones evaluadas | A) Angular 22 · B) Vue 3 (Composition API) · C) React SPA *(decisión previa, descartada)* |
| Criterio de elección | Densidad de formularios complejos del dominio + afinidad con un equipo Java/Spring + vida útil larga del producto |
| **Decisión** | **A — Angular con TypeScript estricto** |
| Consecuencias | Ver 4.4 para el sustento. **Contra:** curva de aprendizaje de RxJS, mayor verbosidad y peso inicial de bundle (irrelevante tras login). **Efecto colateral:** la landing deja de ser Next.js, que arrastraba React sin necesidad — ver DT-15 |

**Decisión técnica DT-15: Framework de la landing**

| Campo | Valor |
|---|---|
| Opciones evaluadas | A) Astro · B) Angular Universal · C) HTML + Tailwind sin framework |
| Criterio de elección | Al salir React de la app (DT-04), mantener Next.js en la landing significaría sostener un segundo ecosistema solo para una página de marketing |
| **Decisión** | **A — Astro con salida estática** |
| Consecuencias | Entrega HTML puro sin JavaScript de framework: es lo más rápido posible para SEO y lo más barato de servir en CloudFront. **Contra:** una herramienta más en el equipo, aunque de superficie mínima. B se descarta porque SSR en Angular exige servidor y contradice R-12 |

### 4.3 Política de versiones

**Regla: este documento fija criterios de versión, no números.** Un DTE vive años; los números de versión caducan en meses. Cuando un número aparece, es referencia al momento de redacción y **debe verificarse al arrancar cada corte** (Alcance §6).

| Componente | Criterio | **Versión decidida** |
|---|---|---|
| **Angular** | Última versión estable al iniciar el desarrollo del frontend | **22** |
| **Java** | LTS que soporte el runtime gestionado de Lambda con SnapStart | **21 (LTS)** |
| **Spring Boot** | Versión estable compatible con el Java elegido y con CRaC | Rama estable sobre Java 21 |
| **PostgreSQL** | Versión disponible en RDS con soporte vigente | Más reciente disponible al desplegar |
| **Astro** | Última estable | — |

**Sobre Java 21:** es la elección de menor riesgo, no una concesión. El requisito duro es SnapStart (DT-02), y Java 21 tiene runtime gestionado con soporte probado. Un LTS más nuevo solo aporta si Lambda ya lo ofrece con SnapStart, y llegar antes que el runtime obligaría a empaquetar runtime propio — más trabajo y más riesgo para el único desarrollador (R-13), a cambio de nada que este sistema necesite.

Dos precisiones sobre el criterio:

- **Para Java, la restricción vinculante no es qué LTS publicó Oracle, sino qué runtime ofrece Lambda con SnapStart.** SnapStart es requisito de DT-02, así que un Java sin runtime gestionado compatible queda descartado por más moderno que sea. Verificar en la documentación de Lambda, no en la de Java.
- **Para Angular, arrancar en la última versión es lo correcto en un proyecto nuevo**, sobre todo con un solo desarrollador (R-13): cada mayor que se salta es una migración menos, y Angular sostiene cada versión por un período acotado. Verificar antes que la biblioteca de componentes elegida (Material o PrimeNG) ya publicó su versión compatible — el ecosistema suele ir unas semanas detrás de un lanzamiento mayor.

> **Advertencia sobre la fuente de estos números.** Provienen del conocimiento del asistente que redactó el documento, con fecha de corte propia. **Confírmalos en la documentación oficial antes de fijar dependencias**, especialmente Angular y el runtime de Lambda, que son los que más rápido se mueven.

### 4.4 Angular vs Vue — sustento de DT-04

**1. Formularios reactivos con estructura dinámica.** Es el argumento decisivo. El detalle de una factura es una lista variable de líneas donde cada una tiene validación cruzada: afectación de IGV, cálculo de valor unitario contra precio unitario, redondeo a los decimales que SUNAT valida. Los `FormArray` de Angular modelan exactamente eso, con validadores compuestos y estado de validez propagado, en el núcleo del framework. En Vue hay que ensamblarlo con una librería externa (VeeValidate, FormKit) y mantener esa integración. En un sistema que es esencialmente formularios y tablas, la diferencia es estructural, no de preferencia.

**2. Afinidad con el equipo.** El backend es Java + Spring (DT-01). Angular comparte inyección de dependencias, decoradores, separación por capas y una arquitectura opinada. Un desarrollador de Spring lee un servicio de Angular sin traducción mental. Vue exige adoptar un modelo de composición distinto al que el equipo ya practica en el backend.

**3. Vida útil y disciplina.** Angular impone router, cliente HTTP, formularios y pruebas de forma oficial y versionada en conjunto. En un producto de facturación que va a vivir años y a ser mantenido por gente que hoy no está en el equipo, que el framework imponga la estructura vale más que la libertad de elegirla. Vue deja esas decisiones al equipo: más rápido al inicio, más divergente a los tres años.

**Cuándo Vue habría sido la elección correcta:** con un equipo de uno o dos desarrolladores priorizando velocidad de entrega sobre estructura, o si el equipo ya tuviera experiencia en Vue y ninguna en Angular. Vue exige menos código y se aprende antes; su desventaja aquí es específica del dominio (formularios complejos) y del contexto (equipo Java, producto de larga vida), no de calidad.

**Sobre descartar React:** era la opción con mayor ecosistema y mercado laboral, pero es una biblioteca, no un framework — enrutamiento, formularios y estado se eligen y se mantienen por separado. Para este perfil de producto, esa libertad es costo, no beneficio.

**Componentes de interfaz:** evaluar Angular Material + CDK (tabla virtualizada, accesibilidad resuelta) o PrimeNG (mayor densidad de componentes de datos, más cercano a lo que un ERP necesita). Decisión pendiente para la Etapa 3, no bloquea nada ahora.

**Decisión técnica DT-05: Autenticación**

| Campo | Valor |
|---|---|
| Opciones evaluadas | A) Cognito · B) Keycloak autogestionado · C) Auth0 |
| Criterio de elección | Costo, multiempresa (R-04), MFA |
| **Decisión** | **A — Cognito con dominio propio `auth.ondexia.com`** |
| Consecuencias | Integración nativa con API Gateway (authorizer sin código), MFA incluido. **Contra:** la personalización de la interfaz de login es limitada, y salir de Cognito es costoso — los usuarios se exportan, los hashes de contraseña no, así que migrar obliga a restablecer credenciales a todos. Mitigado porque `cognito_sub` es la única clave foránea y la tabla `usuario` es la fuente de verdad. La pertenencia a empresa y los permisos finos **no** viven en Cognito: viven en la base (ver §5.2 y §8.1) |
| Corrección 0.8 | La versión 0.7 afirmaba «50 000 usuarios activos gratis». **AWS modificó el modelo de capa gratuita**; ese número ya no aplica. Verificar el nivel vigente antes de usarlo en cualquier proyección. Al volumen previsto el costo sigue siendo despreciable, pero el dato concreto estaba caduco |

**Decisión técnica DT-16: Infraestructura como código — Terraform en vez de CDK**

| Campo | Valor |
|---|---|
| Opciones evaluadas | A) AWS CDK en TypeScript · B) Terraform · C) SAM / CloudFormation a mano |
| Criterio de elección | Legibilidad del cambio antes de aplicarlo, modos de fallo con un solo operador |
| **Decisión** | **B — Terraform** |
| Sustituye a | La decisión de las versiones 0.1 a 0.8, que fijaba CDK |
| Sustento | El `plan` de Terraform enumera exactamente qué se crea, modifica y destruye antes de tocar nada, y eso pesa más cuando no hay un segundo par de ojos. CDK sintetiza CloudFormation, y los fallos de CloudFormation —pilas atascadas en `UPDATE_ROLLBACK_FAILED`, recursos huérfanos tras un rollback fallido— exigen intervención manual que con R-13 no tiene quien la cubra |
| Consecuencias | **A favor:** estado explícito y auditable; cobertura de recursos que CloudFormation tarda en incorporar. **En contra:** empaquetar el artefacto de Lambda deja de ser automático y pasa a ser un paso del pipeline; se pierde `cdk-nag` y hay que sustituirlo por `tfsec` o `checkov` (§8.3); **el estado pasa a ser un activo crítico** — vive en S3 cifrado y versionado, y perderlo obliga a importar los recursos a mano |
| Verificación | `fmt`, `init` y `validate` pasan sobre `ondexia.infra/` y su `bootstrap/`. **No se ha ejecutado `plan` ni `apply` contra una cuenta real** |

**Decisión técnica DT-17: Arquitectura interna del backend**

| Campo | Valor |
|---|---|
| Opciones evaluadas | A) Monolito modular con hexagonal pragmática · B) Hexagonal estricta con dominio POJO puro · C) Capas clásicas de Spring agrupadas por tipo técnico |
| Criterio de elección | Que la estructura siga siendo legible a los 23 submódulos del alcance, con un solo desarrollador (R-13) |
| **Decisión** | **A — paquete por dominio; dentro de cada uno, `dominio` / `aplicacion` / `infraestructura` / `web`** |
| Sustento | C es lo más rápido de arrancar y no sobrevive al alcance: un paquete `service` con sesenta clases no deja ninguna frontera que impida que Ventas llame directo al repositorio de Almacén. B duplica unas setenta clases de modelo y sus mapeadores a cambio de una independencia del motor que no vamos a ejercer — RLS (§5.1) ya nos ata a PostgreSQL a propósito |
| Consecuencias | Las entidades llevan anotaciones de JPA y viven en `ondexia-domain`, compartidas con el motor de facturación. **Lo que sí se conserva de hexagonal es que el dominio no conoce HTTP, Spring Web ni seguridad**, y que las dependencias apuntan hacia adentro. **Deuda:** si algún día hiciera falta un modelo de dominio independiente de la persistencia, el cambio es caro |
| Detalle | Ver `03-estructura-repositorio.md` §4.1 a §4.3 |

**Decisión técnica DT-18: Versión de Spring Boot — la rama 4.0**

| Campo | Valor |
|---|---|
| Opciones evaluadas | A) Spring Boot 4.0.7 · B) Spring Boot 4.1.0 · C) Spring Boot 3.5.x |
| **Decisión** | **A — 4.0.7** |
| Sustento | C queda fuera del soporte abierto en breve. B se publicó sin ningún parche detrás, y el ecosistema —springdoc, integraciones de pruebas— suele ir semanas por detrás de cada versión menor. 4.0.7 es la misma mayor con siete parches de rodaje |
| Consecuencias | **Spring Boot 4 reorganizó los módulos y buena parte de los ejemplos publicados ya no compilan.** Lo verificado en este proyecto: `spring-boot-starter-aop` desapareció (ahora `-aspectj`); `flyway-core` a secas **no trae autoconfiguración y no migra nada** — hay que usar `spring-boot-starter-flyway`; `@EntityScan` se movió a `org.springframework.boot.persistence.autoconfigure`; y `@AutoConfigureMockMvc` salió de `spring-boot-starter-test` a `spring-boot-starter-webmvc-test`. Ninguno de los cuatro da un error legible: dos fallan como «símbolo no encontrado» y el de Flyway aparece mucho después, como un `missing table` de la validación de esquema de Hibernate |
| Verificación | Suite de 15 pruebas de integración contra PostgreSQL 17 real, en verde |

**Decisión técnica DT-06: Firma digital XAdES**

| Campo | Valor |
|---|---|
| Opciones evaluadas | A) Apache Santuario directo · B) Librería UBL de terceros · C) Delegar la firma a un OSE |
| Criterio de elección | Control sobre el formato exacto que SUNAT valida + independencia de proveedor |
| **Decisión** | **A — Apache Santuario, firma enveloped sobre el nodo `ExtensionContent`** |
| Consecuencias | Control total y sin costo por comprobante. **Contra:** cada detalle del canónico y del digest es responsabilidad nuestra; los errores de firma son la causa más común de rechazo en homologación. Presupuestar tiempo de homologación real |

**Decisión técnica DT-12: Canal de envío — directo a SUNAT vs OSE**

| Campo | Valor |
|---|---|
| Opciones evaluadas | A) SEE del contribuyente, envío directo a SUNAT · B) A través de un OSE · C) A través de un PSE |
| Criterio de elección | Ondexia **es** el producto de facturación; tercerizar el núcleo lo convierte en un revendedor |
| **Decisión** | **A — Envío directo a SUNAT** |
| Consecuencias | Sin costo por comprobante y sin dependencia de un tercero para el margen del producto. **Contra:** la homologación ante SUNAT es responsabilidad propia y el soporte de incidencias también. **Esta decisión requiere confirmación explícita del sponsor** — es la de mayor impacto comercial del documento |

### 4.5 Resumen del stack

| Capa | Tecnología |
|---|---|
| Landing | Astro (salida estática) · S3 + CloudFront |
| App | Angular 22 + TypeScript estricto · S3 + CloudFront |
| API | Java 21 LTS · Spring Boot · Lambda con SnapStart · API Gateway HTTP API |
| Asíncrono | SQS (estándar + FIFO donde aplique) · EventBridge Scheduler |
| Datos | RDS PostgreSQL · `db.t4g.micro` · sin proxy (concurrencia acotada) |
| Red | VPC con subred privada · instancia NAT `t4g.nano` + Elastic IP · endpoint gateway S3 |
| Documentos | S3 con versionado y Object Lock |
| Secretos | Secrets Manager (certificados, credenciales SOL) · SSM Parameter Store (endpoints) |
| Identidad | Cognito |
| Firma | Apache Santuario (XAdES-BES) |
| PDF | OpenPDF o JasperReports en Lambda dedicada |
| IaC | **Terraform** (ver DT-16) |
| Observabilidad | CloudWatch Logs · X-Ray · alarmas sobre la DLQ |

### 4.6 Perfil de costo

**SnapStart no tiene costo adicional en los runtimes Java gestionados.** La mitigación del arranque en frío (DT-02) es gratis: el costo de esta arquitectura no está en el cómputo, y **ninguna de las decisiones de costo relevantes tiene relación con haber elegido Java**.

La tabla corresponde a la **Fase 2** del plan de despliegue (§10.3). Las fases 0 y 1 cuestan prácticamente cero y son donde arranca el proyecto.

Estimación de orden de magnitud en `us-east-1`, volumen inicial bajo. **Verificar en la calculadora de AWS antes de comprometer presupuesto** — y confirmar qué esquema de capa gratuita aplica a la cuenta, porque AWS modificó el modelo y las cuentas nuevas ya no reciben necesariamente los 12 meses clásicos.

| Concepto | Año 1 (capa gratuita) | Estado estable |
|---|---|---|
| Lambda (API + Emisor + Poller) | 0 | ~0 — 1 M solicitudes/mes siempre gratis |
| SnapStart | 0 | **0** — sin cargo en Java |
| API Gateway HTTP API | 0 | ~1 |
| RDS `db.t4g.micro` + 20 GB | 0 | ~14 |
| Instancia NAT `t4g.nano` | ~3 | ~3 |
| S3 (XML, CDR, PDF) | ~0.5 | ~1 |
| SQS | 0 | 0 — 1 M/mes siempre gratis |
| CloudFront | 0 | 0 — nivel siempre gratuito |
| Cognito | 0 | 0 al volumen previsto |
| Secrets Manager (**1 secreto por empresa**) | ~0.4 × empresas | ~0.4 × empresas |
| **WAF** (1 Web ACL + 3 reglas) | ~8 | ~8 |
| **IP pública IPv4 de la NAT** | ~3.65 | ~3.65 |
| Route 53 (2 zonas) + dominio | ~1.6 | ~1.6 |
| CloudWatch | ~0.5 | ~2 |
| **Total aproximado USD/mes** | **~30** | **~40** |

Contra el diseño original (Aurora Serverless v2 + RDS Proxy + NAT Gateway), el piso baja de ~96 a ~40 USD/mes.

**Tres partidas se corrigieron en la versión 0.8** — la tabla anterior daba ~24 y las omitía:

- **WAF.** Protege el portal público (R-03) y no estaba costeado. Verificado en la tarifa
  vigente: 5 USD por Web ACL, 1 USD por regla, 0.60 USD por millón de solicitudes. Es la
  segunda partida fija después de RDS. **Es aplazable**: hasta que el portal tenga tráfico
  que merezca ser atacado, se puede vivir sin él.
- **IP pública IPv4.** La tabla contaba solo el cómputo de la instancia NAT. AWS cobra
  todas las IPv4 públicas a 0.005 USD/hora sin franja gratuita, y la NAT necesita una por
  definición: su razón de ser es dar una IP estable a SUNAT.
- **Los secretos escalan por empresa.** `secret_arn_certificado` es por RUC (§5.2), no dos
  en total. Son 0.40 USD por cada cliente que entra. Con 100 empresas son 40 USD/mes.
  Alternativa a evaluar cuando pese: Parameter Store avanzado a 0.05 USD por parámetro,
  a cambio de perder la rotación gestionada — irrelevante para un certificado que se
  renueva a mano una vez al año.

**La capa gratuita cambió y la columna «Año 1» ya no aplica a cuentas nuevas.** AWS
sustituyó los 12 meses clásicos por un plan de **créditos: hasta 200 USD a consumir en
6 meses**, y la cuenta del plan gratuito **se cierra sola** al agotarse los créditos o al
vencer el periodo. RDS no figura entre los servicios siempre gratuitos. Para un producto
con clientes de pago hay que **pasar al plan de pago antes del vencimiento**, no cuando
llegue: una cuenta que se autocierra con datos tributarios dentro no es una opción.

---

### 4.7 Perfil de costo de la v1

La v1 —un cliente, dos usuarios, **sin integración SUNAT**— no despliega la mitad de la
arquitectura, y con ella desaparece la mitad del costo.

| Concepto | USD/mes |
|---|---|
| RDS `db.t4g.micro` + 20 GB, una zona | ~14 |
| CloudWatch Logs, retención 30 días | ~0.50 |
| Route 53, 1 zona | ~0.60 |
| S3, buckets estáticos y de marca | ~0.30 |
| API Gateway HTTP API | ~0.05 |
| CloudFront, Lambda, Cognito, ACM, endpoint S3 de puerta de enlace | 0 |
| **Total aproximado USD/mes** | **~15** |

**RDS es el 90 % de la factura.** Tres palancas, de menor a mayor incomodidad:

1. **Sin RDS en desarrollo.** PostgreSQL en contenedor local; RDS solo en producción.
   Una segunda instancia duplica la factura exacta. Es el ahorro mayor y no cuesta nada.
2. **Parada programada fuera de horario.** Baja a ~8 USD, con dos peros: AWS reinicia sola
   cualquier instancia detenida más de 7 días, y cualquier corte a deshora es un problema
   de servicio ante un cliente que paga.
3. **Instancia reservada a un año**, cuando la permanencia esté confirmada.

**Lo que no cambia con el volumen.** El piso es fijo y el costo marginal por comprobante es
cero. A 5 000 comprobantes/mes son 0.008 USD por comprobante; a 50 000, 0.0008. Es lo que
hace defendible «comprobantes ilimitados» como argumento comercial — y lo que convierte
DT-12 en una decisión de margen, no de arquitectura.

**Controles obligatorios antes del primer despliegue** (además de los de §4.6): presupuesto
de AWS Budgets con alerta al 80 % de un tope de 30 USD.

---

### 4.8 Consecuencias de una Lambda sin salida a internet

En la v1 no hay instancia NAT, porque nada necesita salir. Eso no es gratis: **una Lambda en
subred privada sin NAT no alcanza internet ni las APIs de AWS.** De ahí salen tres
decisiones que hay que respetar o el ahorro se evapora.

**El endpoint de S3 es obligatorio y es gratuito.** Sin un endpoint de puerta de enlace, la
Lambda no puede leer ni escribir en S3. Se crea explícitamente y no cuesta nada.

**Las credenciales de la base van como variables de entorno cifradas con KMS**, no en
Parameter Store ni en Secrets Manager. Lambda las descifra antes de ejecutar el código, sin
tráfico de red. Llamar a esos servicios desde subred privada exigiría un **endpoint de
interfaz a ~0.01 USD/hora (~7.30 USD/mes)** — más caro que la instancia NAT que se evitó.

**La administración de usuarios la hace el SPA contra Cognito, no el backend.** Crear o
desactivar un usuario es una operación de identidad, no de negocio, y hacerla desde la
Lambda obligaría al mismo endpoint de interfaz. Aplica igual a cualquier servicio externo
que se sume después: enviar correo por SES desde la Lambda reabre el problema.

Controles para que no se dispare:

- **Límite de concurrencia reservada** en la Lambda API (sugerido: 20) y `HikariCP` con `maximum-pool-size: 2`. Sustituye al RDS Proxy y además acota el gasto ante un pico o un bucle accidental.
- **Presupuesto de AWS Budgets con alerta** al 80 % de un tope mensual definido. Obligatorio antes del primer despliegue.
- **Retención de logs de CloudWatch a 30 días.** Los logs sin política de retención son la fuga de costo más común en proyectos pequeños.
- **`dev` apagado fuera de horario.** La instancia RDS de `dev` se detiene por planificador; la capa gratuita cubre una sola instancia, así que dos entornos encendidos siempre ya cuestan.

---

## §5 Modelo de datos

### 5.1 Principios

1. **`empresa_id` en toda tabla transaccional y de catálogo** (R-04). Sin excepción; una tabla sin `empresa_id` es una fuga entre clientes esperando ocurrir.
2. **Row Level Security de PostgreSQL activo** sobre las tablas multiempresa. La defensa no puede depender solo de que el desarrollador recuerde el `WHERE empresa_id = ?`.
3. **Los importes son `NUMERIC(18,6)`**, jamás `float`. SUNAT valida a 2 decimales en totales y hasta 6 en valores unitarios; un error de redondeo es un comprobante rechazado.
4. **Los documentos emitidos son inmutables.** No se actualizan: se anulan con otro documento.
5. **Las unidades de medida mapean al catálogo SUNAT nº 03**, los tipos de documento al nº 01 y las monedas al nº 02. Los catálogos son tablas, no enumeraciones en código: SUNAT los actualiza.

### 5.2 Entidades núcleo

| Entidad | Campos clave | Constraints e índices |
|---|---|---|
| `empresa` | `id`, `ruc` (UNIQUE), `razon_social`, `nombre_comercial`, `domicilio_fiscal`, `secret_arn_certificado`, `usuario_sol`, `modo_sunat` (beta/prod) | `ruc` único global. `secret_arn_certificado` es una referencia, **nunca el certificado** |
| `sucursal` | `empresa_id`, `codigo`, `direccion`, `ubigeo` | UNIQUE(`empresa_id`,`codigo`) |
| `cuenta` | `id`, `nombre`, `plan`, `estado_suscripcion`, `permisos_version` | La **suscripción cuelga de la cuenta, no de la empresa**: una cuenta puede tener varios RUC. `permisos_version` se incrementa al tocar cualquier rol y permite cachear permisos en memoria del contenedor sin servirlos rancios |
| `usuario` | `cuenta_id`, `cognito_sub` (UNIQUE), `email`, `nombre`, `activo` | Pertenece a una cuenta. `cognito_sub` es la **única** referencia al proveedor de identidad |
| `cuenta_administrador` | `cuenta_id`, `usuario_id` | Quien factura, crea empresas, asigna usuarios y gestiona roles. **Constraint: no puede quedar vacía.** No se modela como rol de la matriz por tres razones: existe antes que cualquier empresa, gobierna la facturación —que no es un módulo—, y necesita ese invariante que la matriz no puede expresar |
| `usuario_empresa` | `usuario_id`, `empresa_id`, `rol_id`, `sucursal_id` (NULL = todas) | UNIQUE(`usuario_id`,`empresa_id`,`sucursal_id`). El alcance por sucursal es lo que permite «Ventas solo en Miraflores»: quien atiende un local no debe poder cambiarse a otro, porque eso decide la serie del comprobante y qué almacén descarga |
| `rol` / `permiso` / `rol_permiso` | `codigo`, `modulo`, `accion` | Permiso por módulo **y acción**: registrar ≠ aprobar ≠ anular |
| `auditoria` | `empresa_id`, `usuario_id`, `entidad`, `entidad_id`, `accion`, `datos_antes` (JSONB), `datos_despues`, `ip`, `creado_en` | Índice en (`empresa_id`,`entidad`,`entidad_id`). **Append-only**, sin UPDATE ni DELETE |

### 5.3 Almacén

| Entidad | Campos clave | Constraints |
|---|---|---|
| `marca` | `empresa_id`, `nombre` | UNIQUE(`empresa_id`,`nombre`) |
| `modelo` | `empresa_id`, `marca_id`, `nombre` | UNIQUE(`empresa_id`,`marca_id`,`nombre`) |
| `unidad_medida` | `codigo_sunat` (cat. 03), `nombre`, `abreviatura` | Catálogo global, no por empresa |
| `producto` | `empresa_id`, `codigo` , `nombre`, `marca_id`, `modelo_id`, `unidad_id`, `tipo_afectacion_igv`, `stock_minimo` | UNIQUE(`empresa_id`,`codigo`). Índice GIN sobre `nombre` para búsqueda |
| `presentacion` | `producto_id`, `descripcion`, `factor_conversion`, `codigo_barras` | `factor_conversion > 0`. Una presentación es marcada `es_base` |
| `tipo_precio` | `empresa_id`, `nombre`, `orden` | Ej. mayorista, minorista, distribuidor |
| `precio_producto` | `producto_id`, `presentacion_id`, `tipo_precio_id`, `valor`, `moneda`, `vigente_desde` | UNIQUE(`producto_id`,`presentacion_id`,`tipo_precio_id`,`vigente_desde`) |
| `almacen` | `empresa_id`, `sucursal_id`, `nombre` | |
| `stock` | `almacen_id`, `producto_id`, `presentacion_id`, `cantidad` | UNIQUE(`almacen_id`,`producto_id`,`presentacion_id`). `cantidad >= 0` salvo config |
| `movimiento_stock` | `almacen_id`, `producto_id`, `cantidad`, `tipo`, `documento_origen_tipo`, `documento_origen_id`, `creado_en` | **Libro mayor de inventario: append-only.** `stock` es su proyección |
| `guia_ingreso` | `empresa_id`, `almacen_id`, `proveedor_id`, `numero`, `fecha` | Documento interno |
| `guia_remision` | `empresa_id`, `serie`, `numero`, `tipo` (09/31), `punto_partida`, `punto_llegada`, `transportista`, `estado_sunat` | UNIQUE(`empresa_id`,`serie`,`numero`) |

> **"Productos por agotarse" no es una entidad.** Es la consulta `stock.cantidad <= producto.stock_minimo`. Construirla como tabla es duplicar estado que se desincroniza.

### 5.4 Terceros

| Entidad | Campos clave | Constraints |
|---|---|---|
| `cliente` | `empresa_id`, `tipo_documento` (cat. 06), `numero_documento`, `razon_social`, `direccion`, `email` | UNIQUE(`empresa_id`,`tipo_documento`,`numero_documento`) |
| `proveedor` | Misma forma que cliente | Idéntico constraint |

Cliente y proveedor se mantienen **separados** pese a compartir forma: sus ciclos de vida, validaciones y permisos difieren, y un mismo RUC puede ser ambos con datos distintos.

### 5.5 Ventas y comprobantes

Decisión central del modelo: **cabecera unificada con discriminador de tipo**, no una tabla por tipo de documento.

| Entidad | Campos clave | Constraints |
|---|---|---|
| `serie_correlativo` | `empresa_id`, `sucursal_id`, `tipo_documento`, `serie`, `ultimo_numero` | UNIQUE(`empresa_id`,`tipo_documento`,`serie`). **Fuente única del correlativo** |
| `documento_venta` | `empresa_id`, `tipo_documento` (01/03/07/08), `serie`, `numero`, `cliente_id`, `fecha_emision`, `moneda`, `tipo_cambio`, `total_gravado`, `total_igv`, `total_exonerado`, `total_inafecto`, `total_venta`, `forma_pago`, `estado` | UNIQUE(`empresa_id`,`tipo_documento`,`serie`,`numero`) |
| `documento_venta_detalle` | `documento_id`, `producto_id`, `presentacion_id`, `cantidad`, `valor_unitario`, `precio_unitario`, `tipo_afectacion_igv`, `igv`, `total` | `cantidad > 0` |
| `documento_referencia` | `documento_id`, `tipo_doc_referencia`, `serie_ref`, `numero_ref`, `motivo` (cat. 09/10) | Obligatorio para notas de crédito y débito |
| `comprobante_electronico` | `documento_id` (UNIQUE), `estado_sunat`, `xml_s3_key`, `cdr_s3_key`, `pdf_s3_key`, `hash_firma`, `token_publico` (UNIQUE), `ticket`, `codigo_respuesta`, `descripcion_respuesta`, `intentos`, `enviado_en` | `token_publico` de 128 bits generado con CSPRNG (Dominios §5.1) |
| `resumen_diario` | `empresa_id`, `fecha_referencia`, `correlativo`, `ticket`, `estado`, `xml_s3_key`, `cdr_s3_key` | UNIQUE(`empresa_id`,`fecha_referencia`,`correlativo`) |
| `cotizacion` / `nota_preventa` | `empresa_id`, `numero`, `cliente_id`, `validez_hasta`, `estado` | Documentos internos, sin serie fiscal |

**Estados de `comprobante_electronico`:** `PENDIENTE → FIRMADO → ENVIADO → ACEPTADO | RECHAZADO | OBSERVADO | ERROR_ENVIO → ANULADO`. La transición es una máquina explícita, no un campo de texto libre.

### 5.6 Compras

| Entidad | Campos clave | Notas |
|---|---|---|
| `nota_pedido` | `empresa_id`, `numero`, `proveedor_id`, `estado` | Interno |
| `orden_compra` | `empresa_id`, `numero`, `proveedor_id`, `nota_pedido_id`, `moneda`, `total`, `estado` | Interno. Estado incluye aprobación |
| `orden_servicio` | Misma forma, sin detalle de productos | **No genera movimiento de stock** |
| `nota_compra` | `empresa_id`, `numero`, `orden_compra_id` | Interno |
| `factura_compra` | `empresa_id`, `proveedor_id`, `tipo_documento`, `serie`, `numero`, `fecha_emision`, `total`, `orden_compra_id`, `estado_validacion_sunat` | UNIQUE(`empresa_id`,`proveedor_id`,`tipo_documento`,`serie`,`numero`). **Recibida, no emitida** |
| `liquidacion_compra` | `empresa_id`, `serie`, `numero`, `proveedor_id`, `estado_sunat` | **Emitida por nosotros (tipo 04)** → reutiliza `comprobante_electronico` |

### 5.7 Diagrama de relaciones núcleo

```coeqe-flow
direction: LR
node EMP "empresa" [shape=box color=primario]
node USR "usuario_empresa" [shape=box color=primario]
node PRO "producto" [shape=box color=fase1]
node STK "stock" [shape=box color=fase1]
node MOV "movimiento_stock" [shape=box color=fase1]
node CLI "cliente" [shape=box color=gestion]
node DOC "documento_venta" [shape=box color=producto]
node DET "documento_detalle" [shape=box color=producto]
node CPE "comprobante_electronico" [shape=box color=fase2]
node SER "serie_correlativo" [shape=box color=fase2]
edge EMP -> USR
edge EMP -> PRO
edge PRO -> STK
edge MOV -> STK "proyecta"
edge EMP -> CLI
edge CLI -> DOC
edge DOC -> DET
edge DET -> MOV "descarga stock"
edge DOC -> CPE "1 a 1"
edge SER -> DOC "asigna numero"
```

---

### 5.8 Política de almacenamiento

El criterio **no es quién produjo el archivo, sino si se puede reconstruir** y si sus bytes
exactos tienen valor probatorio. Un PDF de kardex lo produce Ondexia y no vale nada
guardarlo.

| | Qué es | Qué se hace | Ejemplos |
|---|---|---|---|
| **A** | Reproducible sin pérdida desde los datos | **No se almacena** | PDF de cotización, nota de preventa, orden de compra y de servicio, kardex, reportes, listados, y la representación impresa de cualquier comprobante — SUNAT considera auténtico el XML, no el papel |
| **B** | Su valor está en los bytes exactos | **Inmutable, retención legal** | XML firmado del comprobante, del resumen diario y de la comunicación de baja |
| **C** | Lo produjo un tercero | **Inmutable, retención legal** | CDR de SUNAT, ticket del resumen diario, y el XML y CDR de comprobantes **recibidos** de proveedores |
| **D** | Insumo para reproducir la categoría A | **Versionado, nunca sobrescrito** | Logo e imágenes de marca por empresa |

**La prueba, en tres preguntas.** ¿Se reconstruye byte a byte desde los datos? No se guarda.
¿Su valor está en la firma, o lo produjo alguien ajeno? Inmutable. ¿Sirve para reconstruir
algo de la categoría A? Versionado. Lo que no cae en ninguna, no entra.

**Por qué B no admite excepción.** La firma XAdES cubre bytes exactos. Volver a serializar
los mismos datos cambia el orden de los atributos, los espacios en blanco o los prefijos de
espacios de nombres; la firma deja de validar y lo que queda no es el documento que SUNAT
aceptó, sino otro parecido. El CDR ni siquiera es nuestro.

**Por qué D tampoco es mutable.** Si un cliente reemplaza su logo y se regenera el PDF de
una cotización del año pasado, se reescribe la historia en silencio: sale un documento que
nunca existió así. Cada documento registra **con qué versión de logo se compuso**.

**La consecuencia que alcanza al modelo, no al almacenamiento.** Renunciar a guardar el
archivo obliga a congelar el registro: si se envía una cotización y después alguien edita
sus líneas, regenerar produce un papel distinto del que recibió el cliente. **O se congela
el archivo, o se congela el registro.** Ondexia elige lo segundo, que es más limpio y más
barato, y eso extiende el principio de §5.1 —los documentos emitidos son inmutables— a
**todo documento entregado a un tercero**: corregirlo genera una versión nueva, la anterior
queda como estaba.

**Sin adjuntos.** Ondexia no es un repositorio documental. Aceptar archivos ajenos —el
escaneo de la factura del proveedor es la petición que aparecerá en Compras— arrastra
custodia, retención, análisis antivírico y responsabilidad sobre contenido que no
producimos. Los comprobantes electrónicos recibidos son otra cosa: son documentos
tributarios con estructura conocida que el sistema valida, y entran por la categoría C.

**El único archivo que se acepta subir es el logo, y trae un riesgo real.** Un SVG puede
contener JavaScript; servido desde el origen de la aplicación es XSS almacenado, con el
vector «suba su logo». Tres controles: aceptar solo PNG y JPG **verificando los bytes de
cabecera**, no la extensión ni el `Content-Type` que pone el cliente; si algún día se admite
SVG, sanearlo en el servidor eliminando `script`, `foreignObject` y atributos `on*`; y en
todo caso **servir estos archivos desde `cdn.ondexia.com`**, nunca desde el origen de la
aplicación ni con el bucket público (dominios §3).

**Optimización para más adelante.** B y C se escriben una vez y se leen casi nunca —solo en
una fiscalización—. Cuando el volumen lo justifique, una regla de ciclo de vida hacia una
clase de acceso infrecuente recorta el costo. Hoy son céntimos.

---

## §6 Flujos técnicos

### F-01 · Emisión de factura o boleta

El punto crítico: **SUNAT puede tardar más de 30 segundos en responder, y API Gateway corta a los 29**. La emisión no puede ser síncrona de extremo a extremo.

```coeqe-flow
direction: TB
node A "Usuario confirma venta" [shape=rounded color=gestion]
node B "API valida y asigna correlativo" [shape=box color=primario]
node C "Persiste documento estado PENDIENTE" [shape=box color=primario]
node D "Publica en SQS y responde 202" [shape=box color=primario]
node E "Emisor genera UBL 2.1" [shape=box color=producto]
node F "Firma XAdES con certificado" [shape=box color=producto]
node G "Envia a SUNAT por IP fija" [shape=box color=fase2]
node H "Respuesta SUNAT" [shape=diamond color=fase2]
node I "Guarda CDR y marca ACEPTADO" [shape=box color=fase1]
node J "Marca RECHAZADO y notifica" [shape=box color=producto]
node K "Reintento con backoff o DLQ" [shape=box color=producto]
edge A -> B
edge B -> C
edge C -> D
edge D -> E
edge E -> F
edge F -> G
edge G -> H
edge H -> I "aceptado"
edge H -> J "rechazado 2xxx"
edge H -> K "error de red o 0100"
```

Puntos de diseño:

- **La respuesta al usuario es inmediata** (`202 Accepted` con el id del documento). La interfaz muestra el estado y lo actualiza por polling o WebSocket.
- **Distinguir rechazo de fallo de comunicación.** Un código `2xxx` de SUNAT es un rechazo definitivo del comprobante: reintentarlo es inútil. Un timeout o un `0100` es un fallo transitorio: reintentar con backoff exponencial. Confundirlos genera duplicados o comprobantes perdidos.
- **Idempotencia obligatoria.** SQS estándar garantiza entrega *al menos una vez*: el Emisor puede recibir el mismo mensaje dos veces. La clave de idempotencia es `(empresa_id, tipo, serie, numero)`, verificada antes de enviar.
- **La DLQ tiene alarma.** Un mensaje en la cola de mensajes fallidos es un comprobante que el cliente cree emitido y no lo está. Es incidente, no métrica.

### F-02 · Asignación de correlativo (concurrencia)

SUNAT no tolera correlativos duplicados y penaliza los saltos. Con Lambda concurrente, dos ventas simultáneas pueden pedir el mismo número.

```sql
BEGIN;
SELECT ultimo_numero FROM serie_correlativo
 WHERE empresa_id = ? AND tipo_documento = ? AND serie = ?
 FOR UPDATE;                      -- bloqueo de fila, serializa a los concurrentes
UPDATE serie_correlativo SET ultimo_numero = ultimo_numero + 1 WHERE ...;
INSERT INTO documento_venta (...) VALUES (...);
COMMIT;                           -- correlativo y documento, o ninguno
```

- **No usar `SEQUENCE` de PostgreSQL:** no es transaccional y deja huecos ante cualquier rollback.
- **El correlativo se asigna al confirmar, nunca al abrir un borrador.** Un borrador abandonado con número asignado es un hueco permanente en la numeración.
- El bloqueo es por `(empresa, tipo, serie)`, así que ventas de series distintas no se serializan entre sí.

### F-03 · Resumen Diario de boletas

Es el flujo asíncrono en dos tiempos que obliga a tener planificador desde V1.

```coeqe-flow
direction: LR
node A "EventBridge dispara" [shape=rounded color=gestion]
node B "Agrupa boletas del dia por empresa" [shape=box color=primario]
node C "Genera y firma XML RC" [shape=box color=producto]
node D "Envia y recibe TICKET" [shape=box color=fase2]
node E "Guarda ticket estado PENDIENTE" [shape=box color=fase1]
node F "Poller consulta el ticket" [shape=box color=producto]
node G "Estado" [shape=diamond color=fase2]
node H "Marca boletas ACEPTADO" [shape=box color=fase1]
node I "Registra observaciones" [shape=box color=producto]
edge A -> B
edge B -> C
edge C -> D
edge D -> E
edge E -> F "cada 5 min"
edge F -> G
edge G -> H "procesado"
edge G -> F "en proceso"
edge G -> I "con errores"
```

El envío **no devuelve el CDR**: devuelve un ticket que se consulta después. El poller corre por EventBridge Scheduler cada 5 minutos y abandona tras N intentos escalando a alerta.

### F-04 · Guía de Remisión Electrónica

Canal distinto (R-06). Secuencia: obtener token OAuth2 del endpoint de seguridad → `POST` del comprobante en JSON con el XML firmado embebido en base64 → recibir ticket → consultar estado. El token se cachea con TTL y **se revalida siempre tras un restore de SnapStart** (§4.2).

### F-05 · Consulta en el portal público

```coeqe-flow
direction: LR
node A "Cliente abre ver punto ondexia" [shape=rounded color=gestion]
node B "CloudFront cache" [shape=diamond color=gestion]
node C "Lambda resuelve token" [shape=box color=primario]
node D "Tabla de consulta publica" [shape=box color=fase1]
node E "Entrega PDF XML CDR de S3" [shape=box color=fase1]
edge A -> B
edge B -> E "hit"
edge B -> C "miss"
edge C -> D "busca por token"
edge D -> E
```

El portal **no consulta la base transaccional**: lee una tabla de proyección con solo lo publicable. Aunque un fallo de autorización se colara, no habría nada más que exponer. WAF con límite por IP delante.

### F-06 · Registro de compra con afectación de stock

Orden de Compra aprobada → recepción por Guía de Ingreso → `movimiento_stock` de entrada → registro de Factura de compra → validación del comprobante contra SUNAT. La Orden de Servicio recorre el mismo flujo **sin** generar movimiento de stock.

---

## §7 Integraciones externas

| # | Integración | Protocolo | Autenticación | Manejo de fallos |
|---|---|---|---|---|
| I-01 | **SUNAT CPE** (factura, boleta, NC, ND, liquidación, resumen) | SOAP 1.1 con WS-Security | Usuario SOL (`RUC+usuario`/clave) desde Secrets Manager | Backoff exponencial 1/2/4/8/16 min, máx. 5 intentos → DLQ + alarma. Distinguir rechazo definitivo de fallo transitorio |
| I-02 | **SUNAT GRE** | REST/JSON | OAuth2 client credentials, token con TTL | Igual que I-01, más revalidación de token ante `401` |
| I-03 | **Consulta RUC/DNI** | REST | API key de proveedor | **Degradación elegante:** si no responde, permitir carga manual. Nunca bloquear una venta por una consulta de padrón |
| I-04 | **Amazon SES** | API AWS | IAM | Cola de reintento. Un fallo de correo no invalida el comprobante |

### 7.1 DT-12 reabierta por R-13 — emisión propia vs proveedor

La decisión de enviar directo a SUNAT (DT-12) se tomó cuando el equipo era una incógnita. Con **un solo desarrollador**, el cálculo cambia y hay que rehacerlo explícitamente.

Lo que implica emitir directo, y que recae entero sobre una persona:

| Trabajo | Naturaleza |
|---|---|
| Generación de UBL 2.1 por cada tipo de comprobante | Una vez por tipo, 5 tipos en V1 |
| Firma XAdES correcta al detalle (canónico, digest) | Una vez, pero con depuración larga |
| **Homologación ante SUNAT** | Proceso externo, plazo fuera de tu control |
| Gestión de certificados, vencimientos y rotación | **Permanente** |
| Seguimiento de cambios normativos de SUNAT | **Permanente, indefinido** |

Las dos últimas filas son las que importan: no son trabajo de construcción sino **carga operativa perpetua** sobre la única persona del proyecto.

**Alternativa: delegar la emisión en un proveedor con API REST** (OSE/PSE del mercado peruano). Entregas el comprobante en JSON y el proveedor se encarga de UBL, firma, envío, CDR y actualizaciones normativas.

| | Emisión propia | Vía proveedor |
|---|---|---|
| Esfuerzo inicial | Alto — la parte más incierta del proyecto | Bajo — integración REST convencional |
| Homologación | A tu cargo | Ya resuelta por el proveedor |
| Cambios normativos | A tu cargo, para siempre | Del proveedor |
| Costo | Sin costo por comprobante | Costo variable por comprobante |
| Control y margen | Total | Menor |

**Recomendación revisada:** empezar con proveedor, migrar a emisión propia cuando el volumen lo justifique.

Lo que hace viable esa migración sin castigo es que **la arquitectura ya la contempla**: DT-13 aisló el Emisor como componente separado detrás de una cola. El núcleo comercial no sabe —ni debe saber— si detrás del Emisor está SUNAT o un tercero. Cambiar de uno a otro es reemplazar la implementación de un componente, no rediseñar el sistema.

**Matiz sobre la objeción anterior.** Argumenté que tercerizar convertiría a Ondexia en revendedor. Eso aplica a un producto cuyo negocio *es* la facturación electrónica; Ondexia es un ERP que además emite comprobantes. La mayoría de los ERP del mercado peruano opera exactamente así, y no es lo que los diferencia.

**Decisión pendiente del sponsor.** Si la estrategia comercial es vender facturación como diferenciador, la emisión propia se justifica pese al costo en tiempo.

**Endpoints por entorno** en SSM Parameter Store, nunca en código:

| Entorno | CPE | Certificado |
|---|---|---|
| `dev` | Beta de SUNAT | Certificado de prueba |
| `prod` | Producción | Certificado real del contribuyente |

> Con solo dos entornos (Alcance §3 del documento de dominios), `dev` es el **único** lugar donde se prueba contra beta. Un despliegue que apunte `dev` a producción emite comprobantes legalmente válidos por error. Bloquear por política de IAM: el rol de `dev` no tiene permiso de lectura sobre el secreto del certificado de producción.

---

## §8 Seguridad y cumplimiento

### 8.1 Autenticación y autorización

**La autenticación se compra; la autorización se construye.** Son problemas distintos y la
respuesta es opuesta. Autenticar bien —hash de contraseñas, recuperación con token de un
solo uso, límite de intentos, defensa contra relleno de credenciales, prevención de
enumeración de cuentas, TOTP, rotación de refresh tokens— es meses de trabajo cuyo modo de
fallo es **silencioso**: funciona perfectamente hasta el día en que alguien entra. Autorizar
es lógica de dominio y nadie puede escribirla por nosotros.

- Cognito emite JWT; **API Gateway lo valida de forma nativa**, sin autorizador Lambda. Un
  autorizador propio añadiría un arranque en frío a cada petición y código que mantener,
  y no haría falta: los permisos finos no se resuelven ahí.
- El token porta **identidad y nada más**. La empresa activa y los permisos se resuelven en
  la base **en cada petición**. Un JWT es válido hasta que caduca, y revocar «anular
  comprobante» no puede esperar a la renovación. Además, ~50 submódulos × 3-5 acciones son
  unos 200 permisos: no caben en una cabecera que viaja en cada llamada.
- Autorización por `(modulo, accion)`. Anular un comprobante es un permiso propio, separado
  de emitirlo. **Denegar por defecto**, comprobado en el endpoint. Que el menú del frontend
  oculte lo que no corresponde es comodidad, no seguridad: la API se puede llamar sin pasar
  por el SPA.
- **El contexto que envía el cliente no se cree nunca.** El frontend manda «empresa activa»;
  el servidor verifica en cada petición que ese usuario tiene asignación a esa empresa. No
  es celo de manual: la empresa determina **con qué certificado digital se firma**, y un
  `empresa_id` falsificado no sería solo ver datos ajenos, sería emitir un comprobante
  firmado con el certificado de otro RUC.
- Row Level Security de PostgreSQL como segunda barrera del aislamiento multiempresa.

**Quién puede moverse entre empresas.** El administrador de la cuenta es el único que
**concede** el acceso; cualquier usuario puede **tener** varias empresas si se lo
asignaron. Separarlo así evita el caso que rompe la regla estricta: un contador en planilla
que lleva tres RUC del mismo grupo necesita las tres empresas sin heredar la facturación ni
la gestión de usuarios. El selector de empresa aparece cuando hay más de una asignación, no
cuando el usuario es administrador.

**Grupos de usuarios separados.** El personal de Ondexia —back-office, soporte— vive en un
grupo de usuarios de Cognito **distinto** del de los inquilinos. Con un grupo compartido, un
usuario nuestro y uno de un cliente se diferencian solo por un claim, y un error al
comprobarlo expone a todos los clientes. Con grupos separados ese error deja de ser
posible. **Crear el grupo del personal desde el principio**, aunque el back-office no
exista: migrar identidades en producción es caro.

> **Trampa de RLS con Lambda — la más peligrosa del diseño.**
> RLS necesita una variable de sesión con el inquilino actual, y Lambda **reutiliza
> conexiones** entre invocaciones. Si se fija y no se restablece, la petición del cliente B
> puede ejecutarse bajo el inquilino del cliente A: exactamente la fuga que RLS venía a
> evitar. Se fija **dentro de la transacción**, siempre, y nunca se asume que quedó limpia.

**Implementado (0.9).** `GestorTransaccionesConAislamiento` sustituye al gestor de transacciones estándar, de modo que **toda** transacción de la aplicación pasa por él: el aislamiento no depende de que nadie recuerde anotar nada. Fija la variable con `set_config(..., true)` —válida solo dentro de la transacción y revertida por el motor al confirmar o deshacer—, así que la limpieza no la hace nuestro código y no se puede olvidar. Las políticas usan `nullif(current_setting(...), '')::uuid`: sin contexto, ninguna fila pasa. **Falla cerrado.**

> **Segunda trampa de RLS, descubierta al probarlo — y peor que la primera.**
>
> **Un rol con `SUPERUSER` o `BYPASSRLS` no está sujeto a ninguna política**, y
> `FORCE ROW LEVEL SECURITY` tampoco le alcanza: `FORCE` solo afecta al *propietario* de la
> tabla, no al superusuario. El resultado es el peor estado posible de un control de
> seguridad — las políticas existen, `pg_policies` las lista, el código es correcto, y no
> filtran absolutamente nada. Sin error y sin aviso.
>
> No es hipotético: la imagen oficial de PostgreSQL crea `POSTGRES_USER` como superusuario,
> así que el entorno de desarrollo por omisión *tiene* el problema. Y no se arregla
> degradándolo, porque PostgreSQL lo prohíbe: *«the bootstrap superuser must have the
> SUPERUSER attribute»*.
>
> **Consecuencia de diseño:** la aplicación se conecta siempre con un rol propio que es
> dueño de su base pero no del clúster. En local lo crea un script de `initdb`; en RDS el
> usuario maestro ya cumple. Y `ComprobacionAislamiento` **aborta el arranque** si detecta
> un rol que puede saltarse las políticas — un despliegue mal configurado no levanta, en
> lugar de levantar pareciendo protegido.
>
> Esto se detectó porque las pruebas de aislamiento fallaron. Sin ellas habría llegado a
> producción sin síntoma alguno. Es el argumento más concreto de este documento a favor de
> probar los controles de seguridad en vez de darlos por implementados.

**Qué queda fuera de RLS, y por qué.** `empresa`, `sucursal` y `usuario_empresa` no llevan
política: son las tablas que hay que leer **para saber** cuál es la empresa activa, así que
una política que dependa de esa misma respuesta las dejaría vacías siempre. Su control
compensatorio es que solo se consultan filtrando por el `usuario_id` que sale del token
—nunca por un valor que mande el cliente— y esa consulta vive en un único sitio,
`ResolutorContexto`. La aplicación avisa en cada arranque si aparecen otras tablas con
`empresa_id` sin política.

### 8.2 Certificados digitales

| Control | Implementación |
|---|---|
| Almacenamiento | Secrets Manager, un secreto por empresa, cifrado con clave KMS propia |
| Acceso | Solo el rol de ejecución del Emisor. El API Core **no tiene permiso** |
| En memoria | Cargado tras el restore de SnapStart, nunca en la inicialización (§4.2) |
| Rotación | Alarma a 60 días del vencimiento. Un certificado vencido detiene toda la facturación del cliente |
| Auditoría | CloudTrail sobre cada acceso al secreto |

### 8.3 OWASP Top 10 — controles

| Riesgo | Control |
|---|---|
| Control de acceso roto | RLS + autorización por acción + pruebas de aislamiento entre empresas en CI |
| Fallas criptográficas | TLS 1.2+ obligatorio, cifrado en reposo con KMS, certificados fuera del código |
| Inyección | JPA con consultas parametrizadas. Prohibida la concatenación de SQL |
| Diseño inseguro | Token opaco en el portal público (no enumerable), documentos inmutables |
| Configuración incorrecta | Terraform como fuente única, análisis estático (`tfsec` o `checkov`) en el pipeline, sin bucket público |
| Componentes vulnerables | Dependabot + OWASP Dependency-Check bloqueante en CI |
| Fallas de identificación | MFA disponible, política de contraseñas de Cognito, bloqueo por intentos |
| Integridad de datos | **Firma XAdES es la integridad del comprobante.** S3 con versionado y Object Lock |
| Fallas de registro | Bitácora `auditoria` append-only + CloudTrail + alarmas sobre la DLQ |
| SSRF | Sin URLs de destino controladas por el usuario. Egress restringido por security group |

### 8.4 Protección de datos personales

Ley 29733 y su reglamento aplican: se tratan nombre, documento de identidad y domicilio de clientes finales.

- El portal público expone datos personales del adquiriente a quien tenga el enlace. **El token opaco es el control de acceso**, por eso su calidad criptográfica es un requisito de cumplimiento, no una preferencia técnica.
- Sin analítica de terceros en `ver.ondexia.com`.
- Retención: el XML y el CDR se conservan por obligación tributaria (5 años). La caducidad del **enlace público** es decisión abierta (Dominios §Pendientes).

---

## §9 NFRs técnicos

> `[PROPUESTO]` — sin PRD aprobado, estos NFR son propuestas del equipo técnico. Requieren validación con negocio.

| NFR | Objetivo | Mecanismo técnico concreto |
|---|---|---|
| NFR-01 `[PROPUESTO]` | Respuesta de la API < 500 ms en p95 | SnapStart (200-400 ms de restore) + índices de §5 + pool acotado. Alarma sobre `p95` de API Gateway |
| NFR-02 `[PROPUESTO]` | Confirmar una venta al usuario en < 2 s | Respuesta `202` sin esperar a SUNAT (F-01). El tiempo de SUNAT queda fuera de la ruta crítica |
| NFR-03 `[PROPUESTO]` | 99.5 % de comprobantes aceptados en < 5 min | Cola con 5 reintentos y backoff. Métrica: antigüedad del mensaje más viejo en SQS |
| NFR-04 `[PROPUESTO]` | Cero correlativos duplicados o con salto | `SELECT FOR UPDATE` transaccional (F-02) + constraint UNIQUE como última defensa |
| NFR-05 `[PROPUESTO]` | Una caída de SUNAT no interrumpe la venta | Desacople por SQS (DT-13). El sistema acumula y drena al restablecerse |
| NFR-06 `[PROPUESTO]` | Aislamiento total entre empresas | RLS + `empresa_id` obligatorio + prueba automatizada de fuga entre inquilinos en CI |
| NFR-07 `[PROPUESTO]` | RPO ≤ 5 min · RTO ≤ 4 h | RDS con PITR (respaldo continuo); S3 con versionado; infraestructura reproducible por Terraform. **Sin Multi-AZ por R-12:** el RTO depende de restaurar, no de conmutar |
| NFR-08 `[PROPUESTO]` | Portal público resiste escaneo automatizado | Token de 128 bits + WAF con límite por IP + `noindex` |
| NFR-09 `[PROPUESTO]` | Trazabilidad completa de todo comprobante | Bitácora append-only + CloudTrail + correlation-id propagado por X-Ray |

---

## §10 Infraestructura y despliegue

### 10.1 Entornos

| Entorno | Dominio | SUNAT | Datos |
|---|---|---|---|
| `dev` | `*.dev.ondexia.com` | **Beta** | Sintéticos. Prohibido copiar producción. **Instancia RDS detenida fuera de horario** por planificador (R-12) |
| `prod` | `*.ondexia.com` | Producción | Reales |

> La capa gratuita de RDS cubre **una sola** instancia. Con dos entornos encendidos de forma permanente ya hay factura, de ahí el apagado programado de `dev`. Alternativa válida durante el desarrollo temprano: PostgreSQL en contenedor local y `dev` en AWS solo para pruebas de integración contra la beta de SUNAT.

### 10.2 Pipeline

**CI** (automático, en cada push): build de pnpm y pruebas del frontend → build de Maven, pruebas de integración contra PostgreSQL real y Dependency-Check → `terraform fmt`, `init` y `validate`.

**Despliegue** (manual, eligiendo entorno): verificar backend → construir frontend → `terraform plan` a un archivo → **aprobación manual del entorno `prod`** → `terraform apply` sobre ese plan → publicar la SPA en S3 e invalidar CloudFront → sondear `/salud`.

> La versión 0.9 de este documento describía aquí `cdk synth` + `cdk-nag`. DT-16 retiró CDK y la línea quedó atrás. Corregido en 0.11.

Dos detalles del orden que no son arbitrarios: **se construye y se prueba antes de tocar AWS**, para que un fallo de compilación no deje medio despliegue hecho; y **el `apply` se hace sobre el plan guardado**, no recalculándolo, porque entre el plan y el apply hay una aprobación humana y lo aplicado tiene que ser exactamente lo revisado.

La aprobación manual antes de producción no es burocracia: un despliegue defectuoso del Emisor genera comprobantes inválidos con consecuencias fiscales para el cliente.

### 10.3 Plan de despliegue por fases (R-12)

**Principio: la arquitectura no cambia entre fases, solo cambia dónde corre.** Como toda la infraestructura se define en Terraform, pasar de una fase a la siguiente es cambiar parámetros de configuración, no reescribir. Eso es lo que hace seguro empezar en la fase más barata.

#### Fase 0 — Desarrollo local · **USD 0/mes**

Nada desplegado en AWS. El entorno completo corre en la máquina del desarrollador:

| Componente | En local |
|---|---|
| PostgreSQL | Contenedor Docker |
| API Spring Boot | `spring-boot:run` directo, sin Lambda |
| SQS, S3, Secrets Manager | LocalStack |
| Frontend | Vite en modo desarrollo |
| **SUNAT beta** | **Se consume desde la máquina local** — no requiere infraestructura desplegada |

Punto clave: la homologación contra la beta de SUNAT —la parte más incierta y más larga del proyecto— **se puede completar entera sin desplegar nada**. No hay razón técnica para pagar infraestructura durante esa etapa.

Único gasto real de esta fase: el dominio (~13 USD/año). Conviene registrarlo ya para asegurar la marca, aunque no apunte a nada.

#### Fase 1 — Primer despliegue demostrable · **~USD 0-3/mes**

Cuando exista algo que mostrar a un cliente potencial:

| Componente | Servicio | Costo |
|---|---|---|
| Landing y app | S3 + CloudFront | 0 — nivel siempre gratuito |
| API | Lambda **fuera de la VPC** | 0 — 1 M solicitudes/mes gratis |
| Colas y documentos | SQS + S3 | ~0 |
| Base de datos | Postgres gestionado de capa gratuita externa (Neon, Supabase o equivalente) | 0 |
| Autenticación | Cognito | 0 |

La clave del cero está en **no crear la VPC**: sin VPC no hay NAT que pagar, y las Lambdas alcanzan tanto a SUNAT como a los servicios de AWS por endpoints públicos. La base de datos se accede por internet con TLS.

> **Límite estricto de esta fase:** una base de datos de capa gratuita externa **no es admisible para comprobantes reales**. Esos niveles gratuitos suspenden la instancia por inactividad y no ofrecen garantía de retención, mientras que un comprobante emitido obliga a conservar XML y CDR por 5 años. La Fase 1 es para demostración y datos sintéticos contra la beta de SUNAT. **Emitir en producción exige pasar a Fase 2.**

#### Fase 2 — Producción con clientes reales · **~USD 24/mes**

La arquitectura descrita en §3 y §4.6: VPC, RDS `db.t4g.micro`, instancia NAT con IP fija, entornos separados. Se activa cuando hay el primer cliente pagando — el costo deja de ser gasto y pasa a ser costo de servicio.

**Disparador de transición:** la primera emisión con certificado de producción. No antes, y de ninguna manera después.

### 10.4 Continuidad

| Métrica | Objetivo | Mecanismo |
|---|---|---|
| RPO | ≤ 5 min | RDS PITR (respaldo continuo, retención 7 días) |
| RTO | ≤ 4 h | Infraestructura reproducible por Terraform + restauración de snapshot. Sin conmutación automática (R-12) |
| Retención fiscal | 5 años | S3 Object Lock sobre XML y CDR |
| Bus factor | **1 (R-13)** | **Riesgo aceptado y no mitigable con tecnología.** Los paliativos son documentales: este DTE, la IaC versionada, decisiones con sustento escrito y `README` de arranque reproducible. Si el proyecto adquiere valor comercial, la mitigación real es incorporar una segunda persona |

---

## §11 Deuda técnica conocida

Lo que **no** se diseña ahora, con su razón:

| ID | Deuda | Razón | Disparador para atacarla |
|---|---|---|---|
| DT-D1 | Base de datos compartida entre núcleo y motor de facturación | Evita transacciones distribuidas sobre datos contables en V1 | Si la facturación necesita escalar o desplegarse por separado |
| DT-D2 | Sin Nota de Crédito ni Comunicación de Baja | Excluidas del alcance V1 | **Alto riesgo — ver Alcance §2 V-1.** Recomendación abierta de subirlas a V1 |
| DT-D3 | Sin caché de lectura (Redis) | Volumen inicial no lo justifica; añade costo fijo | Cuando el p95 de lectura supere el NFR-01 |
| DT-D4 | Sin búsqueda de texto completo dedicada | El índice GIN de PostgreSQL alcanza para el catálogo inicial | Catálogos sobre ~100 000 productos |
| DT-D5 | Reportes sobre la base transaccional | No hay volumen para justificar una réplica de lectura | Cuando un reporte degrade la operación |
| DT-D6 | SnapStart acopla a Lambda | Migrar a Fargate exigiría rehacer el arranque | Solo si el modelo de costo cambia |
| DT-D7 | Sin conciliación automática de comprobantes con SUNAT | No hay consulta masiva en V1 | Ante la primera discrepancia detectada en producción |
| DT-D8 | Estados por polling, sin WebSocket | Simplifica V1 | Si la espera percibida molesta a los usuarios |
| DT-D9 | **Base de datos sin Multi-AZ ni réplica** | R-12: Multi-AZ duplica el costo de la instancia | Al primer cliente con compromiso contractual de disponibilidad |
| DT-D10 | **Instancia NAT como punto único de falla** | R-12: ahorra ~29 USD/mes frente al NAT Gateway | Cuando la facturación detenida por caída del NAT tenga costo mayor que el ahorro |
| DT-D11 | Sin RDS Proxy; pool controlado por concurrencia reservada | R-12: ~22 USD/mes que a este volumen no se justifican | Si el agotamiento de conexiones aparece en producción |
| DT-D12 | **El contexto de la petición cuesta ~4 consultas** (usuario, cuenta, administrador, asignaciones) | Escribir una consulta única acopla cuatro conceptos antes de saber cómo evolucionan | Cuando el p95 de la API se acerque al NFR-01. Se resuelve con una vista o una consulta con `join`, sin tocar nada más |
| DT-D13 | **La clave del rol de aplicación de PostgreSQL no rota sola** | El rol se crea en el aprovisionamiento; rotar exige un `ALTER ROLE` fuera de las migraciones | Antes del primer cliente real. La salida limpia es autenticación IAM de RDS, que elimina la clave |
| DT-D14 | El emisor de tokens del perfil `local` es código de producción condicionado | Sin él la Fase 0 (§10.3) no es viable: no hay forma de autenticar sin Cognito desplegado | Cuando exista el pool de `dev`. Mitigado: la clave se genera en cada arranque y nunca sale de memoria, y el arranque se aborta si detecta ejecución en Lambda |
| DT-D15 | **Sin análisis estático de seguridad sobre la infraestructura** | DT-16 retiró `cdk-nag` y no se ha sustituido por `tfsec` ni `checkov`. El CI comprueba sintaxis y coherencia de referencias, no configuraciones inseguras | Antes del primer `apply` contra producción |
| DT-D16 | **El CI no ejecuta `terraform plan`** | Un `plan` en cada push exigiría credenciales de AWS y un rol de solo lectura adicional. `validate` no comprueba que AWS acepte cada combinación de argumentos ni que las cuotas den | Si un `apply` falla por algo que un `plan` habría anticipado |
| DT-D17 | **`ondexia.api` no produce un artefacto desplegable en Lambda** | Es una aplicación web de Spring Boot; falta el adaptador que traduce el evento de API Gateway a una petición HTTP, y el empaquetado con SnapStart (DT-02). El despliegue deja entretanto una función de relleno que responde `501` | Es el siguiente trabajo si se quiere una API desplegada, y no solo desplegable |

---

## §12 Validación y peer review

| Rol | Nombre | Estado |
|---|---|---|
| Tech Lead | Rol acumulado por el desarrollador único (R-13) | — |
| Arquitecto | Rol acumulado por el desarrollador único (R-13) | — |
| Peer review formal | — | **PENDIENTE PR — ver 12.1** |

### 12.1 Peer review con equipo de una persona

El proceso COE QE exige revisión por pares antes de "Aprobado". **Con un solo desarrollador no hay par**, así que la práctica no se puede cumplir tal como está escrita. Declararlo es preferible a simularlo con una firma vacía.

Sustitutos admisibles, en orden de valor:

| Mecanismo | Qué cubre | Qué **no** cubre |
|---|---|---|
| **Revisor externo puntual** (contador o desarrollador de confianza, una sesión por artefacto) | Errores de dominio tributario y supuestos falsos | Requiere agenda de un tercero |
| **Revisión asistida por IA con la checklist doctrinal** aplicada en sesión separada de la de redacción | Inconsistencias, omisiones, criterios de cierre sin cumplir | **No detecta un supuesto de negocio equivocado si el autor y el revisor comparten el error** |
| **Revisión diferida** — releer el artefacto a los N días con la checklist | Errores de redacción y saltos lógicos | Sesgo del autor intacto |
| **Pruebas automatizadas como red de seguridad** | Regresiones de código | Nada del diseño |

**El riesgo residual que ninguno cubre:** un error de interpretación de la normativa SUNAT que el autor da por cierto. Una IA tiende a aceptar la premisa del autor, y las pruebas solo verifican lo que el autor pensó verificar.

**Mitigación específica y obligatoria:** validar la interpretación tributaria contra **la beta de SUNAT**, que es un revisor imparcial e inapelable. Un comprobante que la beta acepta está bien formado; uno que rechaza señala exactamente dónde está el error. Es la razón por la que la Fase 0 (§10.3) prioriza llegar temprano a la beta: sustituye al peer review en la única dimensión donde el peer review era insustituible.

**Este documento no puede pasar a "Aprobado" mientras:**

1. No exista un PRD en estado "Aprobado" (§1.2).
2. No se ejecute al menos uno de los mecanismos sustitutos de 12.1, con bitácora.
3. No se resuelva DT-12 reabierta (§7.1) — emisión propia vs proveedor.
4. No se resuelvan las 5 preguntas abiertas del documento de alcance §5.
5. No se confirme el plan de cortes verticales del alcance §6.

---

## §13 Control de versiones

| Tipo de cambio | Requiere |
|---|---|
| Corrección de redacción | Registro en la bitácora, sin nueva versión |
| Nueva decisión técnica DT-XX | Versión menor + revisión del Tech Lead |
| Cambio de una DT ya aprobada | Versión mayor + peer review + análisis de impacto |
| Cambio en el modelo de datos post-desarrollo | Versión mayor + migración documentada |

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1 | 2026-08-06 | Redacción inicial en estado Preliminar, con desviación documentada por ausencia de PRD | — |
| 0.2 | 2026-08-06 | Restricción de costo R-12. DT-03 revisada (RDS `t4g.micro` en vez de Aurora Serverless v2 + proxy), DT-14 nueva (instancia NAT), §4.3 perfil de costo, deudas DT-D9 a DT-D11 | — |
| 0.3 | 2026-08-06 | §10.3 plan de despliegue en 3 fases con costo cero en Fase 0 y 1. Aclaración de la naturaleza del arranque en frío de Spring en Lambda (§4.1) | — |
| 0.4 | 2026-08-06 | DT-04 revisada: Angular en vez de React. DT-15 nueva: landing en Astro. §4.5 sustento Angular vs Vue | — |
| 0.5 | 2026-08-06 | R-13 (equipo unipersonal). §7.1 reabre DT-12 con recomendación de proveedor de emisión. §12.1 sustitutos del peer review. Bus factor 1 asumido | — |
| 0.6 | 2026-08-06 | Corrección de versiones desactualizadas (Angular 19→22, PostgreSQL 16→17/18, Java y Spring Boot despinneados). §4.3 nueva: política de versiones por criterio, no por número | — |
| 0.7 | 2026-08-06 | Versiones confirmadas: Angular 22, Java 21 LTS, Spring Boot sobre Java 21, PostgreSQL en RDS | — |
| 0.11 | 2026-08-11 | §10.2 corregido: describía el pipeline con `cdk synth` + `cdk-nag`, que DT-16 había retirado. Se sustituye por el pipeline real, con el orden de pasos y su porqué. DT-D15 acotada a lo que sigue pendiente (análisis estático de infraestructura) tras arreglarse los tres pasos rotos del CI; DT-D16 y DT-D17 nuevas | — |
| 0.10 | 2026-08-11 | Esqueleto del backend construido y verificado. DT-17 nueva (arquitectura interna: monolito modular con hexagonal pragmática) y DT-18 nueva (Spring Boot 4.0.7, con las cuatro reorganizaciones de módulos que rompen los ejemplos publicados). §8.1: **segunda trampa de RLS** — un rol superusuario se salta todas las políticas y `FORCE` no le alcanza; detectada porque las pruebas de aislamiento fallaron, mitigada con un rol dedicado y una comprobación que aborta el arranque. Se documenta qué tablas quedan fuera de RLS y por qué. Deudas DT-D12 a DT-D15 | — |
| 0.9 | 2026-08-10 | DT-16 nueva: Terraform sustituye a AWS CDK. Se escribe la v1 completa en `ondexia.infra/`. Referencias a CDK actualizadas en §4.5, §8.3, §9 y §10.3 | — |
| 0.8 | 2026-08-10 | §4.6 corregido: faltaban WAF, IP pública IPv4 y el escalado de secretos por empresa; el piso pasa de ~24 a ~40 USD/mes. La capa gratuita cambió a créditos. §4.7 nueva: costo de la v1 (~15). §4.8 nueva: consecuencias de la Lambda sin NAT. §5.2 ampliado con `cuenta`, `cuenta_administrador`, alcance por sucursal y `permisos_version`. §5.8 nueva: política de almacenamiento por reproducibilidad. §8.1 reescrito: contexto no confiable, grupos de usuarios separados, trampa de RLS con Lambda. DT-05 corregida (dato de capa gratuita caduco) | — |

---

*Proceso COE QE · Wirbi S.A.C*
