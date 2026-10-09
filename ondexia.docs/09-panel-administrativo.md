# Ondexia — Plan: panel administrativo interno

Estado: **propuesta** · Fecha: 2026-08-14 · Deriva de [04 · Organización y suscripción](04-organizacion-y-suscripcion.md) §5 y [07 · Plan backend Configuración](07-plan-backend-configuracion.md)

Consola para el personal de Ondexia: ver las cuentas cliente, su consumo frente
a los límites del plan, cambiar de plan, suspender o reactivar, y decidir a qué
módulos tiene acceso cada cuenta.

---

## 1. Qué sustituye

El doc 04 §5 dejó esto fuera de la v1.0 con una frase que ya venció:

> Cambiar de plan es una gestión comercial fuera de la aplicación **por ahora**.

«Fuera de la aplicación» hoy significa `UPDATE cuenta SET plan = …` a mano contra
la base de producción. Eso no escala más allá de los primeros clientes y, sobre
todo, **no deja rastro**: la tabla `auditoria` registra lo que pasa por la
aplicación, no lo que alguien escribe con un cliente SQL.

El panel no es una comodidad, entonces. Es el sitio donde esos cambios pasan a
ser auditables.

## 2. Decisiones ya tomadas

| Pregunta | Decisión |
|---|---|
| A quién gestiona | **Cuentas cliente y sus usuarios.** El personal de Ondexia se administra desde la consola de Cognito |
| Dónde vive | **Aplicación y Lambda separadas**, con su propio rol de base de datos |
| Alcance de la primera entrega | Listar cuentas, ver consumo frente a límites, cambiar plan, suspender y reactivar, y controlar el acceso a módulos. **Sin cobros** |

Queda fuera por decisión explícita: pasarela de pago, facturación de la propia
suscripción y alta de cuentas nuevas desde el panel.

## 3. Lo que el código dice hoy

Tres hallazgos de la revisión previa. Los dos primeros condicionan el diseño; el
tercero es un requisito previo.

### 3.1 El aislamiento por RLS cubre cinco tablas, no todas

Tienen política de fila: `almacen`, `identidad_visual`, `serie_correlativo`,
`tipo_comprobante_empresa` y `auditoria`. **No la tienen** `cuenta`, `empresa`,
`usuario`, `sucursal`, `usuario_empresa`, `rol` ni `rol_permiso`.

Es coherente con el DTE §5.1 —la política filtra por `empresa_id`, y esas tablas
o están por encima de la empresa o son catálogo global— pero tiene una
consecuencia que conviene decir en voz alta: **entre `cuenta` y `cuenta`, hoy la
frontera es el código, no la base de datos.**

Para el panel es una buena noticia: lee justo lo que necesita sin pelearse con
RLS. Para el resto es una deuda que crece en cuanto haya un segundo consumidor
de la misma base, y este panel es exactamente ese segundo consumidor. Por eso el
rol de base de datos del §6.2 no es un detalle de infraestructura.

### 3.2 La API de inquilinos puede escribir su propio plan

La V8 concedió a `ondexia_app` CRUD sobre todas las tablas, `cuenta` incluida.
Es decir: **hoy un fallo en la API de clientes puede subir de plan a su propia
cuenta o levantarse una suspensión.** Nada lo explota, pero nada lo impide.

Se corrige con permisos por columna, que PostgreSQL soporta:

```sql
REVOKE UPDATE ON cuenta FROM ondexia_app;
GRANT  UPDATE (permisos_version, actualizado_en) ON cuenta TO ondexia_app;
```

La API sigue pudiendo invalidar su caché de permisos y no puede tocar `plan` ni
`estado_suscripcion`. El panel, con su propio rol, sí.

### 3.3 El grupo de personal existe y no está conectado

`aws_cognito_user_pool.personal` se creó desde el primer día
([identidad.tf:212](../ondexia.infra/identidad.tf)) y no tiene cliente, ni
autorizador, ni una línea de código que lo use. Su **MFA está en OFF**, con un
recordatorio pendiente desde entonces.

**Codificado, no recordado:** el MFA de ese grupo está en `ON` y una
`precondition` de Terraform aborta el `plan` de producción si alguien lo baja
([identidad.tf](../ondexia.infra/identidad.tf), `aws_cognito_user_pool.personal`).
Una consola que ve todas las cuentas cliente detrás de solo usuario y contraseña
no se despliega, y ahora eso no depende de que nadie se acuerde.

