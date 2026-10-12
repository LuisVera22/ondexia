# Ejemplo completo: retirar efectivo en un cajero automático

Estado: **ejemplo didáctico, sin implementación ni aprobación bancaria**.
Su objetivo es mostrar la profundidad necesaria para especificar un proceso.
La estructura se apoya en [requisitos y pruebas](requisitos-y-pruebas.md).

## 1. Historia y alcance

**HU-CAJ-01:** Como titular de una cuenta habilitada, necesito retirar una cantidad
de efectivo autorizada, para disponer del dinero y conocer el resultado de la operación.

**CU-CAJ-01 · Retirar efectivo.** Nivel: objetivo de usuario. Alcance: terminal,
lector, autenticación con emisor, autorización de retiro, dispensador y registro
de operación. El banco y la red son actores secundarios; no se supone que el
terminal pueda determinar por sí mismo saldo, estado de tarjeta o PIN correcto.

Interesados: titular, banco, operador del cajero y soporte. Intereses: retirar lo
autorizado, evitar débito sin entrega, evitar duplicados y poder resolver incidentes.

No incluye depósitos, retiro sin tarjeta, tarjetas internacionales, sobregiro,
comisiones reales ni implementación criptográfica del PIN.

## 2. Políticas sintéticas del ejemplo

Estas reglas se fijan **antes** de los resultados esperados. Son decisiones de una
simulación pedagógica, no prácticas obligatorias de un banco ni de Ondexia.

| ID | Regla del ejemplo |
|---|---|
| RG-CAJ-01 | Solo se continúa cuando la tarjeta fue leída y el emisor confirma que está vigente, activa y habilitada para operar |
| RG-CAJ-02 | Solo se habilita retiro con autenticación positiva del emisor; hasta tres PIN incorrectos por sesión; al tercero se termina sin retiro y se intenta devolver la tarjeta |
| RG-CAJ-03 | El monto es positivo y múltiplo de S/ 20; el límite restante de retiro es S/ 500 en el escenario base |
| RG-CAJ-04 | El escenario base no cobra comisión; la cuenta debe permitir retirar y tener fondos disponibles suficientes |
| RG-CAJ-05 | El dispensador debe poder formar el monto exacto; saldo bancario suficiente no garantiza billetes disponibles |
| RG-CAJ-06 | Confirmar el usuario no significa autorizar el banco; una autorización no significa entrega de efectivo |
| RG-CAJ-07 | Una operación tiene un identificador estable; repetir la consulta o confirmación de ese identificador no genera otro retiro |
| RG-CAJ-08 | Solo se comunica éxito definitivo cuando entrega completa y contabilización final están confirmadas; resultado desconocido queda pendiente de conciliación |
| RG-CAJ-09 | Rechazo antes de autorización no descuenta ni reserva fondos; después de autorización, cancelación/fallo requiere liberación o conciliación verificable |
| RG-CAJ-10 | No se conservan ni muestran PIN o claves en registros, recibos o mensajes |

Valores a definir para un banco real: intentos acumulados entre sesiones, bloqueo
del instrumento, retención física, tarjeta no recogida, comisiones, tiempos de
espera, orden de devolución/entrega, débito/reserva/reversión y entrega parcial.
No sería correcto rellenarlos con «lo habitual».

## 3. Inicio, precondiciones y garantías

Disparador: el usuario presenta la tarjeta al terminal. Por tanto, «tarjeta válida»
no puede ser una precondición que elimine su validación del flujo.

Precondiciones: terminal disponible para iniciar una sesión, dispositivos con
diagnóstico de inicio válido y configuración del ejemplo cargada. La disponibilidad
puede cambiar durante el proceso y debe tratarse como extensión.

Garantía de éxito: entrega exacta del monto, una contabilización final por la
operación, resultado consultable y sesión terminada. El terminal ofrece devolución
de tarjeta; su recogida o retención posterior tiene política específica.

Garantías mínimas: no se dispensa sin autorización; no se reintenta una entrega de
resultado incierto como si nada hubiera ocurrido; se conserva identidad y evidencia
durable de la operación según contrato; ninguna salida revela secretos. Una caída
eléctrica puede impedir respuesta inmediata y devolución: el diseño necesita
recuperación posterior, no una garantía físicamente imposible.

## 4. Flujo principal

