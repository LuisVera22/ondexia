package com.ondexia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Core de Ondexia.
 *
 * <p>Sin {@code @EntityScan} ni {@code @EnableJpaRepositories}. Antes hacían
 * falta porque las entidades vivían en otro módulo Maven, fuera del alcance del
 * escaneo automático. Ahora las entidades JPA y sus repositorios están en
 * {@code com.ondexia.infrastructure.salida.persistencia}, bajo el paquete de
 * esta clase, y Spring Boot los encuentra solo.
 *
 * <p>Es un efecto secundario agradable de la reestructuración: lo que antes
 * exigía configuración explícita —y fallaba de forma poco obvia si se
 * olvidaba— ahora se deduce de dónde está cada cosa.
 *
 * <p>Ver {@code ondexia.docs/08-arquitectura-backend.md}.
 */
@SpringBootApplication
public class OndexiaApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OndexiaApiApplication.class, args);
    }
}