> **Corregido el 2026-09-01, hallazgo A4 de la auditoría.** Esto decía
> «requisito previo, no negociable: el MFA pasa a `ON` antes de que el panel
> exista», y el README de infraestructura lo repetía en una lista titulada «Lo
> que Terraform no hace por ti». El MFA seguía en `OPTIONAL` cuatro meses
> después.
>
> Regla que sale de aquí: **una obligación previa al despliegue se codifica o se
> comprueba con `precondition`; no se deja como recordatorio.** Si de verdad no
> se puede codificar —el rol de PostgreSQL de las migraciones es el caso—, el
> documento dice por qué no se puede, no solo qué hay que acordarse de hacer.

## 4. Modelo de datos

### 4.1 El plan deja de ser un enumerado y pasa a ser una tabla

Hoy `cuenta.plan` es un `varchar(30)` con un CHECK de tres valores y **los
límites no existen en ninguna parte**. No se puede mostrar «3 de 5 usuarios» sin
saber que el plan da 5.

```
plan (codigo PK, nombre, max_empresas, max_usuarios, orden, activo)
cuenta.plan  →  FK a plan(codigo), se cae el CHECK
```

Se elige tabla y no constantes en Java por una razón concreta: el doc 04 §2.3
deja los precios y los nombres comerciales pendientes de validación, y el §2.2
prevé un plan «a demanda» **negociable**. Un límite negociable por cliente no
cabe en un enumerado.

Los tres códigos vigentes en la base —`ESENCIAL`, `PROFESIONAL`, `CORPORATIVO`—
se siembran tal cual. No coinciden con los nombres del doc 04 (Base, Intermedio,
A demanda); esa discrepancia se resuelve en la tabla, que es donde ahora se
puede cambiar sin migrar.

### 4.2 Los módulos por cuenta guardan decisiones, no el estado completo

```
cuenta_modulo (cuenta_id, permiso_id, habilitado, motivo, decidido_en)
```

`permiso_id` apunta a una fila de nivel `MODULO` o `SUBMODULO` del catálogo que
ya construyó la V6. **La ausencia de fila significa «lo que dicte el plan».**

Es el mismo idioma que la V5 usó para los tipos de comprobante, y por el mismo
motivo: si la tabla tuviera que estar completa, habría que sembrarla para cada
cuenta al crearla y volver a sembrarla cada vez que aparezca un módulo nuevo. Con
decisiones, un módulo nuevo entra por su plan sin tocar una sola fila de cliente.

El plan aporta el valor por omisión mediante `plan_modulo (plan_codigo,
permiso_id)`, que es lo que hace operativa la tabla de diferenciación del doc 04
§2.2 —GRE, roles personalizados, acceso por API, reportes avanzados—.

### 4.3 Quitar un módulo no borra los permisos que había dentro

Cuando una cuenta pierde un módulo, **los roles que otorgaban permisos ahí se
quedan como están**. La comprobación es en tiempo de ejecución, no una poda.

Es lo contrario de lo que hace la pantalla de roles, donde guardar poda lo que se
queda sin padre, y la diferencia es deliberada: allí quien apaga el módulo es el
dueño de la configuración y ve lo que pierde; aquí quien lo apaga somos nosotros,
sobre datos ajenos. El doc 04 §2.2 ya fijó la regla para la baja de plan —«nunca
borrar datos; los excedentes pasan a solo lectura»— y esto es el mismo caso.

Consecuencia práctica: si la cuenta vuelve a contratar el módulo, su
configuración de roles reaparece intacta.

## 5. Cómo se evalúa

`Permisos.puede(submodulo, accion)` exige hoy tres cosas a la vez: el módulo, el
submódulo y la función. Pasa a exigir **cuatro**, y la nueva va primero:

```
la cuenta tiene contratado el módulo
  ∧ el rol tiene  modulo:acceder
  ∧ el rol tiene  submodulo:acceder
  ∧ el rol tiene  submodulo:accion
```

Dos cosas que no pueden faltar:

- **Se comprueba en el servidor.** Ocultar el menú en el SPA es presentación, no
  control. Un módulo no contratado tiene que devolver 403 aunque alguien escriba
  la URL a mano.
- **La caché ya tiene por dónde invalidarse.** `cuenta.permisos_version` existe
  justamente para eso; cambiar el plan o un módulo la incrementa, igual que
  hacerlo con un rol.

### 5.1 Qué ve el cliente cuando su cuenta no está activa

