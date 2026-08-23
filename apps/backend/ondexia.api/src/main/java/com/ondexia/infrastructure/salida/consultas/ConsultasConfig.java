package com.ondexia.infrastructure.salida.consultas;

import com.ondexia.domain.consultas.VerificacionDeRuc;
import com.ondexia.infrastructure.configuration.PropiedadesConsultas;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

/**
 * Resuelve el secreto de firma y monta la verificación.
 *
 * <h2>Por qué SSM y no una variable de entorno de Lambda</h2>
 *
 * <p>Aunque DT-19 dijera «variable cifrada»: ese cifrado es en reposo y Lambda
 * la descifra sola, así que cualquiera con
 * {@code lambda:GetFunctionConfiguration} ve el valor en texto plano en la
 * consola. Un parámetro {@code SecureString} tiene IAM delante y es gratis,
 * frente a los 0,40 USD al mes de un secreto de Secrets Manager.
 *
 * <p>Se lee una vez al arrancar, no por petición: es el mismo valor durante toda
 * la vida del contenedor, y leerlo en cada alta sería latencia y consumo del
 * límite de peticiones de SSM a cambio de nada.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesConsultas.class)
class ConsultasConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ConsultasConfig.class);

    @Bean
    VerificacionDeRuc verificacionDeRuc(PropiedadesConsultas propiedades) {
        String secreto = resolver(propiedades);

        if (secreto == null) {
            // Se registra, y no se falla el arranque: hay entornos legitimos sin
            // secreto —las pruebas, un arranque local de quien trabaja en otra
            // cosa— y tumbar la aplicacion por eso convierte una funcion ausente
            // en un sistema caido. Lo que no se hace es aceptar cualquier
            // atestacion.
            LOG.info("Sin secreto de firma: el registro de empresas por RUC "
                    + "no está disponible en este entorno.");
            return new VerificacionPorAtestacion.SinSecreto();
        }

        LOG.info("Verificación de RUC activa (secreto desde {})",
                propiedades.firmaParametro() != null ? "SSM" : "variable de entorno");

        return new VerificacionPorAtestacion(
                secreto.getBytes(StandardCharsets.UTF_8), Clock.systemUTC());
    }

    /**
     * El parámetro manda sobre la variable directa.
     *
     * <p>En un entorno desplegado, una variable de entorno olvidada no debe poder
     * ganarle al parámetro: sería una forma silenciosa de firmar con un secreto
     * distinto del que usa {@code ondexia.consultas}, y el síntoma serían
     * atestaciones válidas rechazadas.
     */
    private static String resolver(PropiedadesConsultas propiedades) {
        if (propiedades.firmaParametro() == null) {
            return propiedades.firmaSecreto();
        }
        try (SsmClient ssm = SsmClient.create()) {
            return ssm.getParameter(GetParameterRequest.builder()
                            .name(propiedades.firmaParametro())
                            .withDecryption(true)
                            .build())
                    .parameter()
                    .value();
        } catch (RuntimeException noSePudo) {
            // Se nombra el parametro: el error de AWS por si solo no lo dice, y
            // buscarlo a ciegas entre los de la cuenta es tiempo perdido.
            throw new IllegalStateException(
                    "No se pudo leer el parámetro " + propiedades.firmaParametro()
                            + " de SSM. Revisa que exista y que el rol de la función "
                            + "tenga ssm:GetParameter y kms:Decrypt.", noSePudo);
        }
    }
}
