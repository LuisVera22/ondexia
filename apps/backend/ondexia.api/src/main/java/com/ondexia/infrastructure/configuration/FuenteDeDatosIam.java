package com.ondexia.infrastructure.configuration;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;
import javax.sql.DataSource;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsUtilities;

/**
 * Fuente de conexiones que se autentica contra RDS con un token de IAM.
 *
 * <h2>Aquí no hay contraseña, y ese es todo el propósito</h2>
 *
 * <p>El token se genera <strong>firmando localmente</strong> con las
 * credenciales del rol de la función: no abre ninguna conexión de red. Es el
 * mismo mecanismo que firma las subidas a S3, y es lo que permite que esto
 * funcione en una subred privada sin NAT.
 *
 * <p>La alternativa era guardar la contraseña en algún sitio. En una variable de
 * entorno la lee cualquiera con {@code lambda:GetFunctionConfiguration}; en
 * Secrets Manager haría falta un endpoint de interfaz a ~7.30 USD/mes para
 * alcanzarlo desde esta subred. Con IAM no hay nada que guardar.
 *
 * <h2>Un token por conexión física, sin caché</h2>
 *
 * <p>Los tokens caducan a los quince minutos. Cachearlos ahorraría una firma
 * local —que cuesta microsegundos— a cambio de arriesgar el fallo más
 * desagradable posible: una credencial vencida que solo se manifiesta cuando el
 * pool abre una conexión nueva, es decir, bajo carga y no en la primera prueba.
 *
 * <p>Con dos conexiones por contenedor, generar el token cada vez es
 * intrascendente. Se prefiere lo intrascendente y correcto.
 *
 * <h2>TLS obligatorio</h2>
 *
 * <p>RDS exige conexión cifrada para autenticar por IAM. Se pide explícitamente
 * en vez de confiar en que el servidor lo imponga: {@code rds.force_ssl} está
 * en 1 hoy, y un parámetro que alguien cambie mañana no debería degradar esto en
 * silencio.
 */
final class FuenteDeDatosIam implements DataSource {

    private final String url;
    private final String usuario;
    private final String anfitrion;
    private final int puerto;
    private final RdsUtilities firmador;

    FuenteDeDatosIam(String url, String usuario, String anfitrion, int puerto, Region region) {
        this.url = url;
        this.usuario = usuario;
        this.anfitrion = anfitrion;
        this.puerto = puerto;
        // Se construye una vez: no tiene estado de conexión, solo credenciales
        // y región para firmar.
        this.firmador = RdsUtilities.builder().region(region).build();
    }

    @Override
    public Connection getConnection() throws SQLException {
        var propiedades = new Properties();
        propiedades.setProperty("user", usuario);
        propiedades.setProperty("password", tokenNuevo());
        propiedades.setProperty("ssl", "true");
        propiedades.setProperty("sslmode", "require");

        return DriverManager.getConnection(url, propiedades);
    }

    /**
     * Ignora las credenciales que le pasen.
     *
     * <p>HikariCP llama a esta sobrecarga cuando su configuración trae usuario y
     * contraseña. Aquí no hay contraseña que valga: la única credencial válida es
     * un token recién firmado, así que se delega. Aceptar la que llega
     * produciría un fallo de autenticación difícil de atribuir.
     */
    @Override
    public Connection getConnection(String usuarioIgnorado, String claveIgnorada)
            throws SQLException {
        return getConnection();
    }

    private String tokenNuevo() {
        return firmador.generateAuthenticationToken(constructor -> constructor
                .hostname(anfitrion)
                .port(puerto)
                .username(usuario));
    }

    // ── Resto del contrato de DataSource, sin uso aquí ──────────────────────

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter escritor) {
        // El registro lo lleva el pool, no esta fuente.
    }

    @Override
    public void setLoginTimeout(int segundos) {
        // Lo gobierna Hikari con su connection-timeout.
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> tipo) throws SQLException {
        if (tipo.isInstance(this)) {
            return tipo.cast(this);
        }
        throw new SQLException("No es una envoltura de " + tipo.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> tipo) {
        return tipo.isInstance(this);
    }
}
