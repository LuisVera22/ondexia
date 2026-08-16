# Ondexia — Plan de implementación: módulo Configuración

Estado: **plan aprobado** · Fecha: 2026-08-11 · Deriva de [02 · Alcance](02-alcance-modulos.md) §6.2 y [DTE-ONX-001](DTE-ONX-001_sistema_gestion_comercial.md) §5

Primer módulo de negocio del backend, sobre el esqueleto construido el 2026-08-11.

---

## 1. Por qué configuración primero

El alcance §6.2 advierte contra «todos los catálogos primero», y a primera vista
esto lo parece. No lo es: **C1 —emitir una boleta— no existe sin buena parte de
configuración.** Hacen falta empresa con RUC, establecimiento, serie con
correlativo y un usuario con rol. Configuración no es un rodeo hacia C1, es su
cimiento.

Hay un segundo argumento, técnico. El esqueleto tiene Row Level Security y
autorización por `(modulo, accion)` **sin una sola tabla de negocio**. La
primera que llegue va a revelar si el patrón aguanta, y configuración es el
sitio más barato para descubrirlo: un error en el CRUD de establecimientos no
produce un comprobante inválido.

### 1.1 Dónde se recorta el alcance

| Pantalla | ¿Entra? | Motivo |
|---|---|---|
| Empresa | **Sí** | RUC, razón social, domicilio fiscal |
| Establecimientos | **Sí** | Deciden serie y almacén |
| Series y correlativos | **Sí** | La pieza delicada del módulo |
| Almacenes | **Sí** | Mínimo, para descargar existencias en C1 |
| Usuarios | **Sí** | Sin usuarios no hay quien emita |
| Roles y permisos | **Sí** | El catálogo ya existe; falta la gestión |
| Identidad visual | No | Necesita S3 y URLs prefirmadas; va con el bucket de marca |
| Suscripción | No | Es facturación, no operación. Depende de precios sin cerrar |
| Comprobantes | Parcial | Solo qué tipos emite la empresa; el resto es de C1 |

Seis de nueve. Las tres que quedan fuera no bloquean nada.

> **Estado a 2026-08-13: las seis están entregadas**, con Comprobantes en su
> alcance parcial. 91 pruebas en verde. Las dos que quedan fuera —Identidad
> visual y Suscripción— siguen sin tocarse, y sus pantallas conservan la maqueta
> original: dejarlas conectadas a medias sería peor que dejarlas evidentemente
> sin conectar.

### 1.2 Sin SUNAT en esta versión

Decisión del propietario, 2026-08-11: **la v1 no integra con SUNAT.** Consecuencias
para este plan:

- No hay ensayo de firma XAdES, ni homologación, ni `ondexia.facturacion`.
- **No se valida que el RUC exista ante SUNAT.** Esa comprobación exige salir a
  internet y la Lambda no tiene NAT (DTE §4.8); queda descartada, no diferida
  por limitación técnica.
- **Sí se valida formato y dígito verificador**, que es aritmética local: ataja
  la errata de tecleo, que es el error real y frecuente.
- Los campos `modo_sunat` y `secret_arn_certificado` siguen modelados y sin uso.
  Quitarlos ahora obligaría a una migración cuando SUNAT entre, y una columna sin
  usar no cuesta nada.
- **Las series y los correlativos siguen haciendo falta.** Los documentos se
  numeran igual aunque no se envíen, y el rigor del correlativo es el mismo: los
  huecos y los duplicados son un problema del libro, no del envío.

---

## 2. Principio de entrega

**Cada entrega es vertical y demostrable**: endpoint funcionando, pruebas de
integración contra PostgreSQL real, contrato regenerado y **pantalla conectada**.

Nada de «todas las entidades, luego todos los servicios, luego conectar». Esa
forma acumula desajustes de contrato que aparecen todos juntos al final, que es
cuando más caro sale corregirlos.

---

## 3. Punto de partida real del frontend

Conviene decirlo antes de planificar, porque es mayor de lo que parece: **el
frontend nunca ha hecho una petición HTTP.**

