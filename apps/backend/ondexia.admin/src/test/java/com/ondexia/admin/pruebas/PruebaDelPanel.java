package com.ondexia.admin.pruebas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de las pruebas del panel.
 *
 * <h2>PostgreSQL de verdad y el esquema de verdad</h2>
 *
 * <p>El contenedor se arranca una vez y se comparte. Las migraciones salen de
 * {@code ondexia.api}, que es su dueño (doc 09 §6.1), traídas aquí solo en ámbito
 * de prueba: el panel nunca migra, pero sus consultas tienen que correr contra el
 * esquema real. Con uno inventado para la prueba, cambiar una columna no rompería
 * nada aquí y sí en producción.
 *
 * <p>Sin los datos de ejemplo de {@code db/local}: cada prueba siembra lo suyo, y
 * así se ve en la propia prueba qué se está contando.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PruebaDelPanel {

    // Testcontainers 2 retiro la extension de JUnit 5, asi que se arranca a mano.
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;
}
