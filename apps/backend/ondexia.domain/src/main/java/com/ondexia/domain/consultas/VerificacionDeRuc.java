package com.ondexia.domain.consultas;

import java.util.Optional;

/**
 * Puerto de salida: dar por buena —o no— una verificación de RUC que llega del
 * cliente.
 *
 * <h2>Por qué la API tiene este puerto y no {@link ConsultaDeRuc}</h2>
 *
 * <p>Porque la API no puede preguntar. Su Lambda está en subred privada sin NAT
 * (DTE §4.8), y darle salida cuesta entre 7 y 32 USD al mes. Quien pregunta es
 * {@code ondexia.consultas}, fuera de la VPC, y su respuesta llega firmada a
 * través del navegador.
 *
 * <p>Así que aquí la operación no es «consultar» sino «creer»: se recibe una
 * {@link Atestacion} y se comprueba que la firmamos nosotros y que no ha
 * caducado. Sigue siendo una decisión del servidor —el cliente puede reenviar
 * una atestación, no fabricarla—, pero sin red.
 *
 * <p>Los dos puertos conviven a propósito. Cada desplegable implementa el que le
 * corresponde, y ninguno de los dos sabe del otro.
 */
public interface VerificacionDeRuc {

    /**
     * @param atestacion lo que devolvió {@code ondexia.consultas}
     * @return lo que SUNAT dijo, ya comprobado
     * @throws AtestacionInvalida si la firma no cuadra, caducó o no se entiende
     */
    DatosDeRuc comprobar(String atestacion);

    /**
     * Si este entorno puede comprobar atestaciones.
     *
     * <p>Existe para que una vista pueda explicar por qué no hay registro de
     * empresas en lugar de ofrecer un formulario que va a fallar al enviarse. Un
     * entorno sin secreto de firma es un entorno donde el alta no funciona, y es
     * mejor decirlo antes que después de rellenar diez campos.
     */
    default Optional<String> motivoDeNoPoder() {
        return Optional.empty();
    }
}
