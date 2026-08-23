# ondexia.web

Aplicación web de Ondexia: gestión comercial y facturación electrónica para
empresas peruanas. Angular con Tailwind CSS.

**Configuración ya consume `ondexia.api`.** El resto de módulos —Panel,
Almacén, Compras y Ventas— sigue con datos de ejemplo.

## Requisitos

- Node.js 22 o superior
- **pnpm** — la versión la fija `packageManager` en `package.json`, así que
  `corepack enable` basta para tener la correcta. No uses `npm`: no hay
  `package-lock.json` y `npm install` generaría un árbol distinto del que se
  construye en CI.

## Puesta en marcha

```bash
pnpm install --frozen-lockfile
```

```bash
pnpm start
```

Queda servida en `http://localhost:4200`.

## Compilar para producción

```bash
pnpm run build
```

## Pruebas

```bash
pnpm test -- --watch=false
```

Las de maquetación tienen dos trampas propias, explicadas en
[10 · Convenciones de interfaz](../../../ondexia.docs/10-convenciones-de-interfaz.md)
§12: el diseño de teléfono hay que probarlo dentro de un **iframe** —las
consultas de medios miran la ventana, no el contenedor— y los estilos hay que
copiarlos por texto, porque un `<link>` carga de forma asíncrona y la prueba
mediría el DOM sin CSS.

## Organización del código

```
src/
├── app/
│   ├── pages/                  una carpeta por módulo del sistema
│   │   ├── acceso/             ingresar, recuperar contraseña, sin permisos
│   │   ├── almacen/            productos, existencias, guías, kardex
│   │   ├── compras/            proveedores, órdenes, facturas de compra
│   │   ├── ventas/             clientes, comprobantes, notas de crédito
│   │   ├── configuracion/      empresa, series, usuarios, roles, suscripción
│   │   ├── componentes/        galería de los componentes transversales
│   │   ├── panel/              panel principal
│   │   └── no-encontrado/      404
│   └── shared/
│       ├── components/comunes/ los componentes que comparten las vistas
│       ├── layout/             marcos de la aplicación y del acceso
│       ├── services/           contexto, tema, menú, modales
│       └── pipe/
├── styles.css                  el tema: color, tipografía, sombras
└── index.html
```

### Componentes transversales

Las setenta vistas no repiten estructura: la comparten. Antes de crear una
pantalla nueva conviene mirar si encaja en uno de estos, porque duplicarlos es
el error más caro que admite este frontend.

| Componente            | Qué resuelve                                              | Vistas |
|-----------------------|-----------------------------------------------------------|--------|
| `tabla-datos`         | Listado con orden, paginación, selección y estado vacío    | ~30    |
| `editor-documento`    | Cabecera, detalle de líneas y totales de un documento      | 14     |
| `listado-documentos`  | Listado de comprobantes con filtros y estado ante SUNAT    | 13     |
| `desplegable`         | Selector con estilo propio, accesible por teclado          | 19     |
| `editor-lineas`       | Detalle con cantidades, afectación al IGV y totales        | —      |
| `buscador-entidad`    | Búsqueda de cliente, proveedor o producto                  | —      |
| `kardex`              | Movimientos valorizados por promedio ponderado             | —      |
| `confirmacion`        | Diálogo previo a una acción irreversible                   | 13     |
| `encabezado-pagina`   | Título y ruta de navegación                                | 29     |
| `selector-contexto`   | Empresa y establecimiento activos                          | —      |

Ver todos en marcha: `/componentes`.

## Convenciones

**El código está en español**, incluidos nombres de clases, métodos y rutas.
El dominio es tributario peruano y traducir «comprobante» o «afectación» a
inglés para volver a traducirlos al hablar con el usuario solo añade ruido.
Las excepciones son las que impone el marco de trabajo: `ngOnInit`, `@Input`,
y la clase `dark` que espera el variante de Tailwind.

**Los colores viven solo en `styles.css`.** Las vistas usan tokens semánticos
—`brand`, `success`, `error`, `warning`, `gray`, `blue-light`— y nunca valores
literales. `success` y `error` cargan significado contable, así que no se usan
por decoración.

**El resto de convenciones de interfaz** —contraste, movimiento, tablas,
paneles flotantes, foco de teclado— está en
[10 · Convenciones de interfaz](../../../ondexia.docs/10-convenciones-de-interfaz.md),
con el porqué de cada una.

**Los importes se redondean a dos decimales al calcularlos**, no al mostrarlos.
SUNAT valida que la suma de las líneas cuadre con el total declarado, y
redondear solo en pantalla produce comprobantes rechazados.

**Las cifras van alineadas a la derecha y con `tabular-nums`**, para que la coma
decimal quede en la misma columna y se puedan comparar de un vistazo.

## Estado

**Hay sesión y hay persistencia, en un módulo.** `GET /api/v1/contexto` —
identidad, empresas accesibles y permisos efectivos— alimenta el
`selector-contexto` y decide qué entradas del menú se muestran. Configuración
lee y escribe contra la API; los errores llegan como `ProblemDetail` y se
reparten sobre los campos del formulario.

**Panel, Almacén, Compras y Ventas siguen con datos de ejemplo**: los
formularios validan y calculan, pero nada se guarda y nada se envía a SUNAT. Lo
pendiente está en `ondexia.docs/06-notas-de-version.md`.

El contrato vive en `ondexia.contracts/openapi.yaml` y se regenera solo con la
suite del backend. **El cliente se genera desde ahí, no se escribe a mano**: es
la única forma de que un campo renombrado en el backend aparezca al compilar y
no en producción semanas después.

## Terceros

- **Angular** y **Tailwind CSS** — licencia MIT.
- **Outfit**, la tipografía, servida desde `public/fonts/` bajo SIL Open Font
  License 1.1. La licencia va junto a los archivos, en `public/fonts/OFL.txt`.

Los iconos son propios, en SVG dentro del código: trazo de 1.6 y caja de 24.