El aviso se muestra **al iniciar sesión**, después de autenticar. La identidad es
correcta —la persona es quien dice ser—; lo que no está vigente es el contrato,
y son dos cosas distintas que conviene no mezclar en el mismo error.

| Estado | Puede consultar y descargar | Puede emitir a SUNAT | Qué se muestra al entrar |
|---|---|---|---|
| `EN_PRUEBA` | Sí | **No** | Días restantes y qué pasa al terminar |
| `ACTIVA` | Sí | Sí | Nada |
| `SUSPENDIDA` | **Sí** | No | Aviso con el motivo y cómo regularizar |
| `CANCELADA` | **Sí** | No | Aviso de cuenta cerrada y cómo exportar sus datos |

> **`EN_PRUEBA` corrige al doc 04 §2.4**, que proponía la prueba gratuita
> «emitiendo contra la beta de SUNAT». Emitir queda fuera de la prueba: el
> cliente configura, carga catálogo y recorre el producto, pero nada sale hacia
> SUNAT. Evita además exigirle certificado digital y credenciales SOL antes de
> haber decidido si compra. Queda por precisar si durante la prueba el comprobante
> se genera y se queda en borrador, o si la acción de emitir no está disponible.

**El aviso es solo informativo.** Da el estado y una dirección de correo de
contacto. No hay botón que lleve a ningún flujo de pago dentro de la aplicación,
que es coherente con el §7: los cobros no existen todavía en el producto.

**Suspender nunca cierra el acceso a los datos.** Los comprobantes tienen
obligación de conservación de cinco años (DTE §5.8) y el cliente responde ante
SUNAT por ellos: dejarle fuera de sus propios documentos por una factura impaga
sería trasladarle un problema tributario por un problema comercial. Se corta
emitir, que es lo que genera obligaciones nuevas, y nada más.

#### La regla que fija la forma del mensaje

**Ondexia no pide datos de pago desde un aviso, nunca.** El mensaje explica la
situación y remite a un canal nuestro que el cliente ya conoce; no lleva un botón
que desemboque en un formulario de tarjeta.

No es una manía. Un aviso de «tu suscripción venció → pulsa aquí → introduce tu
tarjeta» es literalmente la silueta de la estafa de suplantación más común, hasta
en el detalle del «no te cobraremos nada». Un producto que entrena a sus clientes
a seguir ese flujo los deja indefensos el día que alguien nos suplante a nosotros.

De ahí, tres reglas para redactarlo:

1. **Sin urgencia fabricada.** Ni cuentas atrás, ni «actúa ahora». La situación
   real ya es suficiente motivo.
2. **Sin gancho.** Nada de regalos, extensiones gratuitas ni promociones dentro
   de un aviso de estado. Es el anzuelo característico del fraude.
3. **Decir qué sigue funcionando**, y decirlo primero. «Puedes seguir
   consultando y descargando tus comprobantes» tranquiliza y, de paso, es la
   frase que ningún phishing escribe.

## 6. Arquitectura

### 6.1 Por qué separada y qué cuesta

Un módulo Maven `ondexia.admin` que depende de `ondexia.domain`, su propia
Lambda, su propia API HTTP, su propio SPA y el grupo de personal como
autorizador. Las migraciones **se quedan donde están**, en `ondexia.api`: una
sola pieza es dueña del esquema, y el panel nunca migra.

La alternativa —rutas nuevas en la aplicación de clientes— es menos trabajo y
tiene un fallo que no se puede acotar después: un error en el autorizador deja de
ser un error y pasa a ser escalada de privilegios entre inquilinos. Separado, la
API de clientes **no tiene credencial** con la que leer otras cuentas, aunque su
código se equivoque.

### 6.2 El rol de base de datos es lo que hace real la separación

`ondexia_panel`, autenticación por IAM como `ondexia_app`, y concesiones **solo
sobre las tablas que necesita**: `cuenta`, `plan`, `plan_modulo`,
`cuenta_modulo`, `empresa`, `usuario`, `usuario_empresa`, `rol`, `permiso`,
`auditoria`.

Sin concesión sobre `serie_correlativo`, `almacen`, `identidad_visual` ni las
tablas de comprobantes que vengan. La propiedad que se obtiene es verificable:
**el panel no puede leer los documentos tributarios de un cliente**, y no porque
el código no lo intente, sino porque la base se lo niega.

### 6.3 Sin SnapStart, pero con versiones publicadas

