package com.ondexia.admin.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Los grupos de Cognito se convierten en autoridades (hallazgo A5).
 *
 * <p>Es la mitad que las pruebas de los controladores no cubren: el
 * postprocesador {@code jwt()} de Spring construye la autenticación él mismo y no
 * pasa por el conversor, así que allí las autoridades se ponen a mano. Aquí se
 * comprueba lo contrario — que un token real con {@code cognito:groups} produce
 * las autoridades que las reglas esperan.
 *
 * <p>Sin esta conversión, un token de Cognito llega SIN NINGUNA autoridad: Spring
 * busca por omisión {@code scope} o {@code scp}, que Cognito no emite. Con
 * {@code anyRequest().authenticated()} eso no se notaba —estar en el pool
 * bastaba—, y es justo lo que el hallazgo describe.
 */
class GruposDelPersonalTest {

    private final SeguridadAdmin seguridad = new SeguridadAdmin();

    private static Jwt tokenCon(Object grupos) {
        var reclamos = new java.util.HashMap<String, Object>();
        reclamos.put("sub", "sub-de-prueba");
        if (grupos != null) {
            reclamos.put("cognito:groups", grupos);
        }
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"), reclamos);
    }

    private List<String> autoridadesDe(Object grupos) {
        var autenticacion = seguridad.conversorDeGrupos().convert(tokenCon(grupos));
        return autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                // Solo los ROLE_: Spring Security anade ademas FACTOR_BEARER,
                // que dice como se autentico y no a que grupo pertenece. Las
                // reglas de la cadena miran los ROLE_.
                .filter(autoridad -> autoridad.startsWith("ROLE_"))
                .sorted()
                .toList();
    }

    @Test
    @DisplayName("Un miembro de operaciones llega con ROLE_operaciones")
    void unGrupo() {
        assertThat(autoridadesDe(List.of("operaciones"))).containsExactly("ROLE_operaciones");
    }

    @Test
    @DisplayName("Quien está en los dos grupos llega con los dos")
    void dosGrupos() {
        assertThat(autoridadesDe(List.of("soporte", "operaciones")))
                .containsExactly("ROLE_operaciones", "ROLE_soporte");
    }

    /**
     * El caso que importa: una cuenta del pool que no está en ningún grupo.
     *
     * <p>Es lo que le pasa a la primera cuenta que se le crea a alguien. Antes
     * podía cambiar el plan de un cliente; ahora no llega con ninguna autoridad y
     * la cadena de filtros la rechaza.
     */
    @Test
    @DisplayName("Sin grupos no hay ninguna autoridad")
    void sinGrupos() {
        assertThat(autoridadesDe(null)).isEmpty();
    }
}
