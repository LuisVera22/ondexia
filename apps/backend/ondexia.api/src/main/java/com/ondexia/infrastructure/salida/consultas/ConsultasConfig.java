package com.ondexia.infrastructure.salida.consultas;

import com.ondexia.domain.consultas.ConsultaDeRuc;
import com.ondexia.infrastructure.configuration.PropiedadesConsultas;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Monta la cascada con los proveedores que estén configurados.
 *
 * <h2>El orden es la política</h2>
 *
 * <p>Decolecta primero porque es el más completo; apiperu.dev después porque
 * tiene capa gratuita permanente y sabe decir si su fallo es pasajero. Ese orden
 * está aquí y en ningún otro sitio: cambiarlo es mover dos líneas, no tocar
 * lógica.
 *
 * <h2>Qué pasa si falta una clave</h2>
 *
 * <p>El proveedor no entra en la cascada. No es un fallo de arranque: en local
 * es normal tener una sola clave, y en la Lambda de la API no hay ninguna porque
 * quien consulta es {@code ondexia.consultas} (DT-19).
 *
 * <p>Y se registra en el log qué proveedores quedaron activos. Sin esa línea, un
 * entorno con la variable mal escrita se comporta como uno sin proveedor y no
 * hay forma de distinguirlo mirando.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesConsultas.class)
class ConsultasConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ConsultasConfig.class);

    @Bean
    ConsultaDeRuc consultaDeRuc(PropiedadesConsultas propiedades) {
        List<ConsultaDeRuc> cascada = new ArrayList<>();
        List<String> nombres = new ArrayList<>();

        if (propiedades.tieneDecolecta()) {
            cascada.add(new ConsultaDeRucDecolecta(cliente(
                    propiedades.decolectaUrl(), propiedades.decolectaToken(), propiedades)));
            nombres.add("decolecta");
        }
        if (propiedades.tieneApiPeru()) {
            cascada.add(new ConsultaDeRucApiPeru(cliente(
                    propiedades.apiperuUrl(), propiedades.apiperuToken(), propiedades)));
            nombres.add("apiperu");
        }

        if (cascada.isEmpty()) {
            LOG.info("Sin proveedores de consulta de RUC configurados: "
                    + "la verificación contra SUNAT no está disponible en este entorno.");
            return new ConsultaDeRucSinConfigurar();
        }

        LOG.info("Consulta de RUC por cascada: {}", String.join(" → ", nombres));
        return new ConsultaDeRucEnCascada(cascada);
    }

    /**
     * Un cliente por proveedor, con su token ya puesto.
     *
     * <p>El token va en la cabecera por omisión y no en cada llamada: así ningún
     * adaptador puede olvidarlo, y —lo que importa más— el valor no aparece en
     * el código de las llamadas, donde acabaría en un mensaje de registro el día
     * que alguien depure a base de imprimir.
     */
    private static RestClient cliente(String url, String token,
            PropiedadesConsultas propiedades) {

        // Se construye la fabrica a mano en vez de dejar que RestClient detecte
        // una: la detectada no lleva tiempos de espera, y un cliente HTTP sin
        // ellos espera indefinidamente. Con API Gateway cortando a los 29 s, eso
        // convierte un proveedor lento en un 504 opaco de la pasarela — el error
        // mas dificil de diagnosticar de los dos.
        //
        // Y es la fabrica simple, no la de java.net.http, aunque esa sea la
        // moderna: su cliente abre un socket de loopback al construirse, y este
        // bean se crea al arrancar. En un entorno que no lo permita, la
        // aplicacion entera no levanta por una funcion que quiza nadie use en esa
        // ejecucion. Para dos peticiones por registro, HTTP/2 y el pool de
        // conexiones no compran nada que compense ese riesgo.
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(propiedades.tiempoDeEspera());
        fabrica.setReadTimeout(propiedades.tiempoDeEspera());

        return RestClient.builder()
                .baseUrl(url)
                .requestFactory(fabrica)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }
}