| Pieza | Estado |
|---|---|
| `provideHttpClient` | **No está en `app.config.ts`** |
| Interceptor de autenticación | No existe |
| Guard de rutas | No existe |
| Servicio de sesión | No existe |
| `ContextoService` | Datos de ejemplo, con identificadores **`number`** |
| Cliente generado del contrato | No existe |

El detalle de los identificadores no es cosmético: el backend emite **UUID**, así
que `ContextoService` y todo lo que lo consume cambian de tipo. Es un cambio
mecánico pero ancho, y conviene hacerlo de una vez en la Entrega 0 y no
entrega a entrega.

---

## 4. Las entregas

### Entrega 0 · Andamiaje · **L**

Lo que, si no se resuelve una vez, se escribirá mal doce veces.

**Backend**

| | Por qué |
|---|---|
| `ServicioAuditoria` | La tabla `auditoria` existe y **nadie escribe en ella**. Cada entrega posterior la usa; si llega en la tercera, las dos primeras quedan sin rastro |
| Paginación y ordenación propias | El `Page` de Spring Data no es un contrato estable en OpenAPI y contamina el cliente generado con tipos internos del framework |
| `@RequierePermiso("configuracion.empresa:editar")` | Meta-anotación sobre `@PreAuthorize`. Legible en el controlador, y un sitio único donde cambiar la mecánica |
| Violaciones de unicidad → `ConflictoException` | Hoy un RUC duplicado sale como **500**. Debe ser un 409 con código estable que el frontend distinga |
| Prueba de arquitectura (ArchUnit) | Que `domain` no importe de `api`, y que `api` no importe firma XML ni clientes de SUNAT. **Convierte el boundary de DT-13 en algo que rompe el build**, en lugar de una advertencia en un documento |

**Frontend**

| | |
|---|---|
| `provideHttpClient` con interceptores | No existe |
| `SesionService` | Guarda el token. En local lo pide a `POST /desarrollo/token`; contra Cognito será el flujo real. El resto de la aplicación no debe notar la diferencia |
| Interceptor | Añade `Authorization` y `X-Empresa-Id`. En un solo sitio, o cada servicio lo repetirá |
| Manejo de 401 y 403 | 401 lleva al acceso; 403 muestra «sin permisos». Ya existen ambas pantallas |
| `ContextoService` real | Alimentado por `GET /api/v1/contexto`. **Identificadores a UUID** |
| Menú por permisos reales | El menú lateral deja de mostrarlo todo y refleja los permisos del rol |
| Generación del cliente | Desde `ondexia.contracts/openapi.yaml` |

**Prueba de que funciona:** iniciar sesión como el usuario de ejemplo, ver dos
empresas en el selector, cambiar de una a otra y **ver cómo cambia el menú** —
en una es Administrador y en la otra Vendedor. Eso ejercita token, contexto,
permisos y aislamiento de una sola pasada.

> **Decisión abierta: qué generador de cliente.** `openapi-generator` con
> `typescript-angular` es el estándar y ya hay Java en la máquina para
> ejecutarlo; `orval` y `ng-openapi-gen` son más ligeros y producen código más
> idiomático. Se decide al empezar la entrega.

---

### Entrega 1 · Empresa y establecimientos · **M** — **ENTREGADA**

Endpoints bajo `/api/v1/configuracion`, 43 pruebas en verde, y las dos
pantallas conectadas.

**Lo que cambió respecto a lo planeado:**

- **El RUC no se bloquea «cuando hay comprobantes», se bloquea siempre.** Al
  escribir la regla condicional se vio que sobra: no existe ningún momento en
  que cambiarlo sea correcto. Si se tecleó mal al dar de alta, lo que procede es
  crear la empresa buena.
- **Sin empresa activa se responde 400, no 403.** Los permisos son por empresa,
  así que sin una elegida se denegaba todo y el usuario con varias acababa en
  «sin permisos» — un mensaje falso sobre su situación.
- **Las pantallas perdieron campos.** `departamento`, `provincia`, `distrito`,
  `teléfono` y `correo` no existen en el esquema; mantenerlos habría significado
  aceptar lo que el usuario escribe y descartarlo en silencio. Los tres primeros
  se derivan del ubigeo cuando haya catálogo; los otros dos necesitan columnas
  nuevas, que es una migración y por tanto una decisión.
