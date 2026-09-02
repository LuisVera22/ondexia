# Ondexia — Estructura de repositorio y convenciones de nombres

Estado: **implementado (v3)** · Fecha: 2026-08-11 · Deriva de [DTE-ONX-001](DTE-ONX-001_sistema_gestion_comercial.md) §3

> **v2 — actualizado a la estructura real creada.** Se adoptó la convención con punto (`ondexia.api`) en lugar de la propuesta con guion. Decisión del propietario; este documento la registra como vigente y se rige por ella.
>
> **v3 (2026-08-11) — agrupación bajo `apps/backend/` y `apps/frontend/`.** El backend Java pasó a un único árbol de Maven; los tres frontends se agruparon por afinidad. Se añaden §4.1 a §4.3 (arquitectura interna, nomenclatura y dirección del contrato) y se reescribe §9, porque los tres pasos del CI estaban rotos.

## 1. Monorepo, no varios repositorios

**Decisión: un solo repositorio `ondexia`.**

| Criterio | Monorepo | Varios repos |
|---|---|---|
| Repositorios que crear y configurar | 1 | 6 |
| Archivos de workflow | **1** | 6 como mínimo |
| Configuración de OIDC y secretos | 1 workflow, **un rol por entorno** | 6 workflows, un rol por entorno |
| Cambio que toca API y frontend | Un commit atómico | 3 PR coordinados, con ventana de incoherencia |
| Contrato `openapi.yaml` compartido | Un archivo | **Paquete versionado con su propio pipeline de publicación** |
| Sobrecarga para **un solo desarrollador (R-13)** | Mínima | Multiplicada por seis |

> **Corregido el 2026-09-01, hallazgo C1 de la auditoría.** Esta fila decía «1
> rol, 1 secreto» frente a «1 rol con 6 relaciones de confianza, 6 secretos», y
> esa comparación se cumplió: se desplegó **un rol con `PowerUserAccess` que
> confiaba en `environment:dev` y en `environment:prod` a la vez**. Como el
> entorno `dev` de GitHub no exige revisor, la aprobación manual de producción
> dejó de proteger nada — bastaba lanzar el despliegue con `entorno=dev` y usar
> esas credenciales contra el estado de prod.
>
> **Una frontera de confianza no se colapsa por ahorro operativo.** Un rol por
> entorno; el mantenimiento se automatiza con `for_each`, no se evita
> fusionando. El monorepo sigue siendo la decisión correcta y esa fila no era
> parte de su argumento: el número de repositorios y el número de fronteras de
> confianza son cosas distintas, y aquí se contaron juntas.

### 1.1 Por qué separar en repositorios *aumenta* la complejidad del CI/CD

Es la confusión más común, y conviene dejarla escrita porque la intuición apunta al revés.

**El costo del monorepo es un filtro de rutas — o ni eso.** En este proyecto ni siquiera se usan: un solo workflow construye lo que exista y se salta lo que no.

**El costo de varios repositorios es estructural**, y se concentra en un punto: `ondexia.contracts`. El `openapi.yaml` y los catálogos de SUNAT los consumen `ondexia.api`, `ondexia.web` y `ondexia.facturacion`. En un monorepo eso es **un archivo que los tres leen**. Separados en repositorios, hay exactamente dos caminos:

1. **Publicarlo como paquete versionado** (npm y Maven) con su propio pipeline de release, y actualizar la dependencia en tres repositorios cada vez que cambia un campo. Es un pipeline extra que existe solo por haber separado.
2. **Copiarlo a mano** en cada repositorio — que es garantizar que se desincronicen, justo el error que el contrato existe para evitar.

Caso concreto: agregar un campo a la factura. En monorepo es **un commit** que toca contrato, backend y frontend, y el CI verifica los tres juntos. Separado son tres PR en orden — contrato, publicar, backend, frontend — con el sistema incoherente mientras tanto, y sin ningún CI capaz de verificar el conjunto.

### 1.2 Cuándo varios repositorios sí serían correctos

- Equipos distintos con cadencias de release independientes
- Necesidades de control de acceso distintas (por ejemplo, landing pública y API privada)
- Productos genuinamente independientes que no comparten contratos

