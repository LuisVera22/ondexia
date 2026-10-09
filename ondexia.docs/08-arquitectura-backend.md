# Ondexia — Arquitectura del backend

Estado: **decidido (v1)** · Fecha: 2026-08-11 · Sustituye a [03 · Estructura](03-estructura-repositorio.md) §4.1 · Deriva de [DTE-ONX-001](DTE-ONX-001_sistema_gestion_comercial.md) §3

Arquitectura hexagonal con DDD táctico. Este documento fija dónde va cada cosa
y, sobre todo, **por qué**, para que la próxima decisión no dependa de recordar
una conversación.

> **Sustituye a la v1 del backend.** El esqueleto del 2026-08-11 se construyó con
> «hexagonal pragmática»: agregados con anotaciones de JPA y sin capa de
> infraestructura. No se sostuvo por un motivo concreto y verificable —lo que el
> documento prometía y lo que había en disco no coincidían— y se reestructuró
> antes de añadir el segundo dominio. Ver §9.

---

## 1. Dos módulos Maven, y por qué exactamente dos

```
ondexia-domain              no depende de nada
      ▲
ondexia-api                 application + infrastructure
      ▲
ondexia-facturacion         (futuro) también depende de domain
```

**`ondexia-domain` es un módulo aparte porque hay que compartirlo.** DT-13 separa
el motor de facturación del núcleo comercial, y el Emisor necesita leer el mismo
`Comprobante` que escribió el núcleo. Sin módulo propio no hay forma de
compartir el modelo sin arrastrar la API entera.

Es decir: **la frontera de módulo existe donde hay una necesidad real**, no
porque la arquitectura hexagonal se dibuje con cuatro cajas.

### 1.1 Lo que sale gratis

Como `ondexia-domain` no declara `jakarta.persistence` ni Spring, **el
compilador impone la pureza del dominio**. No es una convención que alguien
tenga que recordar: una entidad con `@Entity` en ese módulo no compila.

La frontera entre `application` e `infrastructure` vive dentro de
`ondexia-api` y la sostiene ArchUnit (§8). Es un muro más blando, y es
deliberado: separarlas en módulos añadiría fricción diaria a cambio de proteger
una línea que se cruza dos o tres veces al año.

### 1.2 Por qué no cuatro módulos

Cuatro `pom.xml`, un reactor más lento, navegación entre capas que cambia de
módulo y refactores que dejan de ser «mover un archivo». Con un solo
desarrollador (R-13) esa fricción se paga todos los días, y lo que compra
—convertir en error de compilación lo que ArchUnit ya detecta— no lo vale.

**Pasar de dos módulos a cuatro después es mecánico**, porque los paquetes ya
están separados. Al revés no.

---

## 2. El árbol

### 2.1 `ondexia.domain` — el negocio, sin tecnología

```
com.ondexia.domain
├── comun/
│   ├── Ruc, Ubigeo, Importe          value objects
│   ├── ContextoOperacion             quién opera y sobre qué empresa
│   ├── ProveedorDeContexto           PUERTO
│   └── error/                        excepciones de negocio, sin HTTP
├── identidad/
│   ├── Cuenta, Empresa, Sucursal, Usuario…      agregados
│   ├── Permisos                      VO: el conjunto y puede(modulo, accion)
│   └── *Repositorio                  PUERTOS de salida
├── auditoria/
│   ├── Anotacion
│   └── RegistroDeAuditoria           PUERTO
├── almacen/
└── ventas/
```

Sin dependencias. Ni JPA, ni Spring, ni Bean Validation: **las reglas se
comprueban en el constructor**, no con anotaciones que solo actúan si alguien se
acuerda de invocar al validador.

### 2.2 `ondexia.api` — todo lo demás

```
com.ondexia
├── OndexiaApiApplication
├── application/
│   └── <dominio>/                    un caso de uso por clase
│       └── dto/
└── infrastructure/
    ├── entrada/web/<dominio>/        controladores y DTOs del contrato HTTP
    ├── salida/persistencia/<dominio>/ entidades JPA, mapeadores, adaptadores
    ├── salida/auditoria/
    ├── seguridad/
    └── configuration/
```

---

## 3. El corte por dominio, que es lo que hace esto legible

**Arriba se corta por capa; dentro de cada capa, por dominio de negocio.**

Es la diferencia con los ejemplos que circulan de arquitectura hexagonal, que
cortan por capa y no vuelven a cortar. Funciona muy bien con un `Product`. Con
las **38 tablas** que define el DTE §5, `domain/entidades/` sería una carpeta con
Empresa junto a Comprobante junto a Producto junto a OrdenCompra, y
`adaptadores/entrada/rest/` tendría unos treinta y cinco controladores.

Dos consecuencias, y la segunda es la grave:

1. Tocar «series y correlativos» obliga a abrir cuatro carpetas lejanas y
   encontrar el archivo entre treinta y ocho vecinos sin relación.
2. **No hay ninguna frontera entre áreas de negocio.** La hexagonal protege la
   dirección *entre capas*, pero deja `ventas` y `almacen` completamente
   abiertos entre sí — y ese es el acoplamiento que de verdad enreda un ERP.

---

## 4. Los puertos

| Tipo | Dónde | Ejemplo |
|---|---|---|
| **Salida** (el dominio necesita algo) | **En `domain`**, junto a lo que sirve | `EmpresaRepositorio`, `RegistroDeAuditoria` |
| **Entrada** (alguien invoca la aplicación) | **No existen como interfaz** | El controlador depende de `ActualizarEmpresa` directamente |

