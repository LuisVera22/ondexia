# F06 — inventario previo a recuperar documentos antiguos

Base: develop `98d7d6f8c6e168eb48f4bfb76729b7024ef99fb7`.
El propietario aprobó conservar lo disponible sin inventar información cuando
no exista fuente fiable. Antes se agotan originales, versiones y respaldos.

## Corte autorizado y flujo

Operador selecciona una copia aislada del prefijo `documentos/`, nunca la raíz
del bus con certificados y credenciales. La herramienta recibe esa carpeta
explícita, comprueba su existencia y enumera XML/ZIP/PDF y referencias
`envio.json`/`recepcion.json`. Calcula tamaño y SHA-256 de los archivos regulares
sin extraer ZIP, analizar XML, conectarse a AWS o escribir en la fuente.
El informe JSON sale por stdout; el operador lo guarda fuera de la fuente.

Estados: `CANDIDATO_LOCALIZADO`, `NO_INVENTARIADO` con motivo (enlace,
archivo no regular, tamaño excedido, error de lectura o cambio durante lectura).
Una carpeta sin candidatos conserva `BUSQUEDA_PENDIENTE`: no acredita pérdida.
Ningún estado implica autenticidad, recuperabilidad, aceptación SUNAT o
verificación criptográfica. SHA-256 calculado al inventariar no autentica la
procedencia ni sustituye una referencia independiente anterior.

Límite operativo configurable: 64 MiB por archivo por defecto, no fiscal.
Archivos mayores no se borran: requieren inventario controlado con límite mayor.
Usar copia estable sin escrituras concurrentes; no se afirma protección contra
un atacante que sustituya directorios durante el recorrido. Los enlaces no se
siguen. Informe con identificadores sensibles: mantener acceso restringido;
no confirmar originales ni informes reales en Git.

## Aceptación y pruebas

Bytes literales `abc` tienen SHA-256 conocido fijado en la prueba, independiente
de la salida. XML/ZIP/PDF permanecen byte por byte iguales tras el inventario.
Referencias se distinguen de documentos; certificados/credenciales se excluyen.
Enlaces se omiten; carpeta ausente falla; carpeta vacía no indica irrecuperable;
archivo mayor al límite se registra sin truncarlo ni tratarlo como original.
TDD de la capacidad nueva no se presentará como defecto del sistema anterior.

## Fuera de alcance

No restaura datos históricos, no modifica migraciones, no genera PDF, no verifica
firma/certificado ni vincula automáticamente artefactos a documentos en BD.
No habilita IAM ni consulta AWS/SUNAT. Sin acceso a las fuentes reales no se
clasifican comprobantes de clientes como irrecuperables.
