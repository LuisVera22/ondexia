package com.ondexia.admin.seguridad;

import java.util.List;
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

    @Bean
    SecurityFilterChain cadena(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> {
                })
                // Sin estado: cada petición trae su token. No hay sesión que
                // fijar ni que robar, así que CSRF no aplica.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(peticiones -> peticiones
                        .requestMatchers(HttpMethod.GET, "/salud").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {
                }))
                .build();
    }
}
