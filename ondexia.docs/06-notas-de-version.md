# Ondexia — Notas de versión

## v0.1.0 — Recorrido cognitivo del frontend

Fecha: 2026-08-07 · Rama: `release/0.1.0` → `main`

### Qué incluye

Las **73 rutas** de la versión 1.0 navegables de principio a fin, con la
identidad visual de la plantilla TailAdmin adaptada a Ondexia.

| Módulo | Contenido |
|---|---|
| **Acceso** | Iniciar sesión, recuperar contraseña, sin permisos, página no encontrada |
| **Panel** | Pendientes que exigen atención, indicadores, últimos comprobantes, acciones frecuentes |
| **Almacén** | Productos y su ficha, presentaciones, tipos de precio, marcas, modelos, unidades, almacenes, guías de ingreso, guías de remisión, productos por agotarse |
| **Compras** | Proveedores y su ficha, notas de pedido, órdenes de compra y de servicio, notas de compra, facturas de compra, liquidaciones de compra |
| **Ventas** | Clientes y su ficha, cotizaciones, notas de preventa, boletas, facturas, notas de crédito, comunicación de baja, resumen diario, formas de pago, detalle de comprobante |
| **Configuración** | Empresa, identidad visual, establecimientos, series y correlativos, usuarios, roles y permisos, comprobantes, suscripción |

### Qué NO incluye

**No es una aplicación funcional.** Es un recorrido cognitivo navegable:

- Sin backend, sin peticiones HTTP, sin persistencia
- Los datos son constantes escritas en cada componente
- Las validaciones de formato son reales; las de negocio no
- Nada se envía a SUNAT

### Componentes reutilizables construidos

La razón por la que 73 pantallas fueron viables:

| Componente | Pantallas que lo usan |
|---|---|
| `tabla-datos` | 26 |
| `editor-documento` | 12 |
| `listado-documentos` | 11 |
| `editor-lineas` | 12 |
| `buscador-entidad` | 12 |
| `estado-comprobante` | 14 |
| `confirmacion` | 9 |
| `pagina-vacia` | 26 |
| `selector-contexto` | Layout |

Catálogo consultable en `/kit/componentes`.

### Deuda declarada

| # | Deuda | Razón |
|---|---|---|
| 1 | Datos de ejemplo dentro de cada componente | Decisión explícita: se buscaba validar el recorrido antes de escribir backend. Al conectarlo habrá que reescribir la obtención de datos en cada vista |
| 2 | Ninguna pantalla verificada visualmente durante su construcción | El entorno de desarrollo no tuvo navegador disponible. Se garantiza compilación y resolución de rutas, no apariencia |
| 3 | El kit de la plantilla sigue en el menú | Referencia interna mientras se construye. **Debe eliminarse antes de publicar** |
| 4 | Sin pruebas automatizadas | Fuera del alcance de esta entrega |
| 5 | Notas de débito fuera del alcance | Comparten editor con las notas de crédito; incorporarlas es trabajo menor |

### Decisiones del dominio visibles en la interfaz

- La factura advierte si el cliente tiene DNI **antes** de emitir, en lugar de esperar el rechazo de SUNAT
- La nota de crédito se emite desde el detalle del comprobante de origen, nunca desde un formulario en blanco
- La comunicación de baja solo lista comprobantes dentro de los 7 días calendario
- El resumen diario separa ticket de estado, porque «enviado» no significa «aceptado»
- La guía de remisión traslada sin vender: su detalle no lleva precios ni IGV
- La liquidación de compra es el único documento de compras que emite Ondexia, y el único que no exige RUC
- Las existencias son de solo consulta: se modifican con guías de ingreso y ventas
- El RUC de la empresa se bloquea cuando ya hay comprobantes emitidos

### Antes de la siguiente versión

1. Retirar el kit de la plantilla del menú y del enrutador
2. Resolver DT-12: emisión propia ante SUNAT o vía proveedor
3. Definir qué documentos de la cadena de compras son obligatorios (riesgo C-1)
4. Confirmar precios de los planes tras validarlos con prospectos
