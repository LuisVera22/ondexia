package com.ondexia.admin.seguridad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Monta el decodificador de tokens del panel.
 *
 * <h2>Por qué está en su propia clase</h2>
 *
 * <p>Vivía dentro de {@link SeguridadAdmin}, junto a la cadena de filtros. Se
 * separó al escribir {@code FirmaDelTokenIT}: cargar {@code SeguridadAdmin} en
 * una prueba arrastra {@code HttpSecurity}, y con él la autoconfiguración
 * entera, y con ella el {@code DataSource} y Flyway — un contenedor de
 * PostgreSQL para comprobar que una firma no cuadra.
 *
 * <p>Que un control de seguridad sea difícil de probar en aislamiento es motivo
 * suficiente para moverlo. Este monta una sola cosa y se carga solo.
 *
 * <h2>El decodificador va a mano</h2>
 *
 * <p>No lo construye Spring desde {@code issuer-uri}: el suyo descarga la
 * configuración del emisor al primer token, y esta función no tiene por dónde
 * salir a internet. Verifica la firma igualmente, contra el JWKS que inyecta
 * Terraform; el porqué completo está en {@link TokenDeLaPasarela}.
 *
 * <p>Las tres propiedades son obligatorias a propósito: sin valor por omisión,
 * la aplicación no arranca fuera de los perfiles que las declaran. Una consola
 * que ve las cuentas de todos los clientes no debería levantarse con una
 * configuración de token a medias — y desde el hallazgo A2 de la auditoría
 * 2026-09-01, «a medias» incluye no poder verificar firmas.
 */
@Configuration
public class DecodificadorDelPanel {

    @Bean
    JwtDecoder decodificador(@Value("${ondexia.panel.emisor}") String emisor,
            @Value("${ondexia.panel.cliente}") String cliente,
            @Value("${ondexia.panel.jwks}") String jwks) {
        return new TokenDeLaPasarela(emisor, cliente, jwks);
    }
}
