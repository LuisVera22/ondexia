package com.ondexia.consultas.web;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS para el SPA, y solo importa en local.
 *
 * <h2>Por qué desplegado no hace nada</h2>
 *
 * <p>La ruta cuelga de la misma pasarela que la API, así que para el navegador es
 * el mismo origen que el resto de las llamadas y no hay comprobación previa que
 * atender. Y aunque la hubiera, API Gateway <strong>ignora las cabeceras CORS de
 * la integración</strong> y pone las suyas.
 *
 * <p>En local sí: el SPA está en el 4200 y esto en el 8081 — dos orígenes. Sin
 * esto, el navegador bloquea la respuesta y en la consola solo se ve un error de
 * red sin cuerpo, que no menciona CORS por ningún lado.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final List<String> origenes;

    public CorsConfig(@Value("${ondexia.cors.origenes}") List<String> origenes) {
        this.origenes = origenes;
    }

    @Override
    public void addCorsMappings(CorsRegistry registro) {
        registro.addMapping("/consultas/**")
                .allowedOrigins(origenes.toArray(String[]::new))
                // El SPA manda Authorization, asi que hay comprobacion previa.
                .allowedHeaders("Authorization", "Content-Type")
                .allowedMethods("GET");
    }
}