La Lambda de la consola no lleva SnapStart. Son pocas invocaciones al día de
gente que trabaja aquí, un arranque en frío de segundos es tolerable, y se evita
lo que más ha costado en los despliegues de la API: publicar una versión con
instantánea tarda minutos y convierte cualquier fallo de arranque en una versión
en `Failed`.

Sí publica versiones y sirve a través de un alias, que es una decisión distinta
aunque en la API vayan juntas. Servir `$LATEST` es servir un blanco móvil: lo que
corre cambia con cada despliegue sin que quede constancia de qué había antes, y
deshacer obliga a reconstruir el artefacto. Con el alias en medio, revertir es
moverlo a la versión anterior.

### 6.4 Quién valida el token

**Las dos.** `TokenDeLaPasarela` verifica la firma contra el JWKS del grupo de
personal, que Terraform descarga al aplicar e inyecta en la función; la pasarela
la verifica también, antes de invocar.

> **Corregido el 2026-09-01, hallazgo A2 de la auditoría.** Este párrafo decía
> «la pasarela, no la aplicación», y lo justificaba así: «el permiso de
> invocación está atado al ARN de esa API: **no hay otra puerta**».
>
> Esa premisa era falsa cuando se escribió. La ruta `OPTIONS /{proxy+}` de esa
> misma API no llevaba autorizador y apuntaba a la misma función: había una
> puerta sin cerradura. Que no se ejecutara ningún controlador era casualidad de
> que todos declararan método; un `@RequestMapping` sin método bastaba para
> convertirlo en toma total de la consola.
>
> Y la prueba que acompañaba a la decisión, `unaFirmaQueNadieVerificaPasa`,
> fijaba la **excepción** en vez de la premisa: dejaba constancia verde de que
> una firma falsa se aceptaba.
>
> Regla que sale de aquí: **toda premisa de seguridad externa lleva en el mismo
> commit el test que la verifica.** Aquí ese test es `FirmaDelTokenIT`, y la
> premisa que se verifica ya no es «no hay otra puerta» —eso no lo puede
> comprobar la aplicación— sino la que sí depende de nosotros: que un token que
> no firmó Cognito se rechaza.

No es una optimización, es lo único que funciona en esta red. El decodificador
que Spring construye a partir de `issuer-uri` descarga la configuración del
emisor, y la función vive en una subred privada sin NAT cuyo grupo de seguridad
solo permite el 5432 hacia la base y el 443 hacia S3. La descarga se agotaba, el
contexto no levantaba y la pasarela devolvía **502**:

```
Unable to resolve the Configuration with the provided Issuer of
"https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXXXXXXX"
Caused by: java.net.SocketTimeoutException: Connect timed out
```

Se descartaron dos alternativas:

- **Copiar SnapStart de `ondexia.api`**, que es lo que hace que allí esa descarga
  funcione: al crear la instantánea la función todavía no está enganchada a la
  red privada, sale a internet y el JWKS queda dentro de la foto. Sale gratis,
  pero apoya la validación en un detalle de plataforma que AWS no ha prometido, y
  congela las claves de Cognito hasta el siguiente despliegue — si Cognito rota
  una, deja de validar. **Esto es deuda de `ondexia.api`**, no un patrón a
  extender.
- **Un punto de enlace de interfaz para Cognito**, ~7.30 USD/mes sobre un
  presupuesto de 15 en dev, para repetir una comprobación ya hecha.

Lo que se pierde, dicho con todas las letras: si algún día se añade otra ruta sin
autorizador, u otro disparador sobre esta misma función, la aplicación aceptaría
un token que nadie verificó. La prueba `unaFirmaQueNadieVerificaPasa` deja esa
decisión escrita en algo que se ejecuta, y el comentario del
`aws_lambda_permission` marca el sitio exacto donde dejaría de ser cierta.

### 6.5 Coste

### 6.4 Coste

| Pieza | Coste mensual |
|---|---|
| Lambda | Dentro de la capa gratuita |
| API Gateway HTTP | ~0 con este volumen |
| S3 + CloudFront del SPA | Dentro de la capa gratuita |
| Rol de base de datos | 0 |
| Cognito (grupo ya creado) | 0 |

Del orden de **1 USD/mes**, contra un presupuesto de 15 en dev.

### 6.5 Quién puede qué (auditoría 2026-09-01, hallazgos A5 y A6)

Hasta el 2026-09-01 **todo el personal era superadministrador**: no había
grupos ni autorización por endpoint, y la única frontera era estar dentro o
fuera del pool. La primera cuenta que se le creaba a alguien —para consultar una
duda— venía con la capacidad de suspender el servicio de un cliente.

