package com.ondexia.api.comun.configuracion;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origenes autorizados a llamar a la API desde un navegador.
 *
 * <p>Se declaran por configuracion y por entorno. En local es el servidor de
 * desarrollo de Angular; en produccion, el dominio de la aplicacion.
 */
@ConfigurationProperties(prefix = "ondexia.cors")
public record PropiedadesCors(List<String> origenes) {

    public PropiedadesCors {
        origenes = origenes == null ? List.of() : List.copyOf(origenes);
    }
}
