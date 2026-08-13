package com.ondexia.infrastructure.configuration;

import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Decodificador de tokens para el despliegue en Lambda.
 *
 * <h2>Por qué no se verifica la firma aquí</h2>
 *
 * <p>Porque no se puede, y porque ya está hecha.
 *
 * <p><strong>No se puede:</strong> verificar la firma exige descargar el JWKS
 * de Cognito, y la función vive en una subred privada sin NAT ni endpoints de
 * interfaz — una decisión de costo deliberada (DTE §4.8: un endpoint de
 * interfaz cuesta ~7.30 USD/mes por zona, más que toda la factura de dev). El
 * intento termina en {@code SocketTimeoutException} tras agotar la espera, y el
 * resultado es un 502 en cada petición.
 *
 * <p><strong>Ya está hecha:</strong> API Gateway lleva un autorizador JWT
 * nativo configurado contra el emisor y la audiencia del pool
 * ({@code aws_apigatewayv2_authorizer}). Un token con firma inválida, caducado,
 * de otro pool o de otro cliente <em>nunca llega hasta aquí</em>: la pasarela
 * responde 401 sin invocar la función. Ese autorizador fue, de hecho, una de
 * las razones para elegir Cognito (DTE §7).
 *
 * <h2>Qué se comprueba, entonces</h2>
 *
 * <p>Todo lo que no necesita red: que el token esté bien formado, que el emisor
 * sea el nuestro y que no haya caducado. Repetir esas tres es barato y protege
 * de una configuración equivocada de la pasarela; la firma es la única que se
 * delega, por imposibilidad física.
 *
 * <h2>La contrapartida, dicha claro</h2>
 *
 * <p>Quien pudiera invocar la función directamente —saltándose la pasarela—
 * podría presentar un token con la firma que quisiera. Eso lo acota el permiso
 * de invocación, que está restringido al ARN de ejecución de esta API concreta
 * ({@code aws_lambda_permission.api_gateway}), no a «cualquiera de la cuenta».
 *
 * <p>Si algún día se quiere la verificación completa dentro de la función, la
 * vía es un endpoint de interfaz de VPC para {@code cognito-idp}, y es una
 * decisión de gasto, no de código.
 */
@Configuration
@Profile("aws")
public class SeguridadPasarelaConfig {

    private final String emisorEsperado;

    public SeguridadPasarelaConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String emisorEsperado) {
        this.emisorEsperado = emisorEsperado;
    }

    /**
     * Al existir este bean, la autoconfiguración de Spring Boot se retira y no
     * intenta construir el suyo contra el JWKS remoto.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return this::decodificar;
    }

    private Jwt decodificar(String token) {
        SignedJWT firmado;
        try {
            firmado = SignedJWT.parse(token);
        } catch (ParseException e) {
            throw new BadJwtException("El token no tiene forma de JWT.", e);
        }

        var reclamaciones = leerReclamaciones(firmado);

        String emisor = (String) reclamaciones.get("iss");
        if (!emisorEsperado.equals(emisor)) {
            // Un token válido de OTRO pool de Cognito llevaría firma correcta y
            // no serviría aquí. La pasarela ya lo filtra; comprobarlo también
            // aquí cuesta una comparación de cadenas.
            throw new BadJwtException("El token no lo emitió el pool esperado.");
        }

        Instant emitido = instante(reclamaciones.get("iat"));
        Instant expira = instante(reclamaciones.get("exp"));

        if (expira != null && Instant.now().isAfter(expira)) {
            throw new BadJwtException("El token ha caducado.");
        }

        return new Jwt(token, emitido, expira, cabeceras(firmado), reclamaciones);
    }

    /**
     * Lee el cuerpo como JSON crudo, no a través de {@code getJWTClaimsSet()}.
     *
     * <p>Esa vía convierte {@code exp} e {@code iat} a {@link java.util.Date},
     * que además de estar prohibido en este proyecto —mutable y de zona
     * ambigua— dejaría objetos {@code Date} dentro del mapa de reclamaciones
     * del {@link Jwt}, donde los vería todo el código de abajo.
     *
     * <p>La regla de ArchUnit lo detectó en el primer intento.
     */
    private static java.util.Map<String, Object> leerReclamaciones(SignedJWT firmado) {
        var cuerpo = firmado.getPayload().toJSONObject();
        if (cuerpo == null) {
            throw new BadJwtException("El cuerpo del token no es legible.");
        }
        return cuerpo;
    }

    private static java.util.Map<String, Object> cabeceras(SignedJWT firmado) {
        return firmado.getHeader().toJSONObject();
    }

    /** Las marcas de tiempo de un JWT son segundos desde época, no milisegundos. */
    private static Instant instante(Object valor) {
        return valor instanceof Number numero ? Instant.ofEpochSecond(numero.longValue()) : null;
    }
}