Ninguna aplica hoy. **El único candidato defendible a salir es `ondexia.landing`**: no comparte contratos con nadie, tiene otro público y podría editarlo alguien de marketing sin acceso al resto. Aun así, mientras lo mantengas tú, separarlo agrega un repositorio y un workflow a cambio de nada.

### 1.3 La simplificación que sí aplica

La complejidad del CI/CD no la causa el monorepo: la causa **tener cinco desplegables**, y ese número no cambia con la estrategia de repositorios. Lo que sí se puede simplificar es el pipeline, y ahí se fue al mínimo (§9).

## 2. Convención de nombres

| Regla | Valor | Razón |
|---|---|---|
| Formato de carpetas | **`ondexia.<modulo>`** (punto como separador) | Convención adoptada. Aplicarla de forma uniforme importa más que cuál se eligió |
| Traducción en fronteras | El punto **no se propaga** a `artifactId`, paquetes npm, buckets S3 ni nombres de recurso de Terraform | Esos ecosistemas usan guion. `apps/frontend/ondexia.web` contiene el paquete npm `ondexia-web`; `apps/backend/ondexia.api` produce el artefacto `ondexia-api`. La carpeta y el identificador técnico no tienen por qué coincidir, pero la equivalencia debe ser mecánica: **punto en carpeta ⇄ guion en identificador** |
| Idioma | **Inglés para lo técnico, español para lo del dominio** | `api`, `infra`, `contracts` son técnicos. `facturacion`, `comprobante`, `guia-remision` son términos fiscales peruanos que **no se traducen**: "boleta de venta" no tiene equivalente en inglés, y traducirla introduce ambigüedad en un dominio normado |
| Paquetes Java | `com.ondexia.<modulo>` | Convención de dominio invertido sobre `ondexia.com` |
| Tablas y columnas | `snake_case` en español | Ya fijado en el DTE §5 |

La regla del idioma es la que más se rompe sola. El criterio operativo: **si el término aparece en una resolución de SUNAT, va en español**; si es un concepto de ingeniería, va en inglés.

## 3. Estructura propuesta

```
ondexia/
├── .github/workflows/          CI/CD (ver §9)
├── apps/
│   ├── backend/                Todo lo Java. Un solo build de Maven
│   │   ├── pom.xml, mvnw       Padre + wrapper (Maven 3.9.16)       ✅
│   │   ├── ondexia.domain/     Entidades JPA · dominio compartido   ✅
│   │   ├── ondexia.api/        Spring Boot · núcleo comercial       ✅ esqueleto
│   │   └── ondexia.facturacion/ Lambdas Emisor + Poller · SUNAT  [FALTA CREAR]
│   └── frontend/               Tres proyectos independientes, un build cada uno
│       ├── ondexia.web/        Angular · la aplicación             ✅ inicializado
│       ├── ondexia.landing/    Astro · marketing                   ✅ una página
│       └── ondexia.portal/     Portal público de comprobantes          [vacío]
├── ondexia.contracts/          OpenAPI + catálogos SUNAT      ✅ openapi.yaml
├── ondexia.infra/              Terraform                                [v1 escrita]
├── ondexia.docs/               Documentación del proceso COE QE    ✅
├── ondexia.tools/              Scripts y utilidades                     [vacío]
├── ia-skills/                  Skills COE QE · repo aparte, excluido del control de versiones
└── .gitignore
```

> **`ondexia.facturacion` falta y no es opcional.** Es el componente Emisor de DT-13 — el que aísla la firma digital, los certificados y la comunicación con SUNAT del núcleo comercial. Sin esa carpeta, la lógica de facturación termina dentro de `ondexia.api`, que es exactamente el acoplamiento que la arquitectura evita, y la migración proveedor → SUNAT directo (DTE §7.1) deja de ser barata.

### Por qué cada frontera existe

| Carpeta | Corresponde a | Frontera que respeta |
|---|---|---|
| `apps/backend/ondexia.api` | API Core del DTE §3.3 | **Nunca abre conexión hacia SUNAT.** Publica en SQS y responde |
| `apps/backend/ondexia.facturacion` | Emisor + Poller | **No conoce reglas de negocio.** Es la única que toca certificados y SUNAT (DT-13) |
| `apps/frontend/ondexia.web` | App Angular | No firma, no habla con SUNAT |
| `apps/frontend/ondexia.landing` | Landing Astro | Estática pura, sin acceso a datos |
| `apps/frontend/ondexia.portal` | Portal público | **Sin autenticación, aislado.** No consulta la base transaccional |
| `ondexia.infra` | Toda la infraestructura | Fuente única de la topología. Nada se crea a mano en la consola |

