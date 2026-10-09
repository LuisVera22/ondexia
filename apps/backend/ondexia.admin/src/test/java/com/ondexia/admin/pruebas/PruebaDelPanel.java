package com.ondexia.admin.pruebas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base de las pruebas del panel.
 *
 * <h2>PostgreSQL de verdad y el esquema de verdad</h2>
 *
 * <p>El servidor se arranca una vez y se comparte —contenedor o PostgreSQL
 * externo, lo decide {@link ServidorDePruebas}—. Las migraciones salen de
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

    private static final String BASE = "ondexia_panel_pruebas";

    // Testcontainers 2 retiro la extension de JUnit 5, asi que se arranca a mano.
    private static final ServidorDePruebas SERVIDOR = ServidorDePruebas.arrancar();

    static {
        crearBaseVacia();
    }

    /**
     * Una base propia y vacia en cada ejecucion. En un contenedor sobra; en un
     * servidor externo es lo que impide que las migraciones encuentren el
     * esquema de la vez anterior.
     */
    private static void crearBaseVacia() {
        try (java.sql.Connection conexion = java.sql.DriverManager.getConnection(
                SERVIDOR.urlBootstrap(), SERVIDOR.usuario(), SERVIDOR.contrasena());
                java.sql.Statement sentencia = conexion.createStatement()) {
            ServidorDePruebas.borrarRestosDeEjecucionesAnteriores(sentencia);
            sentencia.execute("CREATE DATABASE " + BASE);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("No se pudo preparar la base de pruebas del panel", e);
        }
    }

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> "jdbc:postgresql://"
                + SERVIDOR.anfitrion() + ":" + SERVIDOR.puerto() + "/" + BASE);
        registro.add("spring.datasource.username", SERVIDOR::usuario);
        registro.add("spring.datasource.password", SERVIDOR::contrasena);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;
}
