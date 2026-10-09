package com.ondexia.pruebas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.pruebas.PruebaIntegracion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La sonda de vida y el contrato, que son las dos rutas abiertas del sistema.
 */
class SaludIT extends PruebaIntegracion {

    @Test
    @DisplayName("/salud responde sin token")
    void saludEsPublica() throws Exception {
        // API Gateway la consulta sin credenciales (ondexia.infra/api.tf). Si
        // esta prueba empieza a fallar con un 401, la comprobacion de salud del
        // despliegue fallara igual — y el sintoma alli sera «la Lambda esta
        // muerta», que apunta al sitio equivocado.
        mockMvc.perform(get("/salud"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("vivo"));
    }

    @Test
    @DisplayName("El contrato OpenAPI se publica y no expone las rutas de desarrollo")
    void contratoDisponible() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/contexto']").exists())
                // El emisor de tokens local esta marcado @Hidden. Que aparezca
                // en el contrato lo convertiria en parte de la API del producto y
                // acabaria en el cliente Angular generado.
                .andExpect(jsonPath("$.paths['/desarrollo/token']").doesNotExist());
    }
}
