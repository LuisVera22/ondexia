package com.ondexia.api.desarrollo.web;

import io.swagger.v3.oas.annotations.Hidden;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Emite un token de desarrollo. Solo con el perfil {@code local}.
 *
 * <p>Sustituye al inicio de sesion contra Cognito mientras no haya pool
 * desplegado. Ver {@link com.ondexia.api.desarrollo.SeguridadDesarrolloConfig} para por que esto no
 * debilita el diseno.
 *
 * <p>Uso:
 *
 * <pre>{@code
 * curl -X POST "http://localhost:8080/desarrollo/token?sub=usuario-demo"
 * }</pre>
 *
 * <p>El {@code sub} debe coincidir con el {@code cognito_sub} de una fila de
 * {@code usuario}. Los datos de ejemplo de la migracion V3 crean uno.
 */
@Hidden // Fuera del contrato OpenAPI: no forma parte de la API del producto.
@RestController
@RequestMapping("/desarrollo")
@Profile("local")
public class TokenDesarrolloController {

    private final JwtEncoder emisor;

    public TokenDesarrolloController(JwtEncoder emisor) {
        this.emisor = emisor;
    }

    /**
     * @param sub identificador del usuario, equivalente al {@code sub} que
     *            emitiria Cognito
     */
    @PostMapping("/token")
    public RespuestaToken emitir(@RequestParam String sub) {
        Instant ahora = Instant.now();
        Duration vigencia = Duration.ofHours(8);

        // Se replican los claims que emite Cognito y que el codigo consume, para
        // que el camino sea el mismo. Los grupos no se incluyen a proposito: los
        // permisos no viajan en el token (DTE §8.1), y ponerlos aqui invitaria a
        // leerlos desde el.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://desarrollo.ondexia.local")
                .subject(sub)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(vigencia))
                .claim("token_use", "access")
                .build();

        String token = emisor.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new RespuestaToken(token, "Bearer", vigencia.toSeconds());
    }

    public record RespuestaToken(String accessToken, String tokenType, long expiresIn) {
    }
}
