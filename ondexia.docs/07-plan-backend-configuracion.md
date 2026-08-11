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

### Entrega 1 · Empresa y establecimientos · **M**

Las entidades ya existen. Falta todo lo demás.

- `GET`/`PUT` de la empresa activa; CRUD de establecimientos.
- Validación de RUC: formato y dígito verificador. **No contra SUNAT** (§1.2).
- Regla real: **el RUC se bloquea cuando ya hay comprobantes emitidos.** Hoy no
  hay comprobantes, así que la regla se escribe con la comprobación preparada y
  se activa en C1.
- Auditoría en cada cambio.
- Frontend: pantallas de empresa y establecimientos conectadas.

---

### Entrega 2 · Almacenes · **S**

Entidad nueva `almacen` (`empresa_id`, `sucursal_id`, `nombre`).

Es deliberadamente pequeña y va aquí por un motivo: es **la primera tabla que
estrena `activar_aislamiento_empresa()`**. Valida la función de RLS sobre una
tabla de negocio real con una entidad tan simple que, si algo falla, el fallo
solo puede estar en el aislamiento.

---

### Entrega 3 · Series y correlativos · **L**

La pieza crítica del módulo.

- Entidad `serie_correlativo` y CRUD.
- Validación del formato de serie según tipo de documento (catálogo 01).
- **Servicio de asignación con `SELECT … FOR UPDATE`** (DTE F-02). Nunca
  `SEQUENCE`: no es transaccional y deja huecos ante cualquier rollback.
- El correlativo **se asigna al confirmar, jamás al abrir un borrador**. Un
  borrador abandonado con número asignado es un hueco permanente.

**La prueba que más rinde de todo el módulo:** N hilos pidiendo correlativo de la
misma serie a la vez, verificando **cero duplicados y cero huecos**. Es barata de
escribir y es la única forma de saber que el bloqueo funciona — un fallo de
concurrencia no se reproduce a mano.

Al terminar esta entrega, el cimiento de C1 está completo. Se podría pivotar a la
boleta sin esperar a las dos siguientes.

---

### Entrega 4 · Usuarios y asignaciones · **M**

- Alta de usuario, asignación a empresa con rol y alcance de sucursal.
- Activar y desactivar. El invariante de `cuenta_administrador` ya lo defiende la
  base con un disparador diferido.

> **El alta en Cognito la hace el SPA, no el backend.** La Lambda no tiene salida
> a internet (DTE §4.8), y resolverlo «de forma natural» exigiría un endpoint de
> interfaz a ~7.30 USD/mes — más caro que la NAT que se evitó. El backend crea la
> fila `usuario` sin `cognito_sub`; se vincula en el primer acceso. Ya está
> modelado así en la entidad.

---

### Entrega 5 · Roles a medida · **M**

- Duplicar un rol predefinido y editar los permisos de la copia. Los
  predefinidos son inmutables a propósito: si cada cliente pudiera editar
  «Vendedor», la palabra dejaría de significar lo mismo entre clientes.
- Matriz de permisos por `(modulo, accion)`.
- **Prueba de que revocar un permiso surte efecto en la petición siguiente**, sin
  esperar a que el contenedor de Lambda se recicle. Es lo que justifica el
  `permisos_version` de la cuenta.

---

## 5. Riesgos y decisiones abiertas

| | |
|---|---|
| **`sucursal` y `usuario_empresa` siguen fuera de RLS** | Excepción documentada por el problema de arranque del contexto: son las tablas que hay que leer *para saber* cuál es la empresa activa. Al añadir `almacen` conviene revisar si la frontera sigue siendo la correcta |
| **El contexto cuesta ~4 consultas por petición** (DT-D12) | Aceptable hoy. Se resuelve con una vista cuando el p95 lo pida |
| **La aplicación no se despliega** (DT-D17) | `ondexia.api` no produce artefacto de Lambda. Todo este módulo se desarrolla y prueba en local, que es lo que la Fase 0 contempla |
| **Generador de cliente sin decidir** | Ver Entrega 0 |

---

## 6. Qué queda después

Terminado este módulo, lo que falta para C1 es: producto (mínimo), cliente,
boleta y su detalle, y la máquina de estados del comprobante. Sin emisión ante
SUNAT, C1 se convierte en «registrar una boleta de principio a fin», que es
exactamente la Fase 1 del DTE §10.3 — demostrable a un cliente potencial, con
datos sintéticos, sin infraestructura pagada.
