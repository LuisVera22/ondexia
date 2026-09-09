package com.ondexia.domain.identidad;

import java.util.UUID;

/**
 * Los topes de una cuenta, ya resueltos.
 *
 * <h2>Por qué el puerto devuelve el límite resuelto y no las dos piezas</h2>
 *
 * <p>Porque el techo real es «lo pactado con la cuenta, y si no hay nada
 * pactado, lo del plan» (V9). Devolviendo los dos valores por separado, cada
 * quien que los consuma tiene que recordar el {@code coalesce} — y basta con que
 * uno lo olvide para que el cliente que pagó una empresa extra quede bloqueado
 * por el límite de su plan.
 *
 * <p>La cuenta del uso va en el mismo viaje por el mismo motivo que en
 * {@link LimitesDeCuenta}: el límite sin el uso no permite decidir nada, así que
 * pedirlos aparte solo abre la puerta a compararlos con datos de dos momentos
 * distintos.
 */
public interface LimitesDeCuentaRepositorio {

    LimitesDeCuenta de(UUID cuentaId);
}