La separación `ondexia.api` / `ondexia.facturacion` no es organización cosmética: **es el boundary de DT-13 hecho carpeta.** Que sean dos módulos desplegables distintos es lo que permite cambiar de proveedor de emisión a SUNAT directo (§7.1 del DTE) sin tocar el núcleo comercial.

### `backend/` y `frontend/` no son la misma clase de carpeta

Conviene no dejar que la simetría del nombre engañe.

**`backend/` agrupa un build.** Los tres módulos Java comparten un `pom.xml` padre, un wrapper y un reactor: `./mvnw test` construye los tres juntos, con versiones de dependencias gestionadas en un solo sitio. Sin la carpeta, ese padre quedaba en la raíz de `apps/` pareciendo gobernar también a Angular y Astro.

**`frontend/` agrupa por afinidad.** Los tres proyectos no comparten nada hoy: Angular, Astro y el portal son tres `package.json`, tres builds, tres despliegues y tres distribuciones de CloudFront. La carpeta ordena la vista y no hace más que eso.

Donde la agrupación se volvería técnica es si `frontend/` acaba llevando un **workspace de pnpm**: un solo `pnpm install`, un lockfile, un almacén compartido y la configuración de TypeScript y linting en un sitio. Sería el equivalente del `pom.xml` padre. **Decisión abierta** — tiene sentido plantearla cuando exista el segundo proyecto real, no antes: con un solo `package.json`, un workspace es una capa sin nada que resolver.

> Ojo con §1.2: `ondexia.landing` sigue siendo el único candidato defendible a salir a su propio repositorio. Un workspace de pnpm lo ataría a los otros dos y encarecería esa salida. No es un impedimento, pero sí un costo a contar el día que se decida.

## 4. Backend Java — multi-módulo Maven

`ondexia.api` y `ondexia.facturacion` comparten el modelo de dominio y las entidades JPA. Duplicarlo sería garantizar que se desincronicen.

```
apps/backend/
├── pom.xml                    parent, gestiona versiones
├── mvnw, mvnw.cmd, .mvn/      wrapper: fija Maven 3.9.16 en el repositorio
├── ondexia.domain/            entidades JPA, enums, catálogos SUNAT
│   └── com.ondexia.domain
├── ondexia.api/               API Core
│   └── com.ondexia.api
└── ondexia.facturacion/       Emisor + Poller
    └── com.ondexia.facturacion
```

**Por qué `backend/` y no directamente bajo `apps/`.** La v1 del esqueleto puso el `pom.xml` padre en la raíz de `apps/`, junto a `ondexia.web` y `ondexia.landing`. Funciona —Maven solo entra en los módulos que declara e ignora las carpetas de Angular y Astro— pero mezcla niveles: el padre de Maven parece gobernar cosas que no gobierna, y `apps/` deja de tener una lectura uniforme. Con la carpeta, `apps/` contiene cuatro desplegables al mismo nivel conceptual y uno de ellos resulta estar hecho de tres módulos.

No lleva el prefijo `ondexia.` a propósito: **no es un módulo, es una agrupación**. Darle forma de módulo sugeriría que produce un artefacto, y no produce ninguno. Va en inglés por la regla de §2 — «backend» es un término de ingeniería, no del dominio fiscal.

El wrapper vive aquí y no se comparte con el frontend: son dos gestores de dependencias distintos (Maven y pnpm) con dos ciclos de vida distintos.

| Módulo | `artifactId` | Depende de |
|---|---|---|
| Padre | `ondexia-parent` | — |
| Dominio | `ondexia-domain` | — |
| API Core | `ondexia-api` | `ondexia-domain` |
| Facturación | `ondexia-facturacion` | `ondexia-domain` |

**`domain` no depende de nadie.** Si empieza a importar de `api`, la separación se perdió y conviene detenerse a corregirlo.

