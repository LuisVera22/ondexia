package com.ondexia.consultas.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.Ubigeo;
import com.ondexia.domain.consultas.CondicionDomicilio;
import com.ondexia.domain.consultas.ConsultaDeRuc;
import com.ondexia.domain.consultas.DatosDeRuc;
import com.ondexia.domain.consultas.EstadoContribuyente;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
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
 * La cuota de punta a punta, con una cuota de dos para no escribir seis
 * peticiones. Va en su propia clase porque el contexto de Spring se cachea por
 * configuración: bajar la cuota en {@code ConsultaDeRucControllerTest}
 * rompería sus pruebas, que consultan varias veces con el mismo {@code sub}.
 */
@SpringBootTest(properties = {
    "ondexia.consultas.firma-privada="
            + "MC4CAQAwBQYDK2VwBCIEIKA3itKbjL1Lu/BLbAXSt2igZr8kb8tD5uSNcRYckQ+w",
    "ondexia.consultas.decolecta-token=no-se-usa",
    "ondexia.consultas.cuota-por-hora=2",
})
@AutoConfigureMockMvc
class CuotaDeConsultasTest {

    private static final String RUC = "20601030013";

    /** Cuántas veces se llamó al proveedor: la cuota se aplica ANTES de gastar. */
    private static final AtomicInteger LLAMADAS_AL_PADRON = new AtomicInteger();

    @TestConfiguration
    static class PadronContado {

        @Bean
        @Primary
        ConsultaDeRuc padronContado() {
            return ruc -> {
                LLAMADAS_AL_PADRON.incrementAndGet();
                return Optional.of(new DatosDeRuc(new Ruc(RUC), "ONDEXIA S.A.C.",
                        EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO, "AV. AREQUIPA 100",
                        new Ubigeo("150101"), "LIMA", "LIMA", "LIMA", false, false, null,
                        Instant.parse("2026-09-07T10:00:00Z")));
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private static String tokenDe(String sub) {
        var codificador = Base64.getUrlEncoder().withoutPadding();
        return "Bearer "
                + codificador.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8))
                + "."
                + codificador.encodeToString(("{\"sub\":\"" + sub + "\"}")
                        .getBytes(StandardCharsets.UTF_8))
                + ".";
    }

    @Test
    @DisplayName("La tercera consulta de la misma identidad responde 429 sin gastar el proveedor")
    void laTerceraNoGastaElProveedor() throws Exception {
        String token = tokenDe("sub-con-cuota");
        LLAMADAS_AL_PADRON.set(0);

        mockMvc.perform(get("/consultas/ruc/" + RUC).header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/consultas/ruc/" + RUC).header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/consultas/ruc/" + RUC).header("Authorization", token))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.codigo").value("cuota_de_consultas_agotada"))
                .andExpect(jsonPath("$.reintentable").value(true));

        org.assertj.core.api.Assertions.assertThat(LLAMADAS_AL_PADRON.get())
                .as("el proveedor de pago no se llama cuando la cuota ya esta agotada")
                .isEqualTo(2);

        // Otra identidad no paga por la primera.
        mockMvc.perform(get("/consultas/ruc/" + RUC).header("Authorization", tokenDe("sub-otra")))
                .andExpect(status().isOk());
    }
}
