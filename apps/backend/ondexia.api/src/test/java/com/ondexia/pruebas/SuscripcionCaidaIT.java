package com.ondexia.pruebas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Una cuenta con la suscripción caída entra, consulta y no escribe.
 *
 * <h2>Qué cambió y por qué</h2>
 *
 * <p>Antes {@code ResolverContexto} negaba el acceso entero a las cuentas
 * suspendidas o canceladas: 403 en cada petición, sin poder ni mirar. El doc 09
 * §5.1 lo corrige, y el motivo no es comercial sino legal — los comprobantes
 * tienen obligación de conservación de cinco años y quien responde por ellos ante
 * SUNAT es el cliente. Dejarle fuera de sus propios documentos por una factura
 * impaga le convierte un problema comercial en uno tributario.
 *
 * <p>Se corta lo que <strong>genera obligaciones nuevas</strong>. Nada más.
 */
class SuscripcionCaidaIT extends PruebaIntegracion {

    private static final String ALMACENES = "/api/v1/almacen/almacenes";
    private static final String CONTEXTO = "/api/v1/contexto";

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void reactivar() {
        estado("EN_PRUEBA");
    }

    /**
     * No hace falta vaciar ninguna caché.
     *
     * <p>Es consecuencia de dónde se aplica el recorte: la máscara de solo lectura
     * se calcula en cada llamada a partir del contexto, que se resuelve contra la
     * base en cada petición. Solo lo contratado se cachea, y eso no cambia aquí.
     * Si hubiera que vaciar algo, suspender una cuenta tardaría en surtir efecto.
     */
    private void estado(String estadoSuscripcion) {
        jdbc.update("update cuenta set estado_suscripcion = ? where id = cast(? as uuid)",
                estadoSuscripcion, "00000000-0000-4000-8000-000000000001");
    }

    @Test
    @DisplayName("suspendida: puede consultar")
    void suspendidaConsulta() throws Exception {
        estado("SUSPENDIDA");

        mockMvc.perform(get(ALMACENES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("suspendida: no puede crear")
    void suspendidaNoEscribe() throws Exception {
        estado("SUSPENDIDA");

        mockMvc.perform(post(ALMACENES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"ALM-Z","nombre":"Deposito Z","direccion":null}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("cancelada: entra igual, porque sus datos siguen siendo suyos")
    void canceladaTambienEntra() throws Exception {
        estado("CANCELADA");

        mockMvc.perform(get(ALMACENES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("el contexto dice el estado y si es solo lectura, para el anuncio")
    void elContextoLlevaLoQueNecesitaElAnuncio() throws Exception {
        estado("SUSPENDIDA");

        mockMvc.perform(get(CONTEXTO)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuenta.estadoSuscripcion").value("SUSPENDIDA"))
                .andExpect(jsonPath("$.cuenta.soloLectura").value(true));
    }

    @Test
    @DisplayName("activa: nada cambia")
    void activaEscribeComoSiempre() throws Exception {
        estado("ACTIVA");

        mockMvc.perform(get(CONTEXTO)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(jsonPath("$.cuenta.soloLectura").value(false));

        mockMvc.perform(post(ALMACENES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"ALM-Y","nombre":"Deposito Y","direccion":null}
                                """))
                .andExpect(status().isCreated());
    }
}