- **«Eliminar» pasó a ser «Desactivar»** en establecimientos, y la casa matriz
  (0000) no ofrece la acción.

**Deuda que deja:** el rol Vendedor no tiene ningún permiso de `configuracion.*`,
ni de lectura, así que el filtro entre empresas no se puede ejercitar por HTTP
—la autorización corta antes— y se prueba contra el caso de uso.

---

#### Lo que decía el plan

Las entidades ya existen. Falta todo lo demás.

- `GET`/`PUT` de la empresa activa; CRUD de establecimientos.
- Validación de RUC: formato y dígito verificador. **No contra SUNAT** (§1.2).
- Regla real: **el RUC se bloquea cuando ya hay comprobantes emitidos.** Hoy no
  hay comprobantes, así que la regla se escribe con la comprobación preparada y
  se activa en C1.
- Auditoría en cada cambio.
- Frontend: pantallas de empresa y establecimientos conectadas.

---

### Entrega 2 · Almacenes · **S** — **ENTREGADA**

Entidad nueva `almacen` (`empresa_id`, `sucursal_id`, `nombre`).

Es deliberadamente pequeña y va aquí por un motivo: es **la primera tabla que
estrena `activar_aislamiento_empresa()`**. Valida la función de RLS sobre una
tabla de negocio real con una entidad tan simple que, si algo falla, el fallo
solo puede estar en el aislamiento.

Funcionó: `Almacenes` es visiblemente más corto que `Establecimientos` porque no
comprueba la empresa en ningún método. Esa diferencia de longitud **es** la
prueba de que el aislamiento lo pone la base.

---

### Entrega 3 · Series y correlativos · **L** — **ENTREGADA**

La pieza crítica del módulo. 11 pruebas, incluida la de concurrencia.

**Lo que cambió respecto a lo planeado:**

- **La propagación del asignador es `MANDATORY`, no la habitual.** Con
  `REQUIRED`, llamarlo sin transacción abierta crearía una, confirmaría el número
  y volvería; si la creación del comprobante fallara después, ese número quedaría
  consumido para siempre. `MANDATORY` convierte ese error en una excepción
  inmediata en vez de en un hueco silencioso. **La firma del método impide el mal
  uso** en lugar de confiar en que un comentario lo advierta.
- **La letra de la serie se valida además en la base**, con un `CHECK` por tipo.
  Una serie con la letra equivocada no la rechaza nadie hasta el envío a SUNAT, y
  para entonces todos sus comprobantes están emitidos y numerados.
- **Apareció un `numeroInicial` que el plan no contemplaba.** Quien migra desde
  otro sistema va por el 4300 y no puede volver a numerar desde el 1 sin duplicar
  comprobantes ya declarados. Se fija **solo en el alta** y no se vuelve a tocar:
  un endpoint para «corregir» el correlativo acabaría usándose para tapar un
  error y produciría duplicados.
- **No hay borrado de series**, y el catálogo de permisos ya lo decía —
  `configuracion.serie` solo tiene `consultar`, `registrar` y `editar`. Se
  desactivan, conservando su último número: reactivar continúa donde se quedó.

**La prueba que más rinde:** doce hilos con barrera de salida pidiendo
correlativo de la misma serie, verificando **cero duplicados y cero huecos**. Más
dos que fijan las decisiones de arriba: que sin transacción falla, y que al
deshacer la transacción el número vuelve a estar disponible.

Con esto, el cimiento de C1 está completo.

---

### Entrega 4 · Usuarios y asignaciones · **M** — **ENTREGADA**

12 pruebas.

**Lo que cambió respecto a lo planeado:**

- **El identificador que manejan los endpoints es el de la asignación, no el de
  la persona.** La misma persona puede estar en varias empresas con roles
  distintos; usar su identificador obligaría a mandar además la empresa en cada
  llamada — un parámetro que el contexto ya sabe y que, si se pudiera mandar, se
  podría mandar mal.
