package com.ondexia.infrastructure.salida.consultas;

import com.ondexia.domain.consultas.Atestacion;
import com.ondexia.domain.consultas.VerificacionDeRuc;
import com.ondexia.infrastructure.configuration.PropiedadesConsultas;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Monta la verificación de atestaciones.
 *
 * <p>No lee ningún secreto y no habla con AWS: solo necesita una clave pública,
 * que llega por configuración. Por eso funciona igual en la Lambda —que no tiene
 * salida a internet ni puede alcanzar SSM— que en un portátil.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesConsultas.class)
class ConsultasConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ConsultasConfig.class);

    @Bean
    VerificacionDeRuc verificacionDeRuc(PropiedadesConsultas propiedades) {
        if (propiedades.firmaPublica() == null) {
            // Se registra y no se falla el arranque: hay entornos legitimos sin
            // clave —las pruebas de integracion, un arranque local de quien
            // trabaja en otra cosa— y tumbar la aplicacion por eso convierte una
            // funcion ausente en un sistema caido. Lo que NO se hace es aceptar
            // cualquier atestacion.
            LOG.info("Sin clave pública de firma: el registro de empresas por RUC "
                    + "no está disponible en este entorno.");
            return new VerificacionPorAtestacion.SinClave();
        }

        // Se analiza al arrancar y no en la primera peticion. Una clave mal
        // pegada es un error de configuracion, y conviene que aparezca al
        // desplegar en vez de en el primer alta que intente alguien.
        LOG.info("Verificación de RUC activa.");
        return new VerificacionPorAtestacion(
                Atestacion.clavePublica(propiedades.firmaPublica()), Clock.systemUTC());
    }
}