| Paso | Acción del actor | Respuesta y criterio del sistema |
|---|---|---|
| 1 | Presenta/inserta tarjeta | Lector obtiene datos completos o detecta error; inicia sesión identificada, sin asumir vigencia |
| 2 | Espera la comprobación | Comprueba formato/integridad de lectura y consulta al emisor; exige resultado positivo RG-CAJ-01 |
| 3 | Ingresa PIN por el canal protegido | Emisor confirma autenticación; no se compara con un PIN guardado en texto por el terminal |
| 4 | Selecciona retirar efectivo y cuenta | Sistema presenta solo cuentas/operaciones autorizadas; valida la cuenta elegida |
| 5 | Ingresa monto | Comprueba positivo, múltiplo y límite; contrasta capacidad del dispensador; comunica motivos corregibles |
| 6 | Revisa monto, cuenta y comisión del ejemplo | Sistema presenta información de confirmación; todavía no comunica retiro realizado |
| 7 | Confirma retiro | Crea identificador estable, solicita autorización/reserva según contrato y comprueba fondos/límite actuales |
| 8 | Espera entrega | Solo con autorización positiva ordena dispensar una vez; obtiene resultado del dispositivo, que puede ser completo, fallido o incierto |
| 9 | Recoge efectivo | Sistema registra evidencia de entrega y solicita contabilización final asociada al mismo identificador |
| 10 | Espera resultado | Con ambas confirmaciones informa monto y operación finalizada; una confirmación perdida sigue otra ruta |
| 11 | Solicita recibo si lo desea | Entrega comprobación sin secretos; no convierte fallo de impresión en un nuevo retiro |
| 12 | Recoge tarjeta y finaliza | Sistema termina sesión; tarjeta no recogida se maneja por política, sin repetir débito |

Los pasos son un contrato del ejemplo. Secuencias reales pueden devolver la
tarjeta antes de entregar dinero; ese cambio exige revisar alternativas y pruebas.

## 5. Qué significa que la tarjeta sea correcta

| Comprobación | Fuente de verdad | Si aprueba | Si rechaza | Si no se conoce |
|---|---|---|---|---|
| Lectura completa e íntegra | Lector y protocolo de tarjeta | Continuar al emisor | Pedir reinserción o terminar según política; sin PIN/retiro | Señalar fallo de lectura, no «tarjeta bloqueada» |
| Instrumento admitido y vigente | Datos del instrumento más política/emisor | Evaluar estado | No continuar; mensaje seguro y tarjeta según política | No autorizar por ausencia de respuesta |
| Estado activo, no bloqueado y habilitado | Emisor | Solicitar autenticación | Terminar o permitir corrección según código; sin retiro | Informar imposibilidad de validar; sin retiro |
| PIN correcto | Emisor/canal de autenticación | Habilitar operaciones permitidas | Contar solo intentos realmente rechazados; aplicar RG-CAJ-02 | Error técnico no equivale automáticamente a PIN incorrecto |
| Cuenta y retiro autorizables | Banco en la operación concreta | Solicitar/obtener autorización | Explicar rechazo sin revelar información ajena | No dispensar; si pudo reservarse, conciliar |

Un checksum correcto no prueba vigencia, identidad ni permiso. «Correcto» es una
conjunción de decisiones con autoridades distintas; un timeout es un tercer
resultado, no un booleano falso equivalente a rechazo confirmado.

## 6. Extensiones y excepciones

