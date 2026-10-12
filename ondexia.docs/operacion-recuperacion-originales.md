# F06 — búsqueda y recuperación de originales antiguos

Estado: procedimiento preparado; no ejecutado contra AWS ni documentos reales.
Decisión del propietario, 2026-10-11: el proyecto sigue en desarrollo; la validación
con originales reales se realizará cuando exista un caso en producción. Mientras
tanto se verifican escenarios y archivos sintéticos, sin bloquear el desarrollo
ni dar por comprobada la recuperación real.
Complementa las especificaciones de conservación; no sustituye el backlog ni
permite reemitir o corregir un documento enviado a SUNAT.

## 1. Acceso y preparación

Primera fuente: bucket de emisión y sus versiones, mediante una identidad de
solo lectura en el entorno autorizado. Confirmar cuenta, región, entorno y
bucket con el propietario; no deducirlos de un nombre parecido ni usar otra
identidad. No compartir claves en chats, repositorio o informes.

Permisos necesarios: `s3:GetBucketVersioning` sobre el bucket;
`s3:ListBucketVersions` limitado por `s3:prefix` a `documentos/` y
`documentos/*`; `s3:GetObject` y `s3:GetObjectVersion` exclusivamente en
`arn:aws:s3:::BUCKET/documentos/*`. No escritura, borrado ni acceso a
`certificados/` o `credenciales/`. Son requisitos de configuración, no un rol
creado o probado por este documento. El rol del Emisor no debe ampliarse para
hacer inventarios operativos; la API no tiene permisos de listado de versiones.

En la sesión inspeccionada no hay identidad AWS, CLI AWS ni destino S3 permitido
por la política de red. Antes de ejecutar, configurar acceso mediante el entorno
y contrastar el resultado efectivo. No se ensayó IAM ni se consultó AWS.

## 2. Búsqueda sin alterar el origen

1. Obtener la lista de documentos antiguos sin datos históricos y sus referencias
   XML/CDR con consultas de lectura autorizadas. Registrar empresa, identificador
   del documento, tipo, serie/número y estado, sin incluir credenciales.
2. Comprobar el versionado efectivo. Enumerar **todas** las páginas de versiones
   y marcadores de borrado bajo `documentos/`. Un marcador de borrado no implica
   que sus versiones anteriores hayan desaparecido. No seleccionar solo `IsLatest`.
3. Descargar versiones candidatas por `VersionId` explícito a copias aisladas;
   registrar bucket, clave, versión, fecha y responsable de obtención. No
   sobrescribir candidatos distintos con el mismo nombre local. ETag no se
   considera SHA-256 ni prueba de firma o autenticidad.
4. Inventariar cada copia estable. La herramienta no consulta AWS ni confirma
   la procedencia. Conservar la relación entre ruta local y fuente/versionId
   separadamente; el informe solo contiene rutas relativas, tamaño y SHA-256.
5. Si falta el original, revisar respaldos, versiones anteriores y copias
   descargadas/enviadas/recibidas existentes. Registrar cada fuente realmente
   consultada, su alcance, fecha y resultado; ausencia de acceso no equivale
   a ausencia de documento. No prometer que SUNAT permite descargar cualquier
   original: depende del documento y acceso efectivamente disponibles.

Comandos de referencia (no ejecutados; variables seleccionadas del entorno):

```bash
aws s3api get-bucket-versioning --bucket "$bucket_emision"
aws s3api list-object-versions --bucket "$bucket_emision" --prefix documentos/
aws s3api get-object --bucket "$bucket_emision" --key "$clave_documento" \
  --version-id "$version_documento" "$copia_del_original"
python3 herramientas/inventariar_originales_legado.py \
  --documentos "$carpeta_copia_documentos" > "$informe_fuera_de_la_fuente"
```

Mantener paginación del CLI habilitada y usar un destino nuevo por versión.
Guardar el informe fuera de la fuente, con acceso restringido. No subir
originales ni informes reales a Git. Herramienta para Linux/Python 3.10 o superior;
usa apertura sin seguir enlaces y lectura no bloqueante de archivos especiales.
El límite por archivo es operativo/configurable; superar el límite no destruye
ni declara perdido el archivo. Ejecutar sobre copias estables sin cambios concurrentes.

