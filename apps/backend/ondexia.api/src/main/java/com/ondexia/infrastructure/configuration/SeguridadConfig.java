package com.ondexia.infrastructure.configuration;

import java.util.List;
import java.util.stream.Stream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
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

    /**
     * El emisor de tokens de desarrollo, abierto <strong>solo</strong> con el
     * perfil {@code local}. Ver {@link #rutasPublicas()}.
     */
    private static final String RUTA_TOKEN_DESARROLLO = "/desarrollo/**";

    private final PropiedadesCors propiedadesCors;
    private final Environment entorno;

    public SeguridadConfig(PropiedadesCors propiedadesCors, Environment entorno) {
        this.propiedadesCors = propiedadesCors;
        this.entorno = entorno;
    }

    /**
     * Las rutas abiertas, mas la de desarrollo si el perfil es {@code local}.
     *
     * <h2>Por que hace falta</h2>
     *
     * <p>Sin abrirla, el perfil {@code local} es inservible desde un navegador,
     * y de una forma que cuesta atribuir: {@code SeguridadDesarrolloConfig}
     * instala tambien su propio {@code JwtDecoder}, de modo que la API local
     * <strong>solo</strong> acepta tokens firmados con su clave en memoria —los
     * de Cognito no valen— y la unica ruta que emite uno exigia ya llevarlo.
     * Para pedir el primer token hacia falta un token.
     *
     * <p>Paso inadvertido porque ninguna prueba tocaba el endpoint: las de
     * integracion se sirven el {@code JwtEncoder} directamente, sin pasar por
     * HTTP. Lo cubre ahora {@code TokenDesarrolloIT}.
     *
     * <h2>Por que se comprueba el perfil</h2>
     *
     * <p>{@code TokenDesarrolloController} ya lleva {@code @Profile("local")},
     * asi que en {@code aws} la ruta no existe y responderia 404 igualmente.
     * Pero una ruta abierta cuya seguridad depende de que una clase no este
     * presente es una garantia mas debil de lo que conviene aqui: si alguien le
     * quita el {@code @Profile}, el endpoint queda publico en produccion sin
     * que nada en esta clase haya cambiado. Comprobando el perfil, el patron no
     * llega a registrarse.
     */
    private String[] rutasPublicas() {
        if (!entorno.acceptsProfiles(Profiles.of("local"))) {
            return RUTAS_PUBLICAS;
        }
        return Stream.concat(Stream.of(RUTAS_PUBLICAS), Stream.of(RUTA_TOKEN_DESARROLLO))
                .toArray(String[]::new);
    }

    /**
     * Habilita la sustitución de parámetros en las meta-anotaciones de
     * seguridad, que es lo que hace funcionar a {@code @RequierePermiso}.
     *
     * <p>Sin este bean la anotación no falla: la expresión llega literal con
     * las llaves sin sustituir, el evaluador busca un permiso llamado
     * <code>{modulo}:{accion}</code> y deniega siempre. Es decir, **el sistema
     * queda cerrado a cal y canto sin dar ningún error**, que es difícil de
     * atribuir. Lo cubre una prueba en {@code PermisosAnotacionIT}.
     *
     * <p>Es {@code static} porque lo consume el post-procesador de seguridad de
     * métodos, que se inicializa antes que el resto de la configuración.
     */
    @Bean
    static AnnotationTemplateExpressionDefaults plantillasDeAnotacionesDeSeguridad() {
        return new AnnotationTemplateExpressionDefaults();
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
                // La fuente se pasa explicitamente, y no con
                // Customizer.withDefaults(). Aquello la busca por NOMBRE de bean
                // —«corsConfigurationSource» o «corsFilter»—, y el metodo de
                // abajo se llama de otra forma: Spring Security no encontraba
                // ninguno, no instalaba el filtro y no se quejaba. El preflight
                // respondia 200 sin una sola cabecera Access-Control-*, el
                // navegador bloqueaba la peticion, y en el servidor no aparecia
                // nada raro. En AWS pasa inadvertido porque API Gateway pone las
                // suyas; se nota al servir el SPA contra la API local.
                //
                // Tampoco se inyecta por tipo: hay DOS beans que implementan
                // CorsConfigurationSource —el de abajo y el
                // mvcHandlerMappingIntrospector de Spring MVC—, asi que por tipo
                // es ambiguo y el contexto no arranca. Que haya dos es
                // precisamente el motivo de que Spring Security resuelva por
                // nombre. Se pasa el metodo directamente: sin nombre magico, sin
                // cualificador y sin ambiguedad. Lo cubre CorsIT.
                .cors(cors -> cors.configurationSource(fuenteConfiguracionCors()))

                // Sin sesion en servidor. Cada peticion se autentica sola. Es
                // requisito de Lambda: no hay dos invocaciones que compartan
                // estado de forma fiable.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(rutas -> rutas
                        // Las peticiones de sondeo de CORS no llevan cabecera
                        // Authorization por definicion del navegador.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(rutasPublicas()).permitAll()
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
