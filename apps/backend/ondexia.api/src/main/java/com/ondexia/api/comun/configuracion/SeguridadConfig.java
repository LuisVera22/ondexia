package com.ondexia.api.comun.configuracion;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cadena de seguridad HTTP.
 *
 * <p>El unico trabajo de esta clase es <strong>autenticar</strong>: comprobar
 * que el token existe y que su firma es de nuestro pool de Cognito. La
 * autorizacion fina no vive aqui — se resuelve por endpoint con
 * {@code @PreAuthorize} y {@code EvaluadorPermisos}, contra la base de datos.
 *
 * <p>Esa separacion es DT-05 y §8.1 del DTE: la autenticacion se compra, la
 * autorizacion se construye.
 */
@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

    /** Rutas abiertas. La lista es corta a proposito y cada entrada se justifica. */
    private static final String[] RUTAS_PUBLICAS = {
        // Sonda de vida. La consulta API Gateway y no lleva token — si exigiera
        // autenticacion, la comprobacion de salud fallaria siempre.
        "/salud",
        // Actuator, restringido a health en application.yml. No expone metricas
        // ni variables de entorno.
        "/actuator/health",
        "/actuator/health/**",
        // El contrato OpenAPI y su interfaz. Publicarlos es deliberado: el
        // contrato describe la forma de la API, no sus datos, y tenerlo abierto
        // es lo que permite generar el cliente sin credenciales.
        "/v3/api-docs",
        "/v3/api-docs/**",
        // La variante .yaml es una ruta distinta, no un sufijo de la anterior:
        // /v3/api-docs/** no la cubre porque no hay barra de por medio. Es la que
        // consume la exportacion del contrato.
        "/v3/api-docs.yaml",
        "/swagger-ui.html",
        "/swagger-ui/**",
    };

    private final PropiedadesCors propiedadesCors;

    public SeguridadConfig(PropiedadesCors propiedadesCors) {
        this.propiedadesCors = propiedadesCors;
    }

    @Bean
    public SecurityFilterChain cadenaSeguridad(HttpSecurity http) throws Exception {
        http
                // Sin CSRF: no hay cookies de sesion que proteger. El ataque que
                // CSRF previene depende de que el navegador adjunte una
                // credencial sola; un token que el SPA pone a mano en la
                // cabecera Authorization no viaja solo. Desactivarlo aqui es
                // correcto, y dejarlo activo obligaria a un token adicional que
                // no protege de nada.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())

                // Sin sesion en servidor. Cada peticion se autentica sola. Es
                // requisito de Lambda: no hay dos invocaciones que compartan
                // estado de forma fiable.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(rutas -> rutas
                        // Las peticiones de sondeo de CORS no llevan cabecera
                        // Authorization por definicion del navegador.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        // Denegar por defecto: cualquier ruta que no se haya
                        // nombrado arriba exige token. Anadir un endpoint no
                        // puede dejarlo abierto por olvido.
                        .anyRequest().authenticated())

                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));

        return http.build();
    }

    /**
     * CORS.
     *
     * <p>Los origenes se configuran, no se fijan en el codigo: cambian entre
     * local, dev y produccion. Y se enumeran uno a uno — con
     * {@code allowCredentials} activo, el comodin {@code *} no solo esta
     * prohibido por la especificacion, es que significaria que cualquier pagina
     * de internet puede llamar a esta API con el token del usuario.
     */
    @Bean
    public CorsConfigurationSource fuenteConfiguracionCors() {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(propiedadesCors.origenes());
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("*"));
        configuracion.setAllowCredentials(true);
        configuracion.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", configuracion);
        return fuente;
    }
}
