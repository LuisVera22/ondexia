package com.ondexia.infrastructure.configuration;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;

/**
 * Conexión a la base sin contraseña, solo en la plataforma.
 *
 * <p>Declarar este bean desplaza al que Spring Boot crearía a partir de
 * {@code spring.datasource.password}, que ya no existe en el perfil {@code aws}.
 *
 * <p>Fuera de {@code aws} no se crea nada: en local y en pruebas se conecta con
 * usuario y contraseña contra el PostgreSQL del contenedor, porque
 * <strong>la autenticación por IAM no existe fuera de RDS</strong>. Esa es la
 * consecuencia incómoda de este cambio y conviene tenerla escrita: la ruta que
 * usa producción <em>no la cubre ninguna prueba de integración</em>. Se verifica
 * desplegando, como el mojibake y los JAR multi-release.
 */
@Configuration
@Profile("aws")
public class BaseDeDatosIamConfig {

    /**
     * Hikari por encima, y por debajo la fuente que firma el token.
     *
     * <p>Se le pasa la fuente en vez de una URL con credenciales porque es la
     * única forma de que el token se genere <strong>por conexión física</strong>:
     * Hikari resuelve la URL una sola vez al arrancar el pool, así que una
     * contraseña puesta ahí sería la misma para siempre — y estas caducan a los
     * quince minutos.
     *
     * <p>{@code @ConfigurationProperties} conserva lo que ya estaba afinado en
     * {@code application.yml}: el tamaño del pool, los tiempos de espera y la
     * suspensión que necesita SnapStart. Sin esta línea, el pool volvería a sus
     * valores por omisión —diez conexiones por contenedor— y con diez
     * contenedores eso son cien contra una {@code db.t4g.micro}.
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String usuario,
            @Value("${BD_HOST}") String anfitrion,
            @Value("${BD_PUERTO}") int puerto) {

        var hikari = new HikariDataSource();
        hikari.setDataSource(new FuenteDeDatosIam(
                url, usuario, anfitrion, puerto, Region.of(System.getenv("AWS_REGION"))));
        return hikari;
    }
}
