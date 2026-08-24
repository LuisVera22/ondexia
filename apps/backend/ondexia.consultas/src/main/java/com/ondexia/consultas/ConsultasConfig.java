package com.ondexia.consultas;

import com.ondexia.domain.consultas.Atestacion;
import com.ondexia.domain.consultas.ConsultaDeRuc;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.ssm.SsmClient;

/**
 * Monta la cascada de proveedores y la clave de firma.
 *
 * <h2>El orden es la política</h2>
 *
 * <p>Decolecta primero porque es el más completo —ubigeo, distrito, anexos y su
 * endpoint {@code /full} con la forma societaria—; apiperu.dev después porque
 * tiene capa gratuita permanente y sabe decir si su fallo es pasajero. Ese orden
 * está aquí y en ningún otro sitio: cambiarlo es mover dos líneas.
 *
 * <h2>Se resuelve todo al arrancar</h2>
 *
 * <p>Leer SSM en cada invocación es latencia que paga quien está esperando en el
 * formulario, y cuenta contra el límite de 40 peticiones por segundo del nivel
 * estándar. Resuelto en el arranque, un contenedor tibio no vuelve a preguntar —
 * y con SnapStart, ni siquiera el frío.
 *
 * <p>La contrapartida es que la clave privada queda en la instantánea de
 * SnapStart. Es la fila del certificado de DTE §4.2, con la diferencia de que
 * esta es nuestra y no de un cliente. Se acepta a cambio de que una clave mal
 * puesta falle al desplegar en vez de en el primer alta que intente alguien.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesConsultas.class)
public class ConsultasConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ConsultasConfig.class);

    /**
     * El cliente de SSM solo se crea si algún parámetro lo necesita.
     *
     * <p>Crearlo siempre buscaría credenciales de AWS al arrancar, y en un
     * portátil sin ellas la aplicación no levantaría por una dependencia que en
     * ese entorno no se usa.
     */
    @Bean
    Claves claves(PropiedadesConsultas propiedades) {
        return new Claves(propiedades.necesitaSsm() ? SsmClient.create() : null);
    }

    /**
     * La clave con la que se firman las atestaciones.
     *
     * <p>Sin ella la aplicación no arranca, y es deliberado: arrancando, cada
     * consulta responderia 500 y el motivo estaría en un rastro de pila en vez de
     * en un mensaje que diga qué falta.
     */
    @Bean
    PrivateKey clavePrivadaDeFirma(PropiedadesConsultas propiedades, Claves claves) {
        String pem = claves.resolver(propiedades.firmaParametro(), propiedades.firmaPrivada());
        if (pem == null) {
            throw new IllegalStateException(
                    "Falta la clave de firma. Define ondexia.consultas.firma-parametro "
                            + "(CONSULTAS_FIRMA_PARAMETRO, el nombre del parámetro en SSM) "
                            + "o ondexia.consultas.firma-privada (CONSULTAS_FIRMA_PRIVADA).");
        }
        return Atestacion.clavePrivada(pem);
    }

    @Bean
    ConsultaDeRuc consultaDeRuc(PropiedadesConsultas propiedades, Claves claves) {
        List<ConsultaDeRuc> cascada = new ArrayList<>();
        List<String> nombres = new ArrayList<>();

        String decolecta =
                claves.resolver(propiedades.decolectaParametro(), propiedades.decolectaToken());
        if (decolecta != null) {
            cascada.add(new ProveedorDecolecta(
                    new ClienteDelPadron("decolecta", decolecta, propiedades.tiempoDeEspera()),
                    propiedades.decolectaUrl()));
            nombres.add("decolecta");
        }

        String apiperu =
                claves.resolver(propiedades.apiperuParametro(), propiedades.apiperuToken());
        if (apiperu != null) {
            cascada.add(new ProveedorApiPeru(
                    new ClienteDelPadron("apiperu", apiperu, propiedades.tiempoDeEspera()),
                    propiedades.apiperuUrl()));
            nombres.add("apiperu");
        }

        if (cascada.isEmpty()) {
            throw new IllegalStateException(
                    "Ningún proveedor de consulta configurado. Define al menos "
                            + "ondexia.consultas.decolecta-parametro (CONSULTAS_DECOLECTA_PARAMETRO) "
                            + "o ondexia.consultas.decolecta-token (CONSULTAS_DECOLECTA_TOKEN).");
        }

        /*
         * Se deja constancia de quien quedo activo. Sin esta linea, un entorno
         * con el nombre de un parametro mal escrito se comporta igual que uno con
         * un solo proveedor y no hay forma de distinguirlo mirando.
         */
        LOG.info("Consulta de RUC por cascada: {}", String.join(" → ", nombres));

        return new CascadaDeProveedores(cascada);
    }
}