## 3. Verificación antes de vincular o recuperar datos

Cada candidato requiere evidencia independiente: vínculo de empresa/documento,
tipo/serie/número, RUC, fecha, contenido y procedencia. Comprobar firma XML e
integridad de referencias, certificado y contexto aplicables mediante un
verificador específico; no basta encontrar un elemento `Signature` o que el
XML sea legible. El inventario **no implementa esa verificación**.

El [verificador local de candidatos XML](especificaciones/2026-10-11-verificador-originales-xml.md)
comprueba la integridad de una firma del documento completo y contrasta una
identidad esperada independiente. No verifica confianza del certificado ni
aceptación SUNAT; no cambia el estado del inventario ni recupera datos.
Usar un certificado **público** X.509 DER obtenido por una fuente independiente,
sin `.pfx` ni contraseña, y conservar evidencia de su procedencia. No extraer
el certificado del mismo candidato como única referencia de confianza.

Tras compilar el módulo con JDK 21, desde la raíz del repositorio:

```bash
java -cp apps/backend/ondexia.facturacion/target/classes \
  com.ondexia.facturacion.recuperacion.ComprobarOriginalXml \
  "$copia_del_xml" "$certificado_publico_independiente" \
  "$ruc_esperado" "$tipo_esperado" "$serie_numero_esperados"
```

Para XML legado con RSA-SHA1/SHA1, agregar `--permitir-sha1-legado` de forma
explícita; no cambia el firmador de producción. Código 0 indica únicamente
integridad comprobada dentro del perfil admitido; 1 candidato rechazado; 2
entradas operativas ilegibles/no admitidas. Guardar informe fuera de la fuente.
Límites: XML 8 MiB, certificado 64 KiB; copia estable en directorios de confianza.

Contrastar CDR con el comprobante correspondiente; un CDR acredita una respuesta,
no todos los datos originales de la venta. Un hash recién calculado permite
identificar bytes y comparar copias, pero no demuestra origen, confianza del
certificado ni aceptación por SUNAT. Una representación PDF regenerada no es
el PDF original; no cambiar esa clasificación por su apariencia.

Por campo histórico registrar fuente y condición: presente verificable, ausente
o contradictorio. No inferir logo, nombre comercial o identidad interna del local
si la fuente no los contiene. No completar con maestros actuales. Si versiones
contradictorias no pueden resolverse, conservarlas y dejar la vinculación pendiente.

## 4. Resultado por documento

- **Búsqueda pendiente:** falta consultar una fuente relevante o no hay acceso.
- **Candidato localizado:** existe un archivo; todavía falta validar su relación
  y autenticidad. Es el único hallazgo positivo que emite la herramienta actual.
- **Recuperación verificable parcial/completa:** solo después de validar las
  fuentes y campos; incluir prueba, responsable y referencia de los originales.
- **Original no recuperable:** fuentes pertinentes agotadas sin evidencia fiable.
  Conservar datos/archivos disponibles y señalar faltantes; no fabricar información
  ni sustituir un documento emitido. Esta política fue aprobada por el propietario.

La herramienta no declara los dos últimos estados. Una carpeta vacía mantiene
la búsqueda pendiente. La eventual recuperación debe añadir procedencia sin
eludir la inmutabilidad del documento ni editar migraciones aplicadas: diseño
pendiente del bloque de vinculación, no una operación implementada aquí.

## 5. Cierre y límites de este bloque

Preparación verificable con archivos sintéticos, prueba de no modificación,
omisión de enlaces/archivos especiales y mensajes fijos ante errores. CI prueba
la herramienta mediante el descubrimiento Python ya existente. No demuestra
recuperación real, autenticidad XML, restauración de backups ni conformidad AWS.
F06 continúa abierto: acceso/fuentes reales, confianza del certificado,
contraste de CDR y vinculación, PDF persistido y ensayo AWS. El verificador
local complementa este procedimiento; sus pruebas no prueban originales reales.
PDF en servidor pendiente de decisión de fase. Auditoría independiente pendiente.
