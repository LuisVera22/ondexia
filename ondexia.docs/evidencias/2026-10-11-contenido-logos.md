# Evidencia de validación del contenido de logos

Base: develop `3548944c92df1c5482a309e76780ad3385b2f8d2`.
Rama: `feature/validar-contenido-logos`. ACT-16/F24, alcance parcial.
[Especificación](../especificaciones/2026-10-11-contenido-logos.md).

## Defecto, oráculo y arreglo

DTE §5.8 exige comprobar PNG/JPG por bytes. Antes, confirmar usaba solo
HeadObject/MIME/tamaño. El ensayo conserva esa lógica y añade al doble una
subida de bytes explícitos para reproducir entradas ajenas; no fabrica datos
válidos según el MIME. De cuatro pruebas iniciales, **tres fallaron** porque
SVG con MIME PNG, JPEG con MIME PNG y PNG truncado no se rechazaron. Un PNG
válido pasó. No se afirma que los casos añadidos después se ejecutaran en rojo.

El caso de uso comprueba contenido mediante el puerto antes de guardar/auditar.
Los adaptadores reconocen formato, acotan expansión y decodifican; lectura S3
máximo límite+1 y aborto al exceso/fallo de lectura. Sin dependencia nueva ni
ampliación IAM: api.tf ya declara GetObject para marca. No se probó IAM efectivo.

## Verificación local

| Ensayo | Resultado |
|---|---|
| Regresión inicial sin arreglo | 4 pruebas, 3 fallos, 0 errores |
| Primer verde integración/identidad/venta/arquitectura | 39 correctas |
| Validador y adaptador S3 con dobles | 13 correctas |
| Mutación: omitir frontera de empresa | 1 prueba, 1 fallo |
| Mutación: omitir concordancia formato real/MIME | 9 pruebas, 2 fallos |
| Final restaurado: 6 contenido, 11 identidad, 14 venta, 9 parser, 4 S3, 9 arquitectura, 2 contrato | **55 correctas**, 0 fallos/errores/omitidas |

Restauración byte a byte comprobada con cmp. Un primer intento de mutación usó
una ruta equivocada y no modificó el archivo: **descartado como evidencia**.
La ejecución válida posterior produjo el fallo documentado. OpenAPI/modelos
regenerados sin diff; no cambio de esquema HTTP ni de migraciones.

Comando final desde apps/backend, tras activar el setup de esta nube:

```bash
./mvnw -o -B -s /workspace/.ondexia-setup/maven.xml \
  -pl ondexia.api -am -Ddependency-check.skip=true \
  -Dtest=ContenidoDeLogoIT,IdentidadVisualIT,PuntoDeVentaIT,ValidadorDeImagenTest,AlmacenDeMarcaS3Test,ArquitecturaTest,ExportarContratoIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Los logs comprimidos y SHA-256 están en el JSON homónimo. Datos sintéticos, sin
llamadas a S3 real. El ensayo local omite SCA; CI completo de PR y develop se
comprobará y registrará en el PR antes de cerrar su rama.

## Costos y límites

Un GET adicional por confirmación y lectura de hasta 1 MiB más el byte testigo,
usando el endpoint S3 existente; no NAT nuevo. Límite técnico 4.194.304 píxeles
antes de decodificar. No es presupuesto probado de memoria/CPU/tiempo global ni
objetivo visual; no se midieron costo real, latencia Lambda o arranque en AWS.

No demuestra antivirus, ausencia de toda carga adicional, protección contra
todos los formatos corruptos, cabeceras/CDN reales ni firma del proveedor.
Tampoco impide reutilizar el PUT firmado, sustituir bytes después de confirmar
o servir por CDN una clave conocida antes de confirmar. Inmutabilidad/publicación
siguen pendientes de ACT-16. Sin cambios de UI; feedback HTTP con código y
mensaje fijo comprobado, no revisión completa móvil/WCAG. Auditoría independiente
pendiente; no se despliega ni modifica main.