- **Tres puertas que no se pueden cerrar por dentro**, y el plan solo preveía la
  tercera: nadie se desactiva a sí mismo, nadie se retira su propio acceso, y el
  último administrador de la cuenta no se puede desactivar. Esta última **no la
  puede defender la base**: el disparador de la V1 protege
  `cuenta_administrador`, y desactivar al usuario no borra su fila de
  administrador — la deja intacta y sin poder entrar, que es exactamente el
  estado que el disparador existía para impedir.
- **Retirar el acceso sí borra la fila**, y es la excepción a «desactivar, nunca
  eliminar». Una asignación no aparece en ningún comprobante: es un permiso
  vigente, y uno retirado no tiene por qué seguir existiendo. Quién lo hizo queda
  en la bitácora.
- **La pantalla tiene tres estados, no dos.** «Invitado» —la fila existe, la
  identidad de Cognito todavía no— es la respuesta a «le di de alta y no puede
  entrar», que sin esa etiqueta se diagnostica mirando la base.

> **El alta en Cognito la hace la propia persona, no el backend.** La Lambda no
> tiene salida a internet (DTE §4.8), y resolverlo exigiría un endpoint de
> interfaz a ~7.30 USD/mes — más caro que la NAT que se evitó. El backend crea la
> fila `usuario` sin `cognito_sub`; se vincula en el primer acceso.

#### El «se vincula en el primer acceso» que no vinculaba nada

Esa frase estuvo escrita —aquí y en tres javadoc— durante toda la Entrega 4 sin
que existiera el código que la cumpliera. `ResolverContexto` solo buscaba por
`cognito_sub`, y nadie buscaba nunca por correo para rellenarlo.

El efecto, visto en producción: se da de alta a alguien, se registra en Cognito,
`/contexto` responde `usuario_no_registrado` y el SPA lo lleva al formulario de
empresa nueva. La persona invitada rellena un RUC, **se crea una segunda cuenta
con su propia suscripción**, y la invitación se queda colgada para siempre. No
hay ningún error en ningún log: se reporta como «no me aparece la empresa».

Vale la pena anotarlo porque el modo de fallar se repite: una invitación que no
se puede aceptar no rompe nada, solo confunde a un cliente.

**Lo que lo cierra:** `POST /api/v1/registro/vinculo` (`VincularInvitacion`).
Busca invitaciones pendientes por correo, y si hay exactamente una, la engancha
al `sub` del token. El SPA lo llama antes de pintar el formulario, que es la
única puerta a esa pantalla.

**De dónde sale el correo, y por qué no del cuerpo.** Este es el único endpoint
del sistema que decide en qué empresa entra alguien a partir de su correo.
`RegistrarCuenta` sí lo acepta del cuerpo y ahí es inofensivo —la identidad con
la que se opera es el `sub`, y quien mienta solo se engaña a sí mismo—; aquí
sería una toma de cuenta completa: cualquiera se registra con un correo suyo,
envía el ajeno y entra en la empresa de otro con el rol de esa invitación.

Como el token de **acceso** de Cognito no lleva `email`, este endpoint —y solo
este— exige el de **identidad**, que lo lleva junto a `email_verified` y firmado.
La pasarela valida los dos por igual (el `aud` de un token de identidad es el
identificador del cliente, que es justo lo que comprueba el autorizador), pero no
distingue cuál es cuál: la comprobación de `token_use=id` y `email_verified=true`
vive en el controlador. Sin la segunda, darse de alta declarando el correo de
otra persona bastaría para reclamar su invitación.

Se descartaron dos alternativas: el disparador *pre-token-generation* V2 de
Cognito, que añadiría `email` al token de acceso pero exige el nivel Essentials
del pool (coste mensual) y otra Lambda; y un código de invitación de un solo uso,
que es el flujo definitivo cuando exista SES pero hoy habría que pasar a mano.

**Lo que queda sin resolver a propósito:** si dos cuentas invitan al mismo correo
—`email` es único por cuenta, no en la instalación— se responde 409 y lo arregla
un humano. Elegir por la persona la metería en la empresa equivocada sin que
nadie se entere.