**Los puertos de salida van en el dominio, no en la aplicación.** Es donde
discrepan los ejemplos habituales. `Comprobante` no se puede razonar sin la idea
de que se guarda y se recupera: el puerto expresa lo que el dominio necesita
para existir. Ponerlo en `application` obligaría al dominio a depender de
`application` para nombrar su propio puerto — o a no tener puerto, que lleva al
dominio anémico donde toda la lógica migra a los servicios de aplicación.

**Los puertos de entrada no se declaran.** La ortodoxia crea
`ActualizarEmpresaUseCase` y su implementación `ActualizarEmpresaService`. Con
una sola implementación —y va a ser una sola, siempre— eso es un archivo extra
por caso de uso y un salto más al navegar, a cambio de ninguna sustitución real.
Si algún día hay dos, extraer la interfaz es refactor mecánico.

---

## 5. Dónde se relaja la ortodoxia, a propósito

Tres sitios. Explícitos, para que no se descubran leyendo el código.

**Los catálogos planos no llevan el juego completo.** `marca`, `modelo`,
`unidad_medida` son nombre y poco más, sin invariantes. Darles agregado +
entidad JPA + mapeador + adaptador es pagar cuatro clases por cero reglas. Para
esos, el caso de uso habla directo con Spring Data.

> **La regla para decidir:** si el agregado no tiene ninguna regla que pueda
> violarse, no necesita modelo de dominio propio.

**Los identificadores tipados solo donde se confunden.** `IdEmpresa` frente a
`UUID` evita pasar el identificador equivocado, que en multiempresa es grave.
Para `marca_id` no aporta nada.

**La capa de aplicación depende de Spring.** `@Transactional` y `@Service`. La
alternativa —decoradores transaccionales a mano— cuesta más de lo que ahorra.

---

## 6. Nomenclatura

| | Regla | Ejemplo |
|---|---|---|
| Capas | Inglés: vocabulario estándar del hexágono | `domain`, `application`, `infrastructure` |
| Dominios | Español | `identidad`, `almacen`, `ventas` |
| Agregados | Español | `Empresa`, `Comprobante` |
| Puertos | Español, sin sufijo tecnológico | `EmpresaRepositorio` |
| Adaptadores | Sufijo con la tecnología | `EmpresaRepositorioJpa` |
| Entidades JPA | Sufijo `Jpa` | `EmpresaJpa` |
| Casos de uso | **Verbo en infinitivo** | `ActualizarEmpresa`, `EmitirBoleta` |

**Un caso de uso, una clase, un método público.** No un `ServicioEmpresa` con
once métodos, que es el sitio donde acaba yendo todo.

Los métodos derivados de Spring Data siguen en inglés dentro del adaptador,
porque el framework analiza el nombre para construir la consulta. Ahí quedan
escondidos, que es exactamente donde deben estar.

---

## 7. Un flujo completo

Actualizar el domicilio fiscal de una empresa:

```
EmpresaController                  infrastructure/entrada/web/identidad
  valida el formato HTTP, traduce EmpresaPeticion
        ↓
ActualizarEmpresa.ejecutar(...)    application/identidad     @Transactional
  lee el contexto por ProveedorDeContexto
        ↓
EmpresaRepositorio.buscar(id)      PUERTO — domain/identidad
        ↓ lo implementa
EmpresaRepositorioJpa              infrastructure/salida/persistencia/identidad
  EmpresaJpaRepository → EmpresaJpa → EmpresaMapeador → Empresa
        ↓
empresa.actualizarDomicilio(...)   domain/identidad
  aquí viven las reglas
        ↓
EmpresaRepositorio.guardar(empresa)
RegistroDeAuditoria.registrar(...) PUERTO
        ↓
devuelve un DTO de aplicación, que el controlador traduce a EmpresaRespuesta
```

**Hay dos juegos de DTO** —los de aplicación y los del contrato HTTP— y es
deliberado: el contrato HTTP se genera al cliente Angular y no debe cambiar
porque cambie un nombre interno.

---

## 8. Lo que verifica ArchUnit

`ArquitecturaTest` convierte estas reglas en fallo de build:

- `domain` no depende de `application` ni de `infrastructure`.
- `domain` no conoce HTTP, seguridad web ni persistencia.
- `application` no depende de `infrastructure`.
- `application` no conoce HTTP: no importa `org.springframework.web`.
- El núcleo comercial no importa firma XML ni clientes de SUNAT (DT-13).
- Los controladores viven en `infrastructure/entrada/web`.
- Sin inyección por campo. Sin `java.util.Date`.

---

## 9. Por qué se cambió la arquitectura anterior

Conviene dejarlo escrito, porque la lección vale más que la decisión.

La v1 del backend adoptó «hexagonal pragmática»: agregados con anotaciones de
JPA, sin capa de infraestructura, y `domain` como módulo compartido. El
argumento era el coste de los mapeadores.

**Ese argumento estaba mal calculado.** Se dijo «duplica unas setenta clases de
modelo» contando *tablas*. DDD cuenta **agregados**, y aquí son del orden de
15-20: un comprobante con sus líneas es uno, no dos. El coste real era la mitad
de lo estimado, y con la exención de los catálogos planos (§5), menos aún.

Pero lo que de verdad la hundió fue otra cosa: **lo documentado y lo construido
no coincidían.** El documento prometía cuatro capas por dominio; en disco había
dos, repartidas entre dos módulos, `infraestructura` no existía en ningún sitio
y el paquete `comun` estaba cortado por capa técnica — justo lo que el mismo
documento argumentaba en contra.

Una arquitectura que solo existe en su documentación no orienta a nadie. Se
detectó al no entenderse leyendo el árbol de carpetas, que es exactamente el
momento y la forma correctos de detectarlo.

**Se cambió con un solo corte vertical construido**, que era el momento más
barato. Cada entrega posterior lo habría encarecido.
