package com.ondexia.admin.cuentas;

import java.util.UUID;

/**
 * Un módulo o submódulo, visto para una cuenta concreta.
 *
 * @param decision   lo que se decidió <em>para esta cuenta</em>. {@code null}
 *                   significa que no hay decisión y manda el plan — y eso no es
 *                   lo mismo que un {@code false}: sin fila, la cuenta hereda los
 *                   módulos que se añadan al plan más adelante
 * @param contratado el resultado efectivo, que es lo que la API de clientes va a
 *                   aplicar. La pantalla debe mostrar los dos: uno es la causa y
 *                   el otro el efecto
 */
public record ModuloContratado(
        UUID id,
        String codigo,
        String modulo,
        String nivel,
        String nombre,
        Boolean decision,
        boolean contratado) {
}
