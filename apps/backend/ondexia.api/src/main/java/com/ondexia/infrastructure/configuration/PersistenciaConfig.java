package com.ondexia.infrastructure.configuration;

import com.ondexia.infrastructure.salida.persistencia.comun.GestorTransaccionesConAislamiento;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Sustituye el gestor de transacciones estandar por el que fija el inquilino de
 * PostgreSQL.
 *
 * <p>Declarar este bean desplaza al que Spring Boot crearia por su cuenta, de
 * modo que <strong>toda</strong> transaccion de la aplicacion pasa por el. Eso
 * es lo que hace que el aislamiento no dependa de que alguien se acuerde de
 * anotar algo: no hay forma de abrir una transaccion sin que se aplique.
 *
 * <p>Ver {@link GestorTransaccionesConAislamiento} para el razonamiento
 * completo, incluida la trampa de reutilizacion de conexiones en Lambda.
 */
@Configuration
@EnableTransactionManagement
public class PersistenciaConfig {

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory fabrica,
            com.ondexia.domain.comun.ProveedorDeContexto contexto) {
        return new GestorTransaccionesConAislamiento(fabrica, contexto);
    }
}
