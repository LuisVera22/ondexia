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

**Requisito previo, no negociable:** el MFA de ese grupo pasa a `ON` antes de que
el panel exista. Una consola que ve todas las cuentas cliente detrás de solo
usuario y contraseña no se despliega.

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

## 6. Arquitectura

### 6.1 Por qué separada y qué cuesta

Un módulo Maven `ondexia.consola` que depende de `ondexia.domain`, su propia
Lambda, su propia API HTTP, su propio SPA y el grupo de personal como
autorizador. Las migraciones **se quedan donde están**, en `ondexia.api`: una
sola pieza es dueña del esquema, y el panel nunca migra.

La alternativa —rutas nuevas en la aplicación de clientes— es menos trabajo y
tiene un fallo que no se puede acotar después: un error en el autorizador deja de
ser un error y pasa a ser escalada de privilegios entre inquilinos. Separado, la
API de clientes **no tiene credencial** con la que leer otras cuentas, aunque su
código se equivoque.

### 6.2 El rol de base de datos es lo que hace real la separación

`ondexia_consola`, autenticación por IAM como `ondexia_app`, y concesiones **solo
sobre las tablas que necesita**: `cuenta`, `plan`, `plan_modulo`,
`cuenta_modulo`, `empresa`, `usuario`, `usuario_empresa`, `rol`, `permiso`,
`auditoria`.

Sin concesión sobre `serie_correlativo`, `almacen`, `identidad_visual` ni las
tablas de comprobantes que vengan. La propiedad que se obtiene es verificable:
**el panel no puede leer los documentos tributarios de un cliente**, y no porque
el código no lo intente, sino porque la base se lo niega.

### 6.3 Sin SnapStart, a propósito

La Lambda de la consola no lleva SnapStart. Son pocas invocaciones al día de
gente que trabaja aquí, un arranque en frío de segundos es tolerable, y se evita
lo que más ha costado en los despliegues de la API: publicar una versión con
instantánea tarda minutos y convierte cualquier fallo de arranque en una versión
en `Failed`.

### 6.4 Coste

| Pieza | Coste mensual |
|---|---|
| Lambda | Dentro de la capa gratuita |
| API Gateway HTTP | ~0 con este volumen |
| S3 + CloudFront del SPA | Dentro de la capa gratuita |
| Rol de base de datos | 0 |
| Cognito (grupo ya creado) | 0 |

Del orden de **1 USD/mes**, contra un presupuesto de 15 en dev.

## 7. Entregas

| # | Entrega | Contenido |
|---|---|---|
| 0 | **Requisito previo** | MFA del grupo de personal en `ON` |
| 1 | **Esquema** | Tablas `plan`, `plan_modulo`, `cuenta_modulo`; FK de `cuenta.plan`; permisos por columna del §3.2; rol `ondexia_consola` |
| 2 | **Comprobación** | El cuarto conjunto en `Permisos`, con pruebas. Sin panel todavía: se verifica que un módulo apagado devuelve 403 |
| 3 | **Infraestructura** | Módulo Maven, Lambda, API, cliente de Cognito, bucket y distribución del SPA |
| 4 | **Consola: lectura** | Listado de cuentas, ficha con consumo frente a límites |
| 5 | **Consola: escritura** | Cambio de plan, suspensión y reactivación, módulos por cuenta. Todo a `auditoria` |

La 2 antes que la 3 no es casual: **la comprobación tiene que existir antes que
la pantalla que la manipula.** Al revés se construye un panel que promete un
control que el servidor todavía no aplica.

## 8. Decisiones pendientes

1. **Nombre.** `consola` en este documento. Alternativas: `panel`, `admin`,
   `interno`. Afecta a nombres de módulo, bucket y repositorio de código.
2. **Qué ve una cuenta suspendida.** Suspender no puede borrar datos —hay
   obligación de conservación de 5 años— así que lo razonable es solo lectura con
   un aviso, no un portazo. Falta decidir si emitir se corta de inmediato.
3. **Suplantación para soporte.** Poder «entrar como» un cliente resuelve la
   mitad de las consultas de soporte y es la funcionalidad más peligrosa de un
   panel así. Recomendación: fuera de esta entrega, y cuando entre, con registro
   en `auditoria` y aviso visible al cliente.
