# F06: conservación del momento de emisión

Base: develop 408a68f19151564854acd152bdd97f4bc9d2639d.
Decisión del propietario: los documentos deben guardar los datos exactos al emitir;
los artefactos enviados/recibidos de SUNAT se conservan como originales.

Primer bloque: copiar cliente, emisor y local en la inserción del documento,
proteger la copia con el disparador de inmutabilidad vigente y utilizarla en
consulta, cabecera de impresión y construcción de nuevas órdenes de emisión.
Sin datos históricos en documentos anteriores, no consultar maestros para presentar
esos datos como originales. No rellenar documentos antiguos con datos actuales.

Cliente: ID, tipo/número, nombre, dirección. Emisor: RUC, razón social, nombre
comercial, domicilio fiscal, ubigeo. Local: ID, nombre, dirección, ubigeo, código.
Los logos mantienen V27; no se copia ninguna credencial, certificado o permiso.
La captura cubre nota, boleta, factura, canje y notas de crédito al insertar.

Oráculo: Cliente al emitir / Dirección al emitir permanece después de editar a
Cliente después de emitir / Dirección posterior. Precio 80 y descripción
Instalacion a domicilio permanecen después de cambiar el producto a 90 / Servicio posterior.
La cabecera conserva los datos capturados aunque los maestros se editen.

Se demuestra regresión roja sobre código anterior, después verde; controles SQL
negativos de edición y aislamiento, contratos y UI pertinentes. Representación de
legado explícita; recuperar datos antiguos solo de originales verificables es otro
bloque. No acceso a AWS, no migraciones históricas editadas, no regla fiscal nueva.

Segundo bloque: conservar ZIP enviado y XML/CDR originales, impedir sobrescritura,
retener identidad de intentos/artefactos y distinguir originales de regeneraciones.
Inventario confirmado: XML/CDR se escriben con claves reutilizadas, ZIP enviado no
se almacena; S3 tiene versionado, sin expiración de documentos. No existe PDF original
persistido en el flujo inspeccionado; la impresión del navegador es representación.
El cierre de este primer bloque no acredita la conservación completa de archivos.
