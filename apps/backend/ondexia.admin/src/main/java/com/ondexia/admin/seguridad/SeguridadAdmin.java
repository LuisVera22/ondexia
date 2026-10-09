package com.ondexia.admin.seguridad;

import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Quién entra al panel.
 *
 * <h2>El grupo de personal, y solo el grupo de personal</h2>
 *
 * <p>Los tokens se validan contra el pool de <strong>personal</strong> de
 * Cognito, que es un emisor distinto del de inquilinos. Un token de cliente está
 * firmado con la clave de otro pool, así que la pasarela lo rechaza antes de
 * invocar esta función aunque alguien se equivoque configurando permisos: la
 * separación es criptográfica, no de reglas.
 *
 * <p>Quien comprueba esa firma es el autorizador de la pasarela, no esta
 * aplicación — {@link TokenDeLaPasarela} explica por qué y qué se pierde con
 * ello. Aquí se vuelve a mirar el emisor de todos modos, que es gratis y no
 * depende de la red.
 *
 * <p>La otra mitad de la separación está en la base: este servicio se conecta
 * como {@code ondexia_panel}, que no tiene concedidas las tablas de documentos
 * tributarios (doc 09 §6.2).
 *
 * <h2>Todo cerrado salvo la señal de vida</h2>
 *
 * <p>{@code /salud} queda abierto porque lo consulta el despliegue para saber si
 * la función arrancó, y no dice nada que un desconocido no pueda deducir. Lo
 * demás exige token; no hay endpoints de solo lectura «poco sensibles» en una
 * consola cuyo objeto son las cuentas de todos los clientes.
 */
@Configuration
public class SeguridadAdmin {

    /**
     * El origen permitido, que es uno solo.
     *
     * <p>Nada de comodines: esta API responde con datos de todas las cuentas
     * cliente. Lo pone Terraform como {@code CORS_ORIGENES} y apunta a la
     * distribución del panel.
     *
     * <p>Quien contesta el {@code OPTIONS} es esta aplicación y no la pasarela,
     * porque la ruta del preflight apunta a la función — ver panel.tf.
     */
    @Bean
    CorsConfigurationSource origenesPermitidos(
            @Value("${CORS_ORIGENES:http://localhost:4200}") String origenes) {

        var configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(List.of(origenes.split(",")));
        configuracion.setAllowedMethods(List.of("GET", "PUT", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("authorization", "content-type"));
        configuracion.setMaxAge(3600L);

        var fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", configuracion);
        return fuente;
    }

    /**
     * Los grupos de Cognito, convertidos en autoridades (hallazgo A5).
     *
     * <p>Cognito publica la pertenencia en {@code cognito:groups}, un array de
     * cadenas. Spring no lo sabe: por omisión busca {@code scope} o {@code scp},
     * que un token de Cognito no trae, así que TODA cuenta del grupo de personal
     * llegaba sin ninguna autoridad — y con {@code anyRequest().authenticated()}
     * eso significaba que estar dentro del pool bastaba para cambiar el plan de
     * un cliente o suspenderle el servicio.
     *
     * <p>El prefijo {@code ROLE_} es lo que permite escribir {@code hasRole}. Sin
     * él haría falta {@code hasAuthority("operaciones")} en cada endpoint, que es
     * la clase de detalle que se olvida en el siguiente que se añada.
     */
    @Bean
    JwtAuthenticationConverter conversorDeGrupos() {
        var deGrupos = new JwtGrantedAuthoritiesConverter();
        deGrupos.setAuthoritiesClaimName("cognito:groups");
        deGrupos.setAuthorityPrefix("ROLE_");

        var conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(deGrupos);
        return conversor;
    }

    /**
     * Quién puede qué.
     *
     * <h2>La autorización va aquí y no en anotaciones</h2>
     *
     * <p>Con {@code @PreAuthorize} en cada método, el endpoint que alguien añada
     * mañana sin la anotación queda abierto a cualquiera del pool: el descuido
     * falla hacia el lado inseguro. Declarándolo por patrón y método, lo que
     * queda sin cubrir cae en {@code anyRequest()}, y esa línea exige el grupo
     * más restrictivo.
     *
     * <p>Es la misma razón por la que la API de clientes resuelve permisos en la
     * base y no confía en anotaciones sueltas.
     */
    @Bean
    SecurityFilterChain cadena(HttpSecurity http, JwtAuthenticationConverter conversor)
            throws Exception {
        return http
                .cors(cors -> {
                })
                // Sin estado: cada petición trae su token. No hay sesión que
                // fijar ni que robar, así que CSRF no aplica.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(peticiones -> peticiones
                        .requestMatchers(HttpMethod.GET, "/salud").permitAll()

                        // Leer: soporte u operaciones. Es lo que necesita quien
                        // atiende una consulta de un cliente.
                        .requestMatchers(HttpMethod.GET, "/**")
                        .hasAnyRole("soporte", "operaciones")

                        // Escribir: solo operaciones. Cambiar el plan de un
                        // cliente o suspenderle el servicio tiene consecuencias
                        // de facturación para él.
                        .anyRequest().hasRole("operaciones"))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(conversor)))
                .build();
    }
}
