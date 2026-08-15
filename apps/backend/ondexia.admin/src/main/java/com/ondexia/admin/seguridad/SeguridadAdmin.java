package com.ondexia.admin.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Quién entra al panel.
 *
 * <h2>El grupo de personal, y solo el grupo de personal</h2>
 *
 * <p>Los tokens se validan contra el pool de <strong>personal</strong> de
 * Cognito, que es un emisor distinto del de inquilinos. Eso no es una
 * comprobación más que se pueda olvidar: un token de cliente está firmado con la
 * clave de otro pool, así que aquí <strong>no valida</strong> aunque alguien se
 * equivoque configurando permisos. La separación es criptográfica, no de reglas.
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

    @Bean
    SecurityFilterChain cadena(HttpSecurity http) throws Exception {
        return http
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
