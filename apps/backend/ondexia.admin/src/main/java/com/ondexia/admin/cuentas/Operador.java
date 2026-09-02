package com.ondexia.admin.cuentas;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Quién está operando el panel: su identidad y su correo, por separado.
 *
 * <h2>Por qué dos campos y no uno</h2>
 *
 * <p>Hallazgo A6 de la auditoría 2026-09-01. La bitácora guardaba solo el correo,
 * con este argumento de la V9: «se guarda el correo y no solo el sub porque esta
 * bitácora la lee una persona». La lectura es un buen motivo; el problema estaba
 * en el «y no solo».
 *
 * <p>El correo de una cuenta de Cognito <strong>lo cambia su dueño</strong>. Quien
 * haga algo que no deba, cambie después su correo y deje libre el anterior,
 * consigue que la bitácora señale a una dirección que ya no es suya — o a nadie.
 * El {@code sub} es inmutable y Cognito no lo reasigna.
 *
 * <p>Así que van los dos, y cada uno con su papel: {@link #sub()} identifica,
 * {@link #correo()} se lee. Confundirlos es lo que produjo el hallazgo.
 *
 * @param sub    identidad inmutable en el grupo de personal
 * @param correo tal como estaba en el momento del hecho
 */
public record Operador(String sub, String correo) {

    public Operador {
        if (sub == null || sub.isBlank()) {
            /*
             * No deberia ocurrir: estos endpoints exigen autenticacion y todo JWT
             * lleva `sub`. Si ocurre, es preferible un 500 a una fila de bitacora
             * que no señala a nadie — que es justo lo que este hallazgo corrige.
             */
            throw new IllegalArgumentException(
                    "Un operador sin sub no puede quedar en la bitacora.");
        }
    }

    /**
     * Lo saca del token de la pasarela.
     *
     * <p>Si el token no trae {@code email} se usa el {@code sub} también como
     * texto legible: peor de leer, pero perder el rastro por un reclamo ausente
     * sería mucho peor.
     */
    public static Operador de(Jwt token) {
        if (token == null) {
            throw new IllegalStateException(
                    "Sin token no hay operador. Este endpoint exige autenticacion.");
        }
        String correo = token.getClaimAsString("email");
        return new Operador(token.getSubject(), correo != null ? correo : token.getSubject());
    }
}
