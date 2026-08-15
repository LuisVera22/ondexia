package com.ondexia.admin.configuracion;

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
 * El pool de conexiones del panel, autenticado por IAM.
 *
 * <p>Solo con el perfil {@code aws}: fuera de la nube no hay rol que firmar, y
 * las pruebas usan el contenedor de PostgreSQL con usuario y contraseña.
 *
 * <p>El usuario es <strong>{@code ondexia_panel}</strong>, no {@code ondexia_app}
 * ni {@code ondexia_admin}. Ahí está la mitad de la separación entre esta consola
 * y la API de clientes: aunque este código se equivocara e intentara leer una
 * serie de comprobantes, la base se lo negaría por falta de concesión.
 */
@Configuration
@Profile("aws")
class BaseDeDatosIamConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    DataSource dataSource(
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
