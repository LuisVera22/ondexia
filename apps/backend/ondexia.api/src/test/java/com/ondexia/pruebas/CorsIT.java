package com.ondexia.pruebas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Que la configuracion de CORS llegue de verdad al filtro.
 *
 * <p>Existe porque no llegaba. El bean estaba bien escrito y bien poblado, pero
 * {@code .cors(Customizer.withDefaults())} lo busca <strong>por nombre</strong>
 * —{@code corsConfigurationSource} o {@code corsFilter}—, y el metodo se llamaba
 * de otra forma. Spring Security no encontraba ninguno, no instalaba el filtro y
 * no se quejaba: el preflight respondia 200 sin una sola cabecera
 * {@code Access-Control-*}, y el navegador bloqueaba la peticion sin que en el
 * servidor apareciera nada raro.
 *
 * <p>En AWS pasa inadvertido porque API Gateway pone sus propias cabeceras. Se
 * nota al servir el SPA contra la API local, que es la Fase 0 del DTE §10.3.
 */
class CorsIT extends PruebaIntegracion {

    private static final String ORIGEN_SPA = "http://localhost:4200";

    @Test
    @DisplayName("El preflight de la API autoriza el origen del servidor de desarrollo")
    void elPreflightAutorizaElOrigenDelSpa() throws Exception {
        mockMvc.perform(options("/api/v1/contexto")
                        .header(HttpHeaders.ORIGIN, ORIGEN_SPA)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGEN_SPA));
    }

    @Test
    @DisplayName("Un origen que no esta configurado no se autoriza")
    void unOrigenAjenoNoSeAutoriza() throws Exception {
        // La otra mitad. Sin esto, la primera prueba pasaria igual con un
        // comodin, que es lo que no se quiere: allowCredentials va activo y un
        // `*` ahi no solo esta prohibido por la especificacion, seria dejar que
        // cualquier pagina llame a la API con las credenciales del usuario.
        mockMvc.perform(options("/api/v1/contexto")
                        .header(HttpHeaders.ORIGIN, "https://sitio-ajeno.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}
