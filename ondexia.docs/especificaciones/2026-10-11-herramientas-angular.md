# ACT-19/23: avisos críticos de herramientas Angular

Base: develop `3f97241acc26f6da237869a0ef2247e8a0d10508`.
Complementa la revisión de Dependabot y su backlog, sin migrar a Angular 22.

## Problema, regla y alcance

La auditoría npm de los lockfiles web/panel informa piscina 5.2.0 y proxy-addr
2.0.7 con avisos críticos. Son rutas de herramientas dev; no se ha demostrado
exposición de los sitios publicados ni explotación AWS. Protegemos la cadena
de compilación y desarrollo, sin atribuir beneficio financiero cuantificado.

Oráculo externo: GHSA-67c8-pqhq-4rmx declara piscina corregida desde 5.3.2;
GHSA-jqcg-44mw-7w3h declara proxy-addr corregida desde 2.0.8. La aceptación
del lote exige cero avisos críticos en auditoría completa de ambos árboles
y funcionamiento de los builds y suites existentes. Otros avisos se registran
y no quedan aceptados como excepción por este lote.

Actualizar únicamente @angular/build y @angular/cli a la línea 21.2.26 en
web y panel, con sus locks. Metadata npm de build incluye piscina 5.3.2 y de
CLI incluye MCP SDK 1.31.0. Resolver proxy-addr por el rango admitido por
su padre; no forzar una versión incompatible ni añadir overrides sin análisis.
Mantener core 21, TypeScript 5.9, Node 22, API, datos, migraciones y controles CI.

## Flujo de verificación y extensiones

1. Ejecutar `pnpm audit --audit-level=critical --json` antes de modificar.
   Código 1 debe corresponder a avisos válidos; un fallo de red no prueba defecto.
2. Resolver versiones seleccionadas, revisar diff y peers. Si una resolución
   requiere salto mayor o excepción de control, detener esa variante y caracterizar.
3. Instalar con `--frozen-lockfile`; si falla, el lote no está listo.
4. Repetir auditoría completa. Código 0 y cero críticos satisfacen esta puerta;
   si quedan críticos, no presentar el arreglo como logrado. Guardar avisos
   residuales, versiones y rutas. Auditar --prod aparte, sin confundir cobertura.
5. Ejecutar builds y suites web/panel; comparar con requisitos vigentes,
   sin generar esperados a partir de la salida. No hay cambio de UX previsto;
   un fallo de compatibilidad exige investigación y regresión pertinente.
6. Publicar PR develop; verificar CI completo del mismo head, merge con SHA
   esperado y CI del merge antes de retirar exclusivamente su rama.

La comprobación roja/verde de este lote es el análisis independiente de avisos
antes/después, más la regresión existente. No se ejecuta exploit ni se inventa
un test de aplicación que copie números de versión. No cerrar #27: propone
otra versión y otra migración. La landing y los saltos restantes son otros lotes.

## Fuentes y pendientes

Fuentes: registro npm versionado y notas oficiales Angular CLI 21.2.26,
conservadas en la evidencia. El verde de --prod no cubre automáticamente dev.
Pendiente: auditoría independiente, alcanzabilidad/explotación, ausencia de
avisos futuros, otros paquetes/backend, configuración AWS y operación nueva
de Dependabot. ACT-19/23 continúan parciales.