Ahora hay dos grupos de Cognito en el pool de personal, y solo dos:

| Grupo | Puede |
|---|---|
| `soporte` | Leer: listar cuentas y sus módulos |
| `operaciones` | Todo lo anterior y cambiar plan, suspender, decidir módulos |

La pertenencia **no está en Terraform** a propósito: quién está en cada grupo
es un dato de personal, no de infraestructura, y codificarlo significaría que dar
de baja a alguien exige un despliegue. Se asigna en la consola de Cognito.
**Sin grupo, la cuenta autentica y no puede hacer nada**: un token sin
`cognito:groups` llega sin ninguna autoridad.

Dos detalles que no son evidentes:

- Spring busca las autoridades en `scope`/`scp`, que Cognito no emite. El
  conversor de `SeguridadAdmin` traduce `cognito:groups` con prefijo `ROLE_`;
  sin él, «estar en el pool» era la única comprobación posible.
- La autorización va en la cadena de filtros por patrón y método, **no en
  `@PreAuthorize` por endpoint**: un endpoint nuevo sin anotación quedaría
  abierto a cualquiera del pool. Lo que no esté cubierto cae en `anyRequest()`,
  que exige `operaciones`.

La bitácora (`auditoria_admin`) guarda desde la V15 el `sub` del operador
además de su correo —el correo lo cambia su dueño, el `sub` no— y es de solo
inserción por disparador, como la de clientes. Y la pasarela del panel tiene
registro de acceso con el `sub` del autorizador y un techo de 5 rps.

## 7. Entregas

| # | Entrega | Contenido |
|---|---|---|
| 0 | **Requisito previo** · CODIFICADO | MFA del grupo de personal en `ON`, impuesto por una `precondition` de Terraform en prod (hallazgo A4). Ya no es un recordatorio |
| 1 | **Esquema** · ENTREGADA | Tablas `plan`, `plan_modulo`, `cuenta_modulo`, `auditoria_admin`; FK de `cuenta.plan`; límites negociables por cuenta; permisos por columna del §3.2; rol `ondexia_panel`. Todo en la V9 |
| 2 | **Comprobación** | El cuarto conjunto en `Permisos`, con pruebas. Sin panel todavía: se verifica que un módulo apagado devuelve 403 |
| 3 | **Infraestructura** | Módulo Maven, Lambda, API, cliente de Cognito, bucket y distribución del SPA |
| 4 | **Consola: lectura** | Listado de cuentas, ficha con consumo frente a límites |
| 5 | **Consola: escritura** | Cambio de plan, suspensión y reactivación, módulos por cuenta. Todo a `auditoria_admin` |

> La bitácora del panel **no puede ser `auditoria`**. Esa tabla tiene política de
> fila por `empresa_id` y las acciones del panel son sobre una *cuenta*: una fila
> con `empresa_id` nulo no satisface `empresa_id = empresa_actual()` y la base la
> rechaza — que es exactamente lo que debe hacer. Debilitar esa política para
> encajar ahí algo que no es de una empresa habría sido el error. De ahí
> `auditoria_admin`, creada en la V9.

La 2 antes que la 3 no es casual: **la comprobación tiene que existir antes que
la pantalla que la manipula.** Al revés se construye un panel que promete un
control que el servidor todavía no aplica.

## 8. Nombres

El proyecto se llama **`ondexia.admin`**: `apps/frontend/ondexia.admin` para el
SPA y `apps/backend/ondexia.admin` para el módulo Maven.

El rol de base de datos **no** puede llamarse igual. `ondexia_admin` ya existe:
es el usuario maestro de la instancia RDS, el dueño de todas las tablas y el que
usa la función de migraciones. Reutilizar ese nombre daría al panel exactamente
los privilegios que el §6.2 quiere negarle, y además lo dejaría exento de las
políticas de fila por ser propietario. El rol del panel es **`ondexia_panel`**.

## 9. Decisiones pendientes

1. **Suplantación para soporte.** Poder «entrar como» un cliente resuelve la
   mitad de las consultas de soporte y es la funcionalidad más peligrosa de un
   panel así. Recomendación: fuera de esta entrega, y cuando entre, con registro
   en `auditoria` y aviso visible al cliente.
2. **Redacción concreta de los avisos del §5.1**, que es trabajo de producto más
   que de código y conviene escribir con los mensajes delante.
