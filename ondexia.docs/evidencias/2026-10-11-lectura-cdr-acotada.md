# ACT-17 · F24 — lectura local acotada de XML/ZIP externos

Base develop `2d22377e419489f9aa44dedc8f8fd994655cfe44`.
[Especificación](../especificaciones/2026-10-11-lectura-cdr-acotada.md) y
[registros comprimidos con SHA-256](2026-10-11-lectura-cdr-acotada.json).

## Pruebas ejecutadas

Sobre implementación previa: diez pruebas, cinco fallos y dos errores. Siete
casos muestran ausencia de límites de XML descomprimido, entradas previas,
cantidad de entradas y SOAP; dos variantes de base64 escapan como excepción;
diagnóstico de XML mal formado aparece en descripción y stderr. No son siete
vulnerabilidades distintas ni prueba de agotamiento o explotación en AWS.

Después del arreglo: 22 focales correctas inicialmente; suite final con
**17 pruebas nuevas**, **130 de dominio y 69 de facturación**, sin fallos/errores.
Los oráculos son límites y conteos fijados por la especificación, bytes de
fixtures y códigos existentes del dominio, sin usar valores observados como
esperados. La frontera ZIP STORED usa cabeceras definidas del formato.

Casos adicionales: límites exactos de ZIP/descompresión total/SOAP/32 entradas,
carpeta y `.XML`, archivos sin XML/no ZIP, preservación byte por byte del SOAP
válido y negativa de inclusión externa con archivo público temporal local.
No se contacta SUNAT ni terceros, ni se emplean secretos de clientes.

Mutaciones aisladas del lector:

- Admitir DTD: un fallo; la entrada antes inválida se interpreta como ticket.
- Ignorar código CDR: dos fallos; constancias válidas dejan de ser aceptadas.
- Activar XInclude: un fallo de clasificación (`SIN_TICKET` → `HTTP_200`);
  la respuesta sigue bloqueada. No acredita filtración ni anulación de las
  demás defensas. Se distingue de los dos controles anteriores.

Restauración byte por byte del lector después de cada ensayo; suite final verde.
Maven offline/JDK 21 con dependency-check omitido localmente: no es evidencia
SCA. CI remoto y del merge pendientes al registrar; se documentarán en el PR.

## Límites y avance

No se limita aún el cuerpo durante descarga HTTP, que ya llega materializado
al lector. No se midió presupuesto global de recursos ni se valida firma,
esquema o asociación del CDR. XML interno de firma, proveedor real y auditoría
independiente pendientes. ACT-17 y F24 siguen parciales.

Se registra también la decisión del propietario: validación con originales
reales cuando exista un caso en producción; no bloquea trabajo verificable con
escenarios sintéticos y no equivale a recuperación real comprobada.
