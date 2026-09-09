package com.ondexia.infrastructure.entrada.web.comun;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sonda de vida.
 *
 * <p>La consulta API Gateway, que la tiene declarada como ruta abierta en
 * {@code ondexia.infra/api.tf}. Si el nombre de esta ruta cambia, hay que
 * cambiarlo tambien alli.
 *
 * <p><strong>No toca la base de datos, y es deliberado.</strong> Una sonda que
 * comprueba la base responde «enfermo» cuando la base esta caida, y entonces el
 * balanceador retira la Lambda — con lo que un problema de conectividad se
 * presenta como una API que no existe, en lugar de como una API que responde
 * 503 explicando que le pasa. Para saber si la base responde esta
 * {@code /actuator/health}, que es otra pregunta y tiene otra respuesta.
 */
@RestController
public class SaludController {

    private final String version;

    public SaludController(@Value("${ondexia.version:desconocida}") String version) {
        this.version = version;
    }

    @Operation(
            summary = "Sonda de vida",
            description = "Responde si el proceso esta en pie. No comprueba dependencias.")
    @SecurityRequirements // Sin token: la sonda no se autentica.
    @GetMapping("/salud")
    public Salud consultar() {
        return new Salud("vivo", version);
    }

    /**
     * @param version version desplegada. Se devuelve porque responde a una
     *                pregunta que llega tarde o temprano: «¿con que version del
     *                sistema se emitio este comprobante?» (doc 03 §6.4)
     */
    public record Salud(String estado, String version) {
    }
}