| ID / paso | Condición | Respuesta, efectos y destino |
|---|---|---|
| EX-01 · 1 | Tarjeta ilegible | No consulta PIN ni retiro; permite otra lectura o termina; vuelve a 1 según política |
| EX-02 · 2 | Emisor rechaza tarjeta vencida/bloqueada/no habilitada | No habilita operaciones; termina sesión; devolución/retención según política definida |
| EX-03 · 2–3 | Emisor no responde | Mensaje de servicio no disponible; no presenta rechazo de identidad como hecho; no dispone de autorización de retiro |
| EX-04 · 3 | PIN incorrecto, intento menor que tres | No habilita retiro; informa intento rechazado; vuelve a 3 |
| EX-05 · 3 | Tercer PIN incorrecto | RG-CAJ-02: termina y devuelve según la simulación; sin autorización ni débito |
| EX-06 · 4 | Cuenta no pertenece o no está habilitada | Rechaza selección; no cambia fondos; vuelve a 4 o termina |
| EX-07 · 5 | Monto cero/negativo/no múltiplo/supera límite | Explica restricción aplicable; no solicita autorización; vuelve a 5 |
| EX-08 · 5, 8 | No puede formar el monto | Antes de autorizar: permite otro monto; después: libera autorización o concilia; no afirma débito cero hasta confirmarlo |
| EX-09 · 7 | Fondos/límite cambiaron desde la revisión | Banco rechaza; no dispensa; permite nuevo monto o termina |
| EX-10 · 7 | Se pierde respuesta de autorización | Consulta mismo identificador; no crea otro retiro ni dispensa por suposición; estado pendiente hasta resolver |
| EX-11 · 8 | Dispensador confirma cero entregado | Solicita liberación/reversión según contrato; éxito de esa compensación debe confirmarse |
| EX-12 · 8–9 | Entrega parcial o resultado incierto | Marca incidente y conciliación; conserva monto autorizado, evidencia de entrega y referencia; no aplica automáticamente «todo debitado» ni «todo devuelto» |
| EX-13 · 9–10 | Efectivo entregado y se pierde confirmación bancaria | Informa operación pendiente; recupera contabilización del mismo ID; no vuelve a dispensar |
| EX-14 · 11 | Impresora sin papel | Ofrece referencia/consulta alternativa; mantiene retiro finalizado; ningún nuevo débito |
| EX-15 · 12 | Tarjeta no recogida | Aplica política física del banco; termina sesión sensible; no cambia el retiro |
| EX-16 · 1–6 | Usuario cancela o sesión expira | Sin autorización aún: termina sin cambio de fondos; devolución según política |
| EX-17 · 7–10 | Usuario cancela o sesión expira tras solicitar autorización | No promete cancelación inmediata; determina fase y resultado; recupera o compensa según contrato |
| EX-18 · cualquiera | Caída de energía/red o reinicio | Recupera por ID y diario durable; los efectos anteriores no desaparecen por cerrar la pantalla |
| EX-19 · 7–10 | Solicitud/resultados duplicados | Responde estado de la misma operación; como máximo una orden de entrega efectiva según contrato del dispositivo |

EX-12 requiere una política bancaria completa antes de implementar liquidación
automática. En este ejemplo el comportamiento definido es detectar, conservar y
conciliar; no se inventa el saldo final de una entrega parcial.

## 7. Decisiones y estados

Tabla simplificada de decisión **antes de solicitar autorización**. «—» significa
irrelevante en esa fila por precedencia, no dato desconocido.

| Lectura/estado aceptados | PIN aceptado | Cuenta permitida | Monto admisible | Dispensador capaz | Acción |
|---|---|---|---|---|---|
| No | — | — | — | — | Rechazar acceso al retiro |
| Sí | No | — | — | — | Reintento/fin según contador |
| Sí | Sí | No | — | — | Rechazar cuenta |
| Sí | Sí | Sí | No | — | Corregir monto |
| Sí | Sí | Sí | Sí | No | Corregir monto o terminar |
| Sí | Sí | Sí | Sí | Sí | Solicitar autorización actual al banco |

Cada comprobación indeterminada tiene extensión propia. Que la última fila se
cumpla no implica fondos suficientes ni éxito: la decisión bancaria ocurre después.

| Estado | Evento y condición | Estado siguiente / efecto |
|---|---|---|
| ESPERANDO_TARJETA | Lectura aceptada | VALIDANDO_TARJETA |
| VALIDANDO_TARJETA | Emisor acepta | AUTENTICANDO |
| AUTENTICANDO | PIN aceptado | PREPARANDO_RETIRO |
| PREPARANDO_RETIRO | Usuario confirma datos admisibles | SOLICITANDO_AUTORIZACION |
| SOLICITANDO_AUTORIZACION | Autorización positiva | AUTORIZADO |
| AUTORIZADO | Orden identificada de entrega | ENTREGANDO |
| ENTREGANDO | Entrega completa confirmada | CONFIRMANDO_CONTABILIZACION |
| CONFIRMANDO_CONTABILIZACION | Banco confirma | FINALIZADO |
| Antes de autorización | Cancelación/rechazo conocido | TERMINADO_SIN_RETIRO |
| Después de solicitar autorización | Resultado incierto/fallo con efectos posibles | PENDIENTE_CONCILIACION |

Completar el modelo con las extensiones al diseñar un sistema real. Nunca son
válidas AUTENTICANDO→ENTREGANDO ni PENDIENTE_CONCILIACION→otro retiro con el mismo
ID. Estado de sesión terminada y estado financiero pendiente pueden coexistir.

## 8. Caso de prueba detallado e independiente

**CP-CAJ-01 · Retiro de S/ 200 autorizado y entregado.**
Referencias: CU-CAJ-01 principal; RG-CAJ-01 a 10. Nivel: integración simulada.
No prueba hardware ni banco reales.