---

### Entrega 5 · Roles a medida · **M** — **ENTREGADA**

9 pruebas.

**Lo que cambió respecto a lo planeado:**

- **El código del rol se deriva del nombre, no lo escribe el usuario.** Es un
  identificador interno con índice único por cuenta: pedírselo solo serviría para
  que choque. Duplicar dos veces «Contador junior» da `CONTADOR_JUNIOR` y
  `CONTADOR_JUNIOR_2`.
- **La copia arrastra los permisos del original.** Empezar en blanco sería más
  simple de programar y peor de usar: quien duplica «Vendedor» quiere «Vendedor y
  además esto», no reconstruir cuarenta casillas.
- **Los predefinidos responden 409, no 403.** No es un problema de quién eres: ese
  rol no lo modifica nadie. Un 403 sugeriría que otro usuario sí podría.
- **La invalidación acompaña a toda operación que altere permisos**, no solo a la
  que los edita: duplicar crea un rol con permisos, y eliminar los quita.

**La prueba que justifica `permisos_version`:** un usuario con rol a medida
recibe 403, se le concede el permiso, la petición **siguiente** da 200, se le
revoca, y vuelve a 403. Sin invalidación por versión, el tramo de revocación
seguiría dando 200 con el permiso ya quitado de la base — conceder tarde es una
molestia, revocar tarde es un agujero.

---

### Comprobantes · parcial · **ENTREGADA**

Solo qué tipos emite la empresa, como marca §1.1. Emitir, anular y consultar el
estado ante SUNAT son de C1.

**La decisión que conviene no olvidar:** la tabla `tipo_comprobante_empresa`
guarda **decisiones, no estado completo**. Una empresa sin ninguna fila tiene los
cinco tipos habilitados; solo aparece fila cuando alguien decide algo distinto de
lo predeterminado.

No es un atajo. `RegistrarCuenta` corre **sin contexto de empresa** —es el caso de
uso que la crea— así que no puede insertar en una tabla con RLS; es el mismo
motivo por el que el alta tampoco escribe en `auditoria`. Con «sin fila =
habilitado» no hay nada que sembrar, ni en el alta ni en la migración para las
empresas que ya existían.

**Y la declaración hace efecto:** `Series.registrar` la consulta antes de dar de
alta una serie, y un tipo con series activas no se deja apagar. Una pantalla de
configuración que no cambia el comportamiento de nada es una pantalla que el
cliente rellena para nada.

El permiso `configuracion.comprobante` se añade en la V5 y **se asigna a mano** a
Administrador y Contador: los `INSERT` por patrón de la V2 ya corrieron, y un
permiso creado después no lo recoge nadie. Es el comportamiento que la V2
describía —lo nuevo permanece cerrado hasta que alguien decide lo contrario— y
aquí alguien lo decide.

---

## 5. Riesgos y decisiones abiertas

| | |
|---|---|
| **`sucursal` y `usuario_empresa` siguen fuera de RLS** | Excepción documentada por el problema de arranque del contexto: son las tablas que hay que leer *para saber* cuál es la empresa activa. Al añadir `almacen` conviene revisar si la frontera sigue siendo la correcta |
| **El contexto cuesta ~4 consultas por petición** (DT-D12) | Aceptable hoy. Se resuelve con una vista cuando el p95 lo pida |
| ~~**La aplicación no se despliega** (DT-D17)~~ | **Resuelto.** `ondexia.api` produce un artefacto de Lambda verificado con un evento real de API Gateway. Ver §5.bis |
| **Generador de cliente sin decidir** | Ver Entrega 0 |

---

## 5.bis Reestructuración a hexagonal y adaptador de Lambda — cerrado

La reestructuración a la arquitectura del [doc 08](08-arquitectura-backend.md)
está **terminada**: 32/32 pruebas en verde, incluidas las 9 reglas de ArchUnit.
El adaptador de Lambda (DT-D17) también, así que **DT-D17 deja de ser deuda**.

### Lo que se construyó para desplegar