### 4.1 Arquitectura interna

> **Sustituido el 2026-08-11 por [08 · Arquitectura del backend](08-arquitectura-backend.md).**
> Se adoptó **hexagonal con DDD táctico**: dominio puro sin JPA en su propio
> módulo Maven, adaptadores en `infrastructure`, y corte por dominio de negocio
> dentro de cada capa.
>
> Lo que sigue describe la decisión anterior y se conserva por trazabilidad. El
> motivo del cambio está en el §9 del documento nuevo, y merece leerse: el
> problema no fue la decisión sino que **lo documentado y lo construido no
> coincidían**.

<details>
<summary>Decisión anterior — monolito modular con hexagonal pragmática (obsoleta)</summary>

**El corte principal es por dominio, no por capa técnica.** Existe `almacen` y existe `ventas`, y dentro de cada uno están sus capas:

```
ondexia-domain                      ondexia-api
com.ondexia.domain.<dominio>        com.ondexia.api.<dominio>
  entidades, enums, puertos           aplicacion/      casos de uso
                                      infraestructura/ adaptadores
                                      web/             controladores y DTO
```

El corte contrario —un paquete `service` con sesenta clases— no deja ninguna frontera que impida que Ventas llame directo al repositorio de Almacén, y a los 23 submódulos del alcance eso es un monolito enredado.

**Las entidades llevan anotaciones de JPA, y la palabra «pragmática» es deliberada.** La versión estricta exige un dominio POJO puro con entidades de persistencia aparte y un mapeador por agregado: son unas setenta clases duplicadas y sus mapeadores, a cambio de una independencia del motor que este proyecto no va a ejercer. No vamos a cambiar de PostgreSQL, y RLS —decisión de aislamiento del DTE §5.1— ya nos ata a él a propósito.

Lo que sí se conserva de hexagonal es lo que rinde: **el dominio no conoce HTTP ni Spring Web ni seguridad**, y las dependencias apuntan hacia adentro.

</details>

### 4.2 Nomenclatura en el código

Sustantivo del dominio en **español**, sufijo técnico en **inglés**:

| | Ejemplo |
|---|---|
| Clases | `ProductoRepository`, `ProductoService`, `EmitirBoletaUseCase` |
| Paquetes | `com.ondexia.almacen.producto` |
| Métodos de negocio | `calcularCostoPromedio`, `habilitarProduccion` |
| Tablas y columnas | `snake_case` español (DTE §5) |

**La excepción la impone el framework, no el gusto.** Spring Data *analiza* el nombre del método para construir la consulta: `findByEmpresaIdAndCodigo` genera el `WHERE`, y `buscarPorEmpresaIdYCodigo` no genera nada — falla al arrancar el contexto. Esos métodos van en inglés y está anotado en `com.ondexia.domain.package-info`.

### 4.3 Contrato OpenAPI — dirección código → contrato

`springdoc` deriva el contrato de los controladores y `ExportarContratoIT` lo escribe en `ondexia.contracts/openapi.yaml` en cada ejecución de la suite. De ahí se genera el cliente Angular.

Que lo escriba una prueba es intencionado: **el contrato no puede quedarse atrás**. Se regenera siempre, y si cambia aparece en `git status` junto al cambio que lo provocó. La alternativa —acordarse de regenerarlo— es exactamente el fallo que el contrato existe para evitar (§5).

## 5. Contratos — el detalle que más rinde con un solo desarrollador

`contracts/openapi.yaml` como fuente única del contrato HTTP, y **el cliente Angular se genera desde ahí**, no se escribe a mano.

Razón concreta: trabajando solo, escribes el backend y el frontend en momentos distintos. Un campo renombrado en el API y no propagado al front es un error que aparece en tiempo de ejecución, semanas después. Generando el cliente, aparece en tiempo de compilación, el mismo día.

Aplica igual a los **catálogos de SUNAT** (tipos de documento, unidades de medida, monedas, motivos de nota de crédito): viven en `contracts/catalogos/` como datos versionados, y tanto el backend como el frontend los consumen del mismo lugar. SUNAT los actualiza, y tener dos copias significa tener una desactualizada.

## 6. Estrategia de ramas — GitFlow

**Decisión: GitFlow.** Es la preferencia adoptada para el proyecto.

