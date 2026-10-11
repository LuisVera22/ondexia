# Validar contenido de logos antes de referenciarlo

ACT-16/F24. Base develop `3548944c92df1c5482a309e76780ad3385b2f8d2`.
Fuente: DTE §5.8 exige PNG/JPG por bytes, no extensión/MIME; CLAUDE conserva
historia. Corrección técnica autorizada; no añade proveedor ni cambia tributación.

Flujo: autorizar con reglas actuales → subir → confirmar empresa/hueco y metadata
→ comprobar bytes con lectura acotada → reconocer formato y dimensiones →
decodificar → guardar referencia/auditoría. Rechazo no cambia identidad anterior.
Fallo de lectura es incertidumbre técnica, no aceptación ni contenido inválido
demostrado. Se conservan códigos/mensajes fijos y permisos existentes.

Oráculos: SVG/texto con MIME PNG, JPEG con MIME PNG y PNG truncado deben
rechazarse sin guardar; PNG/JPG de 2×3 píxeles deben aceptarse. Tamaño máximo
del hueco vigente; S3 lee como máximo límite+1 y aborta al exceder. Antes de
decodificar se limita expansión a 4.194.304 píxeles como protección técnica;
no es objetivo visual ni garantía de memoria/CPU global. La cabecera permite
rechazar dimensiones excesivas sin crear un bitmap de ese tamaño.

Diseño: caso de uso consume capacidad del puerto AlmacenDeMarca; ImageIO y
SDK quedan en adaptadores. Doble usa bytes explícitos, nunca fabrica imagen
válida por MIME. Sin dependencia nueva, migración ni cambio de esquema HTTP.
Pruebas de contrato/modelos verificarán que el corte no cambia su generación.

Límites: no acredita antivirus, ausencia de todo contenido adicional, disponibilidad
AWS, cabeceras servidas ni origen privado. La validación ocurre antes de guardar
la referencia; no impide servir por CDN un objeto subido si se conoce su clave.
La reutilización del PUT y una sustitución posterior todavía requieren un corte
de inmutabilidad/publicación. No cerrar ACT-16 por esta corrección parcial.
