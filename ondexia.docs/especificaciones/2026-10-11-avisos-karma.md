# ACT-19/23: parches compatibles de Karma y cobertura

Base develop `cc3222efb4c6ca6033f472c036566c83815be16c`.
Continúa el lote de herramientas Angular; no sustituye el backlog existente.

## Objetivo y aceptación previa

Eliminar de los dos árboles los avisos publicados de qs, brace-expansion y
engine.io mediante versiones admitidas por sus padres. Los avisos GHSA fijan
el oráculo; no obtener los esperados de versiones o conteos observados después.

- qs: GHSA-x5fp-wj9c-mxmx y GHSA-4mjr-xmp4-gh2g, corregidos en 6.16.0.
- brace-expansion: GHSA-q2hr-2g5m-vwhr, GHSA-qhr7-859c-m2p7 y
  GHSA-6j4f-fj2g-mc7p, corregidos en 1.1.21 según sus rangos publicados.
- engine.io: GHSA-2gc4-cqfq-p2gv, corregido desde 6.6.10.

Body-parser 1.20.8 admite qs ~6.16.0 y cabe en el rango 1.x de Karma;
minimatch 3 admite brace-expansion ^1.1.7 y Socket.IO admite engine.io ~6.6.0.
Actualizar selectivamente el lockfile, sin añadir dependencias directas,
overrides, nuevas majors, cambio de framework o controles CI. Esfuerzo acotado
de resolución/regresión; protege herramientas, sin retorno monetario medido.

No se declara resuelto braces: el aviso GHSA-vfj7-8cjw-p6xm cita >=3.0.4,
pero el registro devuelve 404 para 3.0.4. Conservar ese pendiente y evaluar
alternativa/alcanzabilidad en otro corte; no aceptar una excepción silenciosa.

## Verificación y extensiones

1. Auditoría completa antes: datos válidos, presencia de los seis avisos objetivo
   y comprobación específica fallida. Fallo de red/datos incompletos no sirve como rojo.
2. Resolver solo parches admitidos; comparar importers/manifiestos. Si cambia
   una dependencia directa o necesita override, revisar antes de aceptar la variante.
3. Auditoría completa después: la comprobación específica debe pasar sin los
   seis avisos, sin críticos y con residuales explícitos. Que pnpm audit siga
   devolviendo 1 por braces no es éxito de toda la auditoría. Auditar prod aparte.
4. Instalaciones congeladas, builds y suites existentes web/panel correctos.
   Los esperados funcionales siguen siendo las reglas vigentes, no salidas nuevas.
5. Publicar PR develop, validar CI completo del mismo head, integrar con SHA
   esperado y verificar el CI del merge antes de borrar exclusivamente su rama.

No hay API, datos, migraciones, importes ni UI nueva. No ejecuta exploits,
terceros ni AWS. No afirma auditoría independiente o seguridad completa del
sistema. Este lote no integra ni cierra propuestas diferentes del bot.
