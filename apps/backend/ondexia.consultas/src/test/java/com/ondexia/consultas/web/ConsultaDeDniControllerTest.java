package com.ondexia.consultas.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.domain.consultas.ConsultaDeDni;
import com.ondexia.domain.consultas.ConsultaNoDisponible;
import com.ondexia.domain.consultas.DatosDeDni;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La consulta de DNI: nombre para la boleta, sin firma y con la misma cuota.
 */
@SpringBootTest(properties = {
    "ondexia.consultas.firma-privada="
            + "MC4CAQAwBQYDK2VwBCIEIKA3itKbjL1Lu/BLbAXSt2igZr8kb8tD5uSNcRYckQ+w",
    "ondexia.consultas.decolecta-token=no-se-usa",
    "ondexia.consultas.cuota-por-hora=1000",
})
@AutoConfigureMockMvc
class ConsultaDeDniControllerTest {

    private static final String SOLICITANTE = "sub-de-prueba";

    private static final String TOKEN = "Bearer "
            + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    "{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
            + "."
            + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ("{\"sub\":\"" + SOLICITANTE + "\"}")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8))
            + ".";

    private static Function<String, Optional<DatosDeDni>> respuesta;

    @TestConfiguration
    static class ReniecDeMentira {

        @Bean
        @Primary
        ConsultaDeDni reniecSustituido() {
            return dni -> respuesta.apply(dni);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Devuelve el nombre en el orden de la boleta: apellidos y nombres")
    void devuelveElNombre() throws Exception {
        respuesta = dni -> Optional.of(new DatosDeDni(dni, "ROSA MARÍA", "QUISPE", "MAMANI",
                Instant.parse("2026-09-07T10:00:00Z")));

        mockMvc.perform(get("/consultas/dni/45678912").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dni").value("45678912"))
                .andExpect(jsonPath("$.nombres").value("ROSA MARÍA"))
                .andExpect(jsonPath("$.nombreCompleto").value("QUISPE MAMANI ROSA MARÍA"))
                .andExpect(jsonPath("$.consultadoEn").value("2026-09-07T10:00:00Z"))
                // Sin atestación: no hay nada fiscal que firmar.
                .andExpect(jsonPath("$.atestacion").doesNotExist());
    }

    @Test
    @DisplayName("Un DNI que RENIEC no conoce da 404")
    void noEncontrado() throws Exception {
        respuesta = dni -> Optional.empty();

        mockMvc.perform(get("/consultas/dni/00000001").header("Authorization", TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("dni_no_encontrado"));
    }

    @Test
    @DisplayName("Un DNI mal formado no gasta consulta")
    void malFormado() throws Exception {
        respuesta = dni -> {
            throw new AssertionError("no debería consultar");
        };

        mockMvc.perform(get("/consultas/dni/1234").header("Authorization", TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Sin proveedor disponible responde 503 y dice si se puede reintentar")
    void proveedorCaido() throws Exception {
        respuesta = dni -> {
            throw new ConsultaNoDisponible("consulta_no_disponible", "Caído", true);
        };

        mockMvc.perform(get("/consultas/dni/45678912").header("Authorization", TOKEN))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reintentable").value(true));
    }

    @Test
    @DisplayName("Sin solicitante no hay consulta")
    void sinToken() throws Exception {
        mockMvc.perform(get("/consultas/dni/45678912"))
                .andExpect(status().isUnauthorized());
    }
}
