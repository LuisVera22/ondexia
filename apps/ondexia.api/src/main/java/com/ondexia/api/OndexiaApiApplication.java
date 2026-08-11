package com.ondexia.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// Spring Boot 4 movio esta anotacion desde
// org.springframework.boot.autoconfigure.domain al modulo spring-boot-persistence.
// Los ejemplos que circulan siguen usando el paquete viejo.
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * API Core de Ondexia.
 *
 * <p>Las entidades y los repositorios viven en {@code ondexia-domain}, un modulo
 * Maven distinto, asi que el escaneo automatico de Spring Boot no los alcanza:
 * solo mira bajo el paquete de esta clase. De ahi las dos anotaciones
 * explicitas. Sin ellas la aplicacion arranca sin errores y falla al primer
 * repositorio inyectado, que es un sintoma poco obvio.
 */
@SpringBootApplication
@EntityScan(basePackages = "com.ondexia.domain")
@EnableJpaRepositories(basePackages = "com.ondexia.domain")
public class OndexiaApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OndexiaApiApplication.class, args);
    }
}