### 6.1 Por qué encaja en este producto

GitFlow suele considerarse pesado para equipos pequeños, pero aquí resuelve un problema real: **Ondexia emite documentos con valor tributario**. Una versión defectuosa en producción no produce una pantalla rota, produce comprobantes inválidos ante SUNAT. La separación estricta entre lo que está integrándose y lo que está emitiendo es exactamente la garantía que hace falta.

Además calza sin fricción con los dos entornos ya definidos (DTE §10.3):

| Rama | Entorno | Significado |
|---|---|---|
| `main` | Producción | **Lo que está emitiendo comprobantes reales ahora mismo** |
| `develop` | `dev` | Integración continua contra la beta de SUNAT |

Esa correspondencia es la que da valor: `main` deja de ser "la última versión" y pasa a ser "la versión con la que hay comprobantes emitidos", que es una afirmación con consecuencias legales.

### 6.2 Ramas

| Rama | Nace de | Se fusiona en | Para qué |
|---|---|---|---|
| `main` | — | — | Producción. **Solo recibe merges de `release/*` y `hotfix/*`.** Nunca commits directos |
| `develop` | `main` | — | Integración. Base de todo trabajo nuevo |
| `feature/*` | `develop` | `develop` | Una funcionalidad o historia |
| `release/*` | `develop` | `main` **y** `develop` | Estabilización antes de producción |
| `hotfix/*` | `main` | `main` **y** `develop` | Corrección urgente en producción |

**La doble fusión de `release/*` y `hotfix/*` no es opcional.** Si un hotfix entra a `main` y no vuelve a `develop`, la corrección se pierde en el siguiente release — el error clásico de GitFlow, y el más caro de diagnosticar.

### 6.3 Convención de nombres

```
feature/c1-emision-boleta
feature/c1-portal-publico
release/0.1.0
hotfix/0.1.1-firma-xades
```

Las ramas `feature/*` se nombran con el **corte vertical al que pertenecen** (C1…C5 del alcance §6). Así el historial deja ver a qué entrega corresponde cada trabajo sin consultar un tablero aparte.

### 6.4 Versionado y etiquetas

Etiqueta en `main` con versión semántica en cada fusión de `release/*` o `hotfix/*`:

| Etiqueta | Cuándo |
|---|---|
| `v0.1.0` | Cierre del corte C1 — primera boleta emitida de extremo a extremo |
| `v0.2.0` | Cierre de C2 — ventas legalmente operables |
| `v0.1.1` | Hotfix sobre C1 |

La etiqueta es la que permite responder la pregunta que va a llegar tarde o temprano: *¿con qué versión del sistema se emitió este comprobante?* Conviene que el número de versión quede registrado junto al comprobante emitido.

### 6.5 Correspondencia con el pipeline

| Evento | Consecuencia |
|---|---|
| Push a `feature/*` o PR | CI: construir y probar |
| Merge a `develop` | CI + despliegue automático a `dev` (desde Fase 1) |
| Merge a `main` | CI + despliegue a producción **con aprobación manual** (DTE §10.2) |

### 6.6 Qué parte de GitFlow rinde más aquí

Con un solo desarrollador (R-13), el valor no se reparte parejo:

- **`develop` / `main` separados y `hotfix/*`** — alto valor. Son los que protegen la producción fiscal.
- **`release/*`** — valor medio. Da el espacio para estabilizar y probar contra la beta antes de emitir en serio.
- **`feature/*` por tarea** — valor bajo en lo mecánico, alto como disciplina de historial. Mantenerlas cortas evita que se conviertan en ramas de larga vida que luego cuesta integrar.

Lo que **no** conviene relajar es la regla de que `main` nunca recibe un commit directo. Es la única barrera entre un cambio a medio terminar y un comprobante inválido.

## 7. Lo que NO va en el repositorio

| Nunca | Dónde va |
|---|---|
| Certificados `.pfx` | Secrets Manager |
| Credenciales SOL | Secrets Manager |
| Endpoints de SUNAT por entorno | SSM Parameter Store |
| Volcados de base de datos con datos reales | En ningún lado fuera de AWS |

