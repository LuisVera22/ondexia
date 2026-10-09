package com.ondexia.pruebas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * El emisor de tokens del perfil {@code local}, por HTTP.
 *
 * <h2>Por que existe esta clase</h2>
 *
 * <p>El endpoint estaba roto y nadie lo sabia: la cadena de seguridad denegaba
 * por defecto y {@code /desarrollo/**} no figuraba entre las rutas abiertas, asi
 * que respondia 401. Para pedir el primer token hacia falta un token.
 *
 * <p>El fallo sobrevivio porque las demas pruebas de integracion no usan el
 * endpoint: se sirven el {@code JwtEncoder} directamente y se ahorran el viaje
 * por HTTP. Es un buen recordatorio de que probar el servicio no prueba que sea
 * alcanzable — el mismo motivo por el que {@code ConfiguracionEmpresaIT} va por
 * HTTP y no llamando a los casos de uso.
 */
class TokenDesarrolloIT extends PruebaIntegracion {

    private static final String TOKEN = "/desarrollo/token";

    @Test
    @DisplayName("El emisor de desarrollo no exige token para dar el primero")
    void elEmisorNoExigeTokenParaDarElPrimero() throws Exception {
        mockMvc.perform(post(TOKEN).param("sub", SUB_DEMO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("El token que emite sirve para entrar de verdad")
    void elTokenEmitidoAbreLaApi() throws Exception {
        // La comprobacion que de verdad importa: que lo que emite lo acepta el
        // servidor de recursos. Un 200 en la ruta de emision solo diria que
        // devuelve una cadena; esto dice que la cadena es un token valido y que
        // detras hay un usuario que se resuelve.
        String cuerpo = mockMvc.perform(post(TOKEN).param("sub", SUB_DEMO))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = cuerpo.replaceAll(".*\"accessToken\"\s*:\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/contexto")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value(USUARIO_DEMO));
    }
}
