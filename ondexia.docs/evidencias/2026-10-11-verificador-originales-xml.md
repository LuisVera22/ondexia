# F06 — verificador local de candidatos XML

Base develop `fb5c9fc1637e3dff0e6b53f5a5680063c057338a`.
[Especificación](../especificaciones/2026-10-11-verificador-originales-xml.md),
[procedimiento existente](../operacion-recuperacion-originales.md) y
[registros con SHA-256](2026-10-11-verificador-originales-xml.json).

## Evidencia ejecutada

- TDD de capacidad nueva: stub sin verificación, **7 fallos de 7 pruebas**;
  implementación inicial verde. No son siete defectos previos del producto.
- Suite final: **19 pruebas nuevas**, incluidas consola, SHA-256 por defecto,
  SHA-1 legado explícito, identidad 01/03/07 y negativos de XML/firma.
- Una firma válida sobre un fragmento se comprueba con JDK antes de exigir
  su rechazo por el verificador: no protege el comprobante entero.
- Mutación aislada que ignora el resultado de la firma: **2 fallos**.
  Mutación que afirma confianza y aceptación: **3 fallos**.
  Mutación que permite DTD: **2 fallos**, incluido XML firmado con DTD sin entidades.
  Cada mutación se restauró byte por byte antes de continuar.
- Suite completa de dominio y facturación verde: **130 + 52 pruebas**.
  Maven offline/JDK 21 con dependency-check omitido localmente: no es evidencia SCA.
- Entrada real de consola Java ejecutada con certificado público de prueba y
  XML sintético con DTD: JSON `XML_NO_ADMITIDO`, salida 1, bytes conservados.
  La consola positiva y rechazo de alteración se ejercitan también en JUnit.
- Oráculos: valores esperados fijados en fixtures, no extraídos de la salida;
  certificado autofirmado generado para pruebas, sin material privado en Git.

## Límites

No original de cliente, AWS/SUNAT real, backup o recuperación histórica inspeccionado.
No se verifican confianza de cadena, revocación, titular jurídico, vigencia en
el momento de emisión ni aceptación tributaria. SHA-1 se admite únicamente
como compatibilidad explícita de examen, sin cambiar producción ni declararlo
seguro para nuevas firmas. Una verificación positiva no autoriza restaurar datos.
Auditoría independiente y PDF persistido pendientes; el PDF en servidor requiere
decisión de fase. F06 sigue parcial. CI remoto pendiente al registrar esta evidencia;
el resultado del head y del merge se documentará en el PR.