`.gitignore` debe incluir `*.pfx`, `*.p12`, `*.jks`, `.env` y `*.dump` **desde el primer commit**. Un certificado que entra al historial de Git obliga a reescribirlo o a revocar el certificado.

## 8. Decisiones de control de versiones tomadas

| Tema | Decisión |
|---|---|
| Proveedor | **GitHub** |
| Estrategia de ramas | **GitFlow** — `main` + `develop` + `feature/*` / `release/*` / `hotfix/*` (ver §6) |
| `ia-skills` | **Excluido del repositorio.** Tiene su propio remoto (`wirbidotcom/ia-skills`). Dejarlo dentro creaba un *gitlink* huérfano sin `.gitmodules`, que rompe el clonado. Para vincularlo formalmente: `git submodule add git@github.com:wirbidotcom/ia-skills.git ia-skills` |
| Carpetas vacías | Marcadas con `.gitkeep` — Git no versiona directorios vacíos |

## 9. CI/CD

**Dos archivos. Eso es todo el pipeline.**

| Workflow | Dispara con | Estado |
|---|---|---|
| `ci.yml` | Push a `main`, `develop` y ramas de trabajo; PR hacia `main` o `develop` | **Activo** — un job que construye y prueba lo que exista |
| `deploy.yml` | Solo manual (`workflow_dispatch`), con entorno a elegir | **Escrito, sin ejecutar todavía** contra una cuenta real |

> **Los tres pasos del CI estuvieron rotos.** Se corrigieron el 2026-08-11, y los tres fallaban de la misma forma silenciosa: `if: hashFiles(...) != ''` hace que un paso mal apuntado **se salte solo**, con lo que el build sale en verde sin haber construido nada.
>
> - **Frontend** ejecutaba `npm ci` con un `package-lock.json` retirado al migrar a pnpm.
> - **Backend** apuntaba a `apps/pom.xml`, ruta que dejó de existir al agrupar bajo `apps/backend/`.
> - **Infraestructura** buscaba `ondexia.infra/package.json` y ejecutaba `cdk synth`; DT-16 había retirado CDK.
>
> La lección no es que hubiera tres errores, sino **cuál era el modo de fallo**: un paso condicionado que no se dispara no avisa. Conviene comprobar de vez en cuando que el log del CI contiene lo que se espera, y no solo que la marca esté verde.

### 9.0 Decisiones de simplicidad

**Sin filtros de rutas.** Construir todo en cada commit cuesta minutos y evita la clase de error que aparece cuando frontend y backend se compilan por separado. Los filtros se agregan el día que el build moleste por lento, no antes.

**Sin workflows por módulo.** Un solo job con pasos que **se saltan solos** mientras el módulo no exista (`if: hashFiles(...) != ''`). El pipeline está listo desde hoy sin fallar en rojo por lo que aún no se ha escrito.

**Sin paso separado de análisis de dependencias.** El OWASP Dependency-Check se declara como plugin del `pom.xml` enlazado a la fase `verify`. Así corre igual en CI y en la máquina local, con un paso menos en el workflow. El control de seguridad se mantiene; lo que desaparece es la duplicación.

**Sin publicación de artefactos en CI.** Mientras no haya despliegue (Fase 0), guardar el `dist/` no sirve para nada.

### 9.1 Qué hace el despliegue

Sigue siendo manual (`workflow_dispatch`) porque no hay nada desplegado: un workflow automático que apunta a nada solo produce fallos rojos que enseñan a ignorar el tablero. El disparador por rama se activa al llegar la Fase 1.

Orden de los pasos, que no es arbitrario:

1. **Construir y probar antes de tocar AWS.** Si el frontend no compila o una prueba falla, conviene enterarse con la infraestructura intacta y no con medio despliegue hecho.
2. **`terraform plan` a un archivo, y `apply` sobre ese archivo.** Un `apply` que vuelve a planificar puede hacer algo distinto de lo que se revisó, y entre uno y otro hay una aprobación humana.
3. **El plan se publica en el resumen de la ejecución.** Con un solo operador (R-13) no hay un segundo par de ojos; el plan legible después del hecho es lo más parecido a una revisión que existe.
4. **Publicar la SPA después del `apply`**, porque los buckets y las distribuciones tienen que existir. `index.html` sin caché y el resto con un año: los bundles llevan hash y son inmutables, `index.html` es el único que cambia de contenido conservando el nombre.
5. **Sondear `/salud`.** Sin esta comprobación el workflow puede terminar en verde con la API caída.