Preparación: reloj 2026-10-10; tarjeta sintética legible con vigencia 2027-12,
emisor configurado con estado activo/habilitado y autenticación positiva; cuenta
permitida con saldo disponible S/ 1.000, límite restante S/ 500, comisión S/ 0;
dispensador capaz de entregar diez billetes de S/ 20 y confirmar entrega completa.
El ID de operación se captura como referencia, no se deduce para fijar importes.

| Paso de prueba | Acción | Resultado esperado |
|---|---|---|
| 1 | Insertar tarjeta sintética | Solicita autenticación; no orden de dispensación ni retiro bancario |
| 2 | Autenticación positiva del emisor | Ofrece cuenta/operación autorizadas |
| 3 | Elegir retiro de S/ 200 | Resumen de S/ 200 y comisión S/ 0; todavía no éxito |
| 4 | Confirmar | Una autorización vinculada al ID; ninguna orden previa a aceptación |
| 5 | Banco autoriza y dispositivo confirma entrega | Una entrega de S/ 200; diez billetes del ejemplo; registra referencia |
| 6 | Banco confirma contabilización | Estado FINALIZADO; saldo bancario final S/ 800; débito final S/ 200; no reserva pendiente |
| 7 | Pedir recibo y terminar | Referencia y monto visibles; sin PIN ni claves; termina sesión |
| 8 | Repetir resultado/consulta del mismo ID | Mantiene una entrega y un débito; no S/ 600 de saldo |

Oráculo: reglas sintéticas y aritmética decimal independiente
`1000.000000 − 200.000000 = 800.000000`, fijadas antes de ejecutar. No se obtiene
el resultado esperado llamando al mismo cálculo de la implementación.
Estado de ejecución: **no ejecutado**. Limpieza: reset de simuladores, cuentas y
diarios sintéticos; ningún dato bancario real ni secreto en evidencia.

## 9. Catálogo mínimo de pruebas del ejemplo

| Casos | Entrada/condición | Resultado que debe distinguirse |
|---|---|---|
| CP-02/03/04 | Ilegible, vencida, bloqueada | Sin habilitación de retiro; motivos y salida definidos |
| CP-05 | Lectura/formato correctos y emisor rechaza | No confundir formato con aprobación |
| CP-06 | PIN incorrecto primero y luego correcto | Solo segundo habilita; contador conforme a regla |
| CP-07/08 | Segundo/tercer PIN incorrecto | Reintento frente a fin; sin débito |
| CP-09 | Timeout de autenticación | No cuenta como rechazo confirmado por omisión |
| CP-10 | Cuenta no permitida | No autorización ni entrega |
| CP-11 | Montos -20, 0, 19, 20, 500, 520 | Rechazo de negativos/cero/no múltiplo; límites 20/500; 520 rechazado por límite del ejemplo |
| CP-12 | Monto admisible, saldo insuficiente | Rechazo bancario; cero entrega |
| CP-13 | Saldo suficiente, billetes insuficientes | No prometer entrega por saldo |
| CP-14 | Saldo disponible cambia al confirmar | Revalidación bancaria, no aprobación vieja |
| CP-15/16 | Cancelar antes/después de autorización | Sin fondos afectados frente a recuperación/compensación |
| CP-17 | Autorización aprobada, respuesta perdida | Recuperar mismo ID; no retiro duplicado |
| CP-18 | Entrega cero confirmada | Liberación/reversión comprobada o pendiente visible |
| CP-19 | Entrega parcial de S/ 100 sobre S/ 200 | Incidente, monto parcial conservado; saldo final pendiente de política |
| CP-20 | Entrega completa, confirmación perdida | Pendiente sin segunda dispensación; recuperación del mismo ID |
| CP-21/22 | Reinicio antes/después de entrega | Recuperación coherente con efectos conocidos |
| CP-23/24 | Doble confirmación/resultados duplicados | Una operación y efectos sin duplicación |
| CP-25/26 | Impresora fallida/tarjeta no recogida | No repetir ni revertir retiro finalizado por defecto |
| CP-27 | Transición sin autenticación/autorización | Denegada; cero entrega |
| CP-28 | Inspeccionar mensajes, recibos y diarios | Sin PIN, claves ni datos innecesarios |

No basta contar 28 IDs: hay casos parametrizados, secuencias y políticas pendientes.
El cierre exige matriz de extensiones/reglas y resultados por cada ejecución.
