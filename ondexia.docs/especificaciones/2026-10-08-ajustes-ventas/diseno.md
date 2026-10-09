# Diseño propuesto de los ajustes de ventas

Vinculado a [requisitos v1.0](requisitos.md). Estado: diseño implementado basado en DEC-01/02/03 confirmadas por Luis el 2026-10-08. Evidencia local vinculada en requisitos.

## Hallazgos de la inspección

Frontend: `apps/frontend/ondexia.web/src/app/`.

- `pages/ventas/documentos/lista-documentos.component.ts`: `nuevaVenta()` navega siempre a `/ventas/punto-de-venta`.
- `app.routes.ts`: nuevas boletas/facturas redirigen al mostrador general; listados llevan `data.tipo`.
- `pages/ventas/punto-de-venta/punto-de-venta.component.ts`: tipo inicial NV y selector según permisos/régimen; búsqueda/selección de clientes, sin alta contextual.
- `pages/ventas/clientes/ficha-cliente.component.ts`: consulta de RUC, atestación y alta existentes, pero tras registrar navega a la ficha de cliente. Reutilizar su lógica sin copiar esa navegación.
- `pages/ventas/documentos/detalle-documento.component.html`: misma hoja para ticket/A4, leyenda superior NV, totales fiscales y pie específicos.

Backend: `apps/backend/ondexia.api/src/main/java/com/ondexia/`.

- `infrastructure/entrada/web/ventas/ClienteController.java`: listado, alta y verificación con permisos propios; alta retorna cliente creado.
- `infrastructure/entrada/web/ventas/DocumentoVentaController.java`: rutas separadas para NV y comprobantes; el tipo fiscal viene en la petición.
- `application/ventas/DocumentosDeVenta.java`: precios y totales autoritativos, caja, cliente y descarga; no hay motivo demostrado para cambiarlo si DEC-02 es presentación.
- `application/ventas/CanjeDeNotaDeVenta.java` y dominio `DocumentoVenta.canjear`: el canje conserva las líneas de la nota original. Cambiar IGV almacenado afecta al canje; no es un ajuste cosmético.

Se observó aritmética monetaria de frontend existente con `number`, `Math.round` y `Number.EPSILON`. No se acredita que esa capa cumpla el contrato de precisión decimal. El diseño evita introducir más aritmética binaria: usar totales autoritativos para NV y determinar representación decimal en los límites tocados. Una corrección general de importes no se absorbe silenciosamente en estas tres observaciones; si el cambio exige tocar esos cálculos, ampliar diseño/evidencia antes de hacerlo.

## Diseño por capacidad

### Alta contextual

Propuesta: formulario contextual reutilizable de consulta/registro, con resultado «cliente creado/seleccionado» y cancelación. Mantener estado de venta en el componente anfitrión; separar formulario de navegación para que la ficha actual siga operativa. Preferir un diálogo con foco y teclado sobre cambiar de ruta y serializar borradores.

Antes del alta, buscar coincidencia exacta por tipo/número en la empresa actual; el filtro `q` es búsqueda, no prueba de identidad: contrastar los campos. El backend conserva la unicidad ante carrera. Cliente inactivo no se reactiva como efecto lateral. Si la recuperación de un duplicado falla, mostrar conflicto sin reintentar altas indiscriminadamente.

Usar servicios actuales de consultas y ventas, con revisión de datos y «Registrar y usar» acordados en DEC-01. No agregar un endpoint combinado solo por comodidad de UI. Invalidar respuesta antigua por número/contexto; conservar atestación correspondiente. Revisar permisos, casos de denegación y datos mínimos. Ante fallo del padrón se ofrece ingresar datos manualmente con aviso de cliente sin verificar, conservando la regla vigente del alta.

### Nota de venta

DEC-02 confirma presentación: separar resumen NV del resumen fiscal en la hoja compartida. Mostrar el total ya persistido en la etiqueta acordada y el total, sin sumar/restar componentes ni recalcular impuesto. Mover la advertencia a un pie legible; consolidar texto para evitar mensajes duplicados. Ocultar también «Incluye IGV» en la preparación de NV.

Conservar notas de crédito, boletas, facturas, XML/CDR y líneas del canje. Actualizar documentación 12/13 para distinguir cálculo interno de representación. No editar documentos emitidos en base ni migraciones aplicadas. La nueva plantilla puede cambiar cómo se reimprime un documento histórico: informar este efecto y comprobar una NV existente; no prometer preservación visual histórica que el sistema no versiona.

Cambiar aritmética, afectaciones o canje queda fuera del alcance confirmado. Si aparece esa necesidad, especificarla como cambio de negocio aparte; no derivar «sin IGV visible» en exención tributaria.

### Emisión según módulo

Rutas propuestas: `/ventas/notas-venta/nueva`, `/ventas/boletas/nueva` y `/ventas/facturas/nueva`, todas con componente compartido y dato de tipo fijo. Situar rutas específicas antes de patrones que las absorban. Listados y accesos «Nueva venta» contextuales usan ruta correspondiente.

Tipo fijo visible, sin selector, validado antes de cargar series y emitir. Si no está habilitado, mostrar impedimento; nunca elegir otro automáticamente. La ruta no es autorización: servidor mantiene permiso, régimen y controles actuales. DEC-03 conserva selector del mostrador general según permisos y régimen. Cambiar de módulo no debe emitir ni mezclar una serie/respuesta tardía del tipo previo; verificar navegación de ida/vuelta y estado preparado.

## Compatibilidad, seguridad y contratos

No se cambiaron API, OpenAPI, modelos Angular ni migraciones; se reutilizan sus contratos vigentes. Si surge un cambio de API, regenerar OpenAPI y modelos Angular y confirmarlos juntos; no editar solo un consumidor. No crear nuevos permisos ni borrar bases locales. Todo nuevo identificador, comentario, documento y mensaje en español.

Pruebas de permisos, aislamiento, respuesta tardía, cancelación y duplicado se vinculan a requisitos y evidencia. Mock del padrón acredita interacción, no disponibilidad, exactitud ni firma de un proveedor real. No registrar cuerpos de errores o datos personales innecesarios. Cambiar empresa invalida las operaciones anteriores.

Auditoría: sesión distinta recibe requisitos, decisiones vigentes, diff y commit a revisar, sin el relato de implementación. Este diseño y el informe del implementador son evidencia contrastable, no resultado de auditoría independiente.

## Componentes de la implementación

`AltaClienteVentaComponent` encapsula el formulario RUC de la venta y usa el modal común para foco, teclado y cancelación. La ficha existente y el alta contextual comparten `datosAltaCliente` para preparar la petición y vincular atestación/número; no se duplicó ni se amplió el servicio de consultas. El anfitrión mantiene el estado de venta y solo selecciona al cliente al confirmar el alta. El cambio de empresa/local vacía el estado anterior; la consulta y el guardado invalidan respuestas tardías.

Se detectó durante la revisión visual que el marco de navegación se imprimía junto a la hoja. Tras reproducirlo se añadió `@media print` global en `src/styles.css` para ocultar menú/barra/avisos y retirar margen/padding del marco; el helper de navegador demuestra rojo/verde en emulación. La impresora física y la paginación completa siguen sin verificar.