Hay una casilla **«solo plan»** que se detiene antes de aplicar. Es la forma de leer un plan contra la cuenta real sin tocar nada.

> **La clave del estado lleva el entorno.** `versions.tf` no fija `bucket` ni `key`: se pasan en `terraform init`. Con una clave fija, `dev` y `prod` compartirían estado y el primer despliegue de `dev` reconfiguraría o destruiría producción.

### 9.2 Autenticación sin claves de larga vida

`deploy.yml` usa **OIDC**: GitHub emite un token de identidad y AWS lo canjea por credenciales temporales contra un rol que confía en este repositorio. No hay `AWS_SECRET_ACCESS_KEY` guardada en ningún lado.

Requiere, al llegar la Fase 1:

1. Proveedor de identidad OIDC de GitHub en la cuenta AWS.
2. Rol con política de confianza restringida **a este repositorio y a la rama `main`** — sin esa restricción, cualquier repositorio de la organización podría asumirlo.
3. Secreto `AWS_DEPLOY_ROLE_ARN` en GitHub con el ARN del rol.

### 9.3 Aprobación manual de producción

El DTE §10.2 exige aprobación manual antes de producción. Se implementa con **GitHub Environments**: el entorno `prod` se configura con revisor requerido. Aunque el revisor seas tú (R-13), es la pausa deliberada que evita desplegar una versión rota que emita comprobantes con valor legal.

### 9.4 Análisis de dependencias

OWASP Dependency-Check se configura en el `pom.xml` enlazado a `verify`, de modo que corre tanto en CI como en local. Es **bloqueante**, no informativo: en un sistema que firma comprobantes con valor tributario, una dependencia con CVE conocido es un defecto, no una advertencia.

**Requiere una clave de API del NIST.** Desde 2023 la NVD limita con dureza a quien consulta sin clave: la primera sincronización pasa de minutos a horas, o falla por límite de peticiones. No es que el análisis sea lento sin clave — es que es inviable dentro de un pipeline. La clave es gratuita y se pide en [nvd.nist.gov/developers/request-an-api-key](https://nvd.nist.gov/developers/request-an-api-key); se guarda como secreto `NVD_API_KEY`.

Mientras el secreto no exista, el paso **se omite con un aviso visible** en lugar de dejar el build colgado hasta agotar el tiempo. Es una concesión deliberada: un control que nadie puede ejecutar acaba desactivado de todas formas, y es preferible que conste que está desactivado a que el pipeline entero se vuelva ruido que se aprende a ignorar.

La base de vulnerabilidades se guarda bajo el repositorio local de Maven, que el CI ya cachea, así que la descarga completa ocurre una vez y no en cada push.

### 9.5 Qué NO valida el pipeline

Conviene tenerlo escrito, porque una marca verde invita a suponer lo contrario:

- **Terraform solo se valida, no se planifica.** `validate` comprueba sintaxis, tipos y referencias; no comprueba que AWS acepte cada combinación de argumentos, ni que las cuotas den, ni que los identificadores de políticas gestionadas sigan vigentes. Un `plan` en CI exigiría credenciales en cada push.
- **Sin análisis estático de seguridad sobre la infraestructura.** DT-16 retiró `cdk-nag` y no se ha incorporado `tfsec` ni `checkov` (DT-D15).
- **El backend no se despliega todavía.** `ondexia.api` es una aplicación web de Spring Boot, sin el adaptador que traduce el evento de API Gateway a una petición HTTP. Hasta que exista, el despliegue deja la función de relleno que responde `501`.

## 10. Nota operativa — el repositorio vive en OneDrive

La carpeta está bajo sincronización de OneDrive. Git y OneDrive compiten por los mismos archivos: la sincronización puede bloquear objetos de `.git` durante una operación y corromper el índice.

Recomendado: **mover el repositorio fuera de la carpeta sincronizada** (por ejemplo `C:\dev\ondexia`). El respaldo lo da GitHub, no OneDrive, y tener ambos duplica el riesgo sin agregar seguridad.
