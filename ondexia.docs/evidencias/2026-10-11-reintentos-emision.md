# F17 · Sensibilidad de duplicados y reintentos

Base develop `e7f003ed0e56fd24fd0e338032990ff8f2d29134`.
[Especificación previa](../especificaciones/2026-10-11-reintentos-emision.md).
Los controles ejercitados ya funcionaban: no se modifican sus transiciones.

18 casos nuevos de dominio: nueve combinaciones de respuestas duplicadas o
contradictorias fuera de EN_COLA; prohibición de reintento de ACEPTADO/ANULADO;
frontera un nanosegundo antes, exactamente y después de diez minutos; reinicio
de espera; reintento de RECHAZADO/ERROR_ENVIO con identidad, XML y resumen
conservados; duración configurada. Fixtures con UUID, datos y fechas fijos;
esperados proceden de las reglas, no de capturar resultados del agregado.

| Ejecución | Resultado |
|---|---|
| Código vigente con pruebas nuevas | 18 correctas, 0 fallos/errores/omitidas |
| Mutación: retirar bloqueo fuera de EN_COLA | 9 fallos de aserción entre 18 casos |
| Mutación: cambiar menor que por menor o igual en la espera | 2 casos fallan por ReglaDeNegocioViolada inesperada; Surefire registra 0 fallos y 2 errores |
| Mutación: permitir reintento de estados finales | 2 fallos de aserción entre 18 casos |
| Restauración exacta y dominio completo | 170 correctas, 0 fallos/errores/omitidas |

Mutaciones aisladas, restauración mediante copia/cmp entre cada una. Los dos
errores de la mutación de espera son excepciones funcionales provocadas en
caminos válidos, no problemas de compilación, entorno ni deuda sin corregir.
Los logs comprimidos son extractos de resultados/aserciones y el JSON guarda
sus hashes SHA-256. No contienen cuerpos HTTP ni credenciales.

Comando desde apps/backend con activador/settings del harness:

```sh
./mvnw -o -B -s /workspace/.ondexia-setup/maven.xml \
  -pl ondexia.domain -am -Ddependency-check.skip=true \
  -Dtest=ReintentosYDuplicadosEmisionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Para la regresión completa se retiraron los dos parámetros del selector.
SCA local omitido explícitamente; no se acredita por estas pruebas.

Se corrige una afirmación documental: ignorar resultados fuera de EN_COLA no
identifica el intento que originó una respuesta cuando se vuelve a encolar.
No se escribe una prueba que convierta aplicar ese resultado antiguo en política
aprobada. F17 sigue parcial por correlación, conciliación, recuperación automática
y concurrencia; no se ensayaron API/bus reales, AWS/SUNAT ni auditoría independiente.
CI del PR y de develop pendientes: se contrastarán por commit exacto en el PR.
