package com.ondexia.infrastructure.salida.consultas;

import com.ondexia.domain.consultas.Atestacion;
import com.ondexia.domain.consultas.AtestacionInvalida;
import com.ondexia.domain.consultas.DatosDeRuc;
import com.ondexia.domain.consultas.VerificacionDeRuc;
import java.security.PublicKey;
import java.time.Clock;
import java.util.Optional;

/**
 * Comprueba la firma de una atestación. Sin red.
 *
 * <h2>Por qué esto es suficiente</h2>
 *
 * <p>Porque la firma es lo que traslada la autoridad. El navegador transporta la
 * atestación pero no puede fabricarla: sin la clave privada —que solo tiene
 * {@code ondexia.consultas}— cambiar {@code NO_HABIDO} por {@code HABIDO}
 * invalida la firma.
 *
 * <p>Aquí solo está la clave <strong>pública</strong>, que no es un secreto. Es
 * lo que permite pasarla en una variable de entorno sin exponer nada, y la razón
 * de que la firma sea Ed25519 y no un HMAC: la API no puede leer SSM desde su
 * subred privada sin pagar un endpoint de interfaz.
 *
 * <p>De modo que la API sigue siendo quien decide si una empresa puede
 * registrarse, aunque no haya podido preguntárselo a SUNAT.
 */
class VerificacionPorAtestacion implements VerificacionDeRuc {

    private final PublicKey publica;
    private final Clock reloj;

    VerificacionPorAtestacion(PublicKey publica, Clock reloj) {
        this.publica = publica;
        this.reloj = reloj;
    }

    @Override
    public DatosDeRuc comprobar(String atestacion) {
        return Atestacion.verificar(atestacion, publica, reloj.instant()).datos();
    }

    /**
     * Lo que se usa cuando no hay clave configurada.
     *
     * <h2>Por qué existe en vez de no registrar el bean</h2>
     *
     * <p>Sin bean, la aplicación no arranca. Y hay entornos legítimos sin
     * secreto: las pruebas de integración, y un arranque local de alguien que
     * está trabajando en otra cosa. Dejar la aplicación sin levantar por eso
     * convierte una función ausente en un sistema caído.
     *
     * <h2>Por qué rechaza todo en vez de aceptar todo</h2>
     *
     * <p>Porque lo segundo es un agujero silencioso: los registros pasarían,
     * nadie vería un error, y cualquiera podría darse de alta con un RUC
     * inventado. Un doble que acepta todo es peor que no tener verificación,
     * porque parece que la hay.
     */
    static final class SinClave implements VerificacionDeRuc {

        @Override
        public DatosDeRuc comprobar(String atestacion) {
            throw new AtestacionInvalida(
                    "La verificación de RUC no está configurada en este entorno.");
        }

        @Override
        public Optional<String> motivoDeNoPoder() {
            return Optional.of("Falta la clave pública de firma de las consultas de RUC.");
        }
    }
}
