package com.ondexia.infrastructure.configuration;

import com.ondexia.infrastructure.seguridad.ContextoInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registro del interceptor que establece el contexto multiempresa.
 *
 * <p>Se excluyen las rutas publicas por una razon concreta: el interceptor
 * exige un token y consulta la base de datos, y {@code /salud} debe responder
 * incluso <strong>si la base esta caida</strong>. Si la sonda de vida dependiera
 * de la base, un problema de conectividad haria que API Gateway diera la Lambda
 * por muerta y el diagnostico apuntaria al sitio equivocado.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesCors.class)
public class WebConfig implements WebMvcConfigurer {

    private static final String[] RUTAS_SIN_CONTEXTO = {
        "/salud",
        "/actuator/**",
        // Las tres variantes del contrato son rutas distintas: /v3/api-docs/**
        // no cubre /v3/api-docs (no hay barra que consumir) ni /v3/api-docs.yaml
        // (el punto no es un separador de ruta). Faltando alguna, el interceptor
        // exige token y el contrato responde 401 — que es como se detecto.
        "/v3/api-docs",
        "/v3/api-docs/**",
        "/v3/api-docs.yaml",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/desarrollo/**",
    };

    private final ContextoInterceptor contextoInterceptor;

    public WebConfig(ContextoInterceptor contextoInterceptor) {
        this.contextoInterceptor = contextoInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(contextoInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(RUTAS_SIN_CONTEXTO);
    }
}
