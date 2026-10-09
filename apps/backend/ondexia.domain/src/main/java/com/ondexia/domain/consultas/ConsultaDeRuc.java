package com.ondexia.domain.consultas;

import com.ondexia.domain.comun.Ruc;
import java.util.Optional;

/**
 * Puerto de salida: preguntar a SUNAT qué sabe de un RUC.
 *
 * <h2>Qué expresa la firma</h2>
 *
 * <p>Recibe un {@link Ruc} y no una cadena: quien pregunta ya superó el dígito
 * verificador, así que ninguna implementación gasta una llamada de red en un
 * número que era una errata de tecleo.
 *
 * <p>Devuelve {@link Optional} para «el padrón no lo conoce» y lanza
 * {@link ConsultaNoDisponible} para «no se pudo preguntar». Son dos cosas
 * distintas y la distinción es la mitad del valor de este puerto: confundirlas
 * hace que la aplicación diga «ese RUC no existe» a quien lo tiene bien.
 *
 * <h2>Lo que el puerto no dice</h2>
 *
 * <p>No dice quién responde. Detrás hay una cascada —Decolecta, apiperu.dev, el
 * padrón en S3— y eso es asunto del adaptador: al dominio no le cambia nada
 * quién contestó. Lo único que se filtra hacia arriba es que la respuesta puede
 * venir sin ubigeo, y eso ya está en {@link DatosDeRuc#faltaUbigeo()}.
 *
 * <p>Tampoco decide si la empresa puede registrarse. Eso lo responde
 * {@link DatosDeRuc#aptaParaRegistro()}, porque es una regla de negocio y no una
 * propiedad de la consulta.
 *
 * <h2>Dónde vive la implementación</h2>
 *
 * <p><strong>Hoy, dentro de {@code ondexia.api}</strong>, hablando con los
 * proveedores por HTTP. Eso funciona ejecutando la API en una máquina de
 * desarrollo y <strong>no funciona desplegado</strong>: la Lambda está en subred
 * privada sin NAT y no tiene salida a internet (DTE §4.8).
 *
 * <p>Falla limpio, al menos: sin claves configuradas se monta
 * {@code ConsultaDeRucSinConfigurar}, que dice que no está disponible en vez de
 * esperar una conexión que nunca va a llegar.
 *
 * <p>Lo que DT-19 decide, y falta: el adaptador de proveedores se mueve a
 * {@code ondexia.consultas} —desplegable propio, fuera de la VPC— y la
 * implementación de este puerto en la API pasa a ser un cliente que valida una
 * atestación firmada, sin red. Cuando eso exista, esta clase no cambia; es el
 * sentido de que el puerto no diga quién contesta.
 */
public interface ConsultaDeRuc {

    /**
     * @param ruc el número a consultar, ya válido en su forma
     * @return lo que SUNAT dice, o vacío si el padrón no conoce ese RUC
     * @throws ConsultaNoDisponible si no se pudo preguntar a nadie
     */
    Optional<DatosDeRuc> consultar(Ruc ruc);
}
