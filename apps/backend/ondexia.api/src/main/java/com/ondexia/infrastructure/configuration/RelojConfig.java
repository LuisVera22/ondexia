package com.ondexia.infrastructure.configuration;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El reloj, como bean, para que los casos de uso que estampan fechas —abrir y
 * cerrar caja, y después emitir— no llamen a {@code Instant.now()} y puedan
 * probarse con una hora fija. UTC siempre: la hora de Lima es cosa de la pantalla.
 */
@Configuration
public class RelojConfig {

    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}