| Pieza | Dónde |
|---|---|
| Adaptador de entrada | `infrastructure/entrada/lambda/ManejadorLambda` |
| Migraciones separadas | `infrastructure/entrada/lambda/ManejadorMigraciones` |
| Perfil de plataforma | `application-aws.yml` |
| Empaquetado plano | `maven-shade-plugin` en `ondexia.api/pom.xml` |
| SnapStart + alias | `ondexia.infra/api.tf` |

### Cinco cosas que costaron y conviene no volver a descubrir

**El fat jar de Spring Boot no sirve.** Lleva los jars anidados y un cargador
propio que sabe leerlos; el de Lambda no. De ahí el shade.

**Los transformadores del shade no se escriben a mano.** Los hereda
`spring-boot-starter-parent`, y son más correctos que los que uno escribiría:
para `spring.factories` usa `PropertiesMergingResourceTransformer` y no
`AppendingTransformer`, porque es un archivo de propiedades y hay que combinar
por clave. Declararlos aparte además rompe el build: Maven fusiona los
`<transformer>` hermanos por posición.

**No excluir `org.apache.tomcat.embed:*` con comodín.** Se lleva
`tomcat-embed-el`, que no es el servidor sino lo que Hibernate Validator usa
para interpolar mensajes. Falla al construir el `EntityManagerFactory`, que no
se parece en nada a la causa.

**SnapStart solo actúa sobre versiones publicadas.** Estaba sin `publish`, sin
alias y con la integración apuntando a `$LATEST`: se podía activar y no servir
de nada.

**Flyway fuera del arranque.** En Lambda «al arrancar» son N arranques en frío
simultáneos compitiendo por el candado, dentro de peticiones de usuario con 29 s
de límite.

### Dos más que solo aparecieron en la nube

**Los JAR multi-release no funcionan en Lambda.** El artefacto arrancaba en
local y moría en AWS con `Virtual threads not supported on JDK <21` — sobre el
runtime `java21`. `spring-core` distribuye `VirtualThreadDelegate` como
multi-release: una versión base que lanza esa excepción y otra real en
`META-INF/versions/21/`. Las dos estaban en el artefacto y el manifiesto
declaraba `Multi-Release: true`.

Pero esas reglas **solo valen para JARs**. Lambda descomprime el zip en
`/var/task` y pone el *directorio* en el classpath, así que
`META-INF/versions/21/` pasa a ser una carpeta cualquiera. Se ve en la traza:
`~[task/:na]`, no `~[ondexia-api.jar:...]`.

Se apagaron los hilos virtuales en el perfil `aws`, que además es lo correcto:
un contenedor de Lambda atiende una petición a la vez.

**La cuenta nueva tiene un límite de concurrencia de 10, no de 1000.** Y AWS
exige dejar 10 sin reservar, así que ninguna función puede reservar nada:
`concurrencia_reservada_api` pasó a `-1`. La reserva era lo que sustituía al
RDS Proxy acotando conexiones; el propio límite de 10 hace ahora ese trabajo
(10 × 2 de Hikari = 20 conexiones). Antes de prod hay que pedir la ampliación.

### Verificado, no supuesto

El artefacto se invocó con un evento real de API Gateway HTTP API v2 contra el
jar aplanado y devolvió **200** en `/salud`. El plan de `dev` da **65 recursos,
cero errores**.

De paso apareció un problema del entorno local que las pruebas no pueden ver:
un **PostgreSQL 18 nativo de Windows** ocupaba `0.0.0.0:5432` y la aplicación
hablaba con él en vez de con el contenedor. Testcontainers publica en un puerto
libre al azar, así que la suite pasaba en verde mientras la aplicación local no
arrancaba. El contenedor pasó a publicar en **5433**.

## 6. Qué queda después

Terminado este módulo, lo que falta para C1 es: producto (mínimo), cliente,
boleta y su detalle, y la máquina de estados del comprobante. Sin emisión ante
SUNAT, C1 se convierte en «registrar una boleta de principio a fin», que es
exactamente la Fase 1 del DTE §10.3 — demostrable a un cliente potencial, con
datos sintéticos, sin infraestructura pagada.
