package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.application.ventas.Cajas;
import com.ondexia.application.ventas.SesionesDeCaja;
import com.ondexia.domain.comun.ContextoOperacion;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.infrastructure.seguridad.ContextoDePrueba;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Cajas y sesiones de caja (doc 12 §3.4), de extremo a extremo.
 *
 * <p>Lo que se fija aquí y no en el dominio: que una caja tenga a lo sumo una
 * sesión abierta lo garantiza un índice parcial; que una sesión cerrada no se
 * toque, un disparador; y que el cajero acotado a un establecimiento no vea ni
 * abra las cajas de otro, el alcance por sucursal de {@code usuario_empresa}.
 */
class CajasIT extends PruebaIntegracion {

    private static final String CAJAS = "/api/v1/ventas/cajas";

    /** Establecimientos de la V900: dos de la empresa administrada, uno de la otra. */
    private static final String MATRIZ = "00000000-0000-4000-8000-000000000020";
    private static final String MIRAFLORES = "00000000-0000-4000-8000-000000000021";
    private static final String LOCAL_DEL_VENDEDOR = "00000000-0000-4000-8000-000000000022";

    /** La caja de la matriz que trae la V900. */
    private static final String CAJA_MATRIZ = "00000000-0000-4000-8000-000000000050";

    @Autowired
    private Cajas cajas;

    @Autowired
    private SesionesDeCaja sesiones;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transaccion;

    @Autowired
    private ObjectMapper json;

    @AfterEach
    void limpiar() {
        ContextoDePrueba.limpiar();
    }

    private String crear(String codigo, String sucursalId) throws Exception {
        String creada = mockMvc.perform(post(CAJAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo": "%s", "nombre": "Caja de prueba", "sucursalId": "%s"}
                                """.formatted(codigo, sucursalId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(creada).path("id").asString();
    }

    private JsonNode abrir(String cajaId, String montoInicial) throws Exception {
        String cuerpo = mockMvc.perform(post(CAJAS + "/" + cajaId + "/sesiones")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montoInicial\": " + montoInicial + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ABIERTA"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(cuerpo);
    }

    @Test
    @DisplayName("Alta, cambio de nombre y desactivación de una caja")
    void cicloCompleto() throws Exception {
        String creada = mockMvc.perform(post(CAJAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo": "caja2", "nombre": "Caja 2", "sucursalId": "%s"}
                                """.formatted(MATRIZ)))
                .andExpect(status().isCreated())
                // En mayúsculas aunque se mande en minúsculas, como el almacén.
                .andExpect(jsonPath("$.codigo").value("CAJA2"))
                .andExpect(jsonPath("$.sucursalId").value(MATRIZ))
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.sesionAbierta").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String id = json.readTree(creada).path("id").asString();

        mockMvc.perform(put(CAJAS + "/" + id)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Caja mostrador\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Caja mostrador"))
                .andExpect(jsonPath("$.codigo").value("CAJA2"));

        mockMvc.perform(put(CAJAS + "/" + id + "/estado")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));

        // Sigue en el listado, desactivada: sus sesiones la referencian.
        mockMvc.perform(get(CAJAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'CAJA2')].activa").value(false));

        mockMvc.perform(put(CAJAS + "/" + id + "/estado")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true));
    }

    @Test
    @DisplayName("El código es único por establecimiento, no por empresa")
    void codigoUnicoPorEstablecimiento() throws Exception {
        crear("DUP", MATRIZ);

        mockMvc.perform(post(CAJAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo": "dup", "nombre": "Otra", "sucursalId": "%s"}
                                """.formatted(MATRIZ)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("codigo_duplicado"))
                .andExpect(jsonPath("$.campos.codigo").exists());

        // Dos locales pueden tener su CAJA1: aquí, su DUP.
        crear("DUP", MIRAFLORES);
    }

    @Test
    @DisplayName("Sin establecimiento, o con uno de otra empresa, no hay caja")
    void establecimientoObligatorioYPropio() throws Exception {
        mockMvc.perform(post(CAJAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\": \"SIN\", \"nombre\": \"Sin local\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.sucursalId").exists());

        mockMvc.perform(post(CAJAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo": "AJENA", "nombre": "Ajena", "sucursalId": "%s"}
                                """.formatted(LOCAL_DEL_VENDEDOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("establecimiento_invalido"));
    }

    @Test
    @DisplayName("Abrir, intentar abrir otra vez, cerrar con arqueo y ver la diferencia")
    void abrirYCerrarConArqueo() throws Exception {
        String caja = crear("ARQ", MATRIZ);

        JsonNode sesion = abrir(caja, "100");
        String sesionId = sesion.path("id").asString();
        assertThat(sesion.path("abiertaPor").asString()).isEqualTo(USUARIO_DEMO);
        assertThat(sesion.path("montoInicial").decimalValue()).isEqualByComparingTo("100");

        // El listado la trae con su sesión abierta: es lo que ve la barra superior.
        mockMvc.perform(get(CAJAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'ARQ')].sesionAbierta.id").value(sesionId));

        mockMvc.perform(get(CAJAS + "/" + caja + "/sesion-abierta")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sesionId));

        // Una caja tiene a lo sumo una sesión abierta.
        mockMvc.perform(post(CAJAS + "/" + caja + "/sesiones")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montoInicial\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("caja_ya_abierta"));

        // Y con la sesión abierta no se desactiva.
        mockMvc.perform(put(CAJAS + "/" + caja + "/estado")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\": false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("caja_con_sesion_abierta"));

        // Cierre: sin ventas todavía, lo calculado en efectivo es el monto
        // inicial. Se declaran 95.50 —faltan 4.50— y nada de tarjeta.
        String cerrada = mockMvc.perform(put(CAJAS + "/sesiones/" + sesionId + "/cierre")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"declarado\": {\"EFECTIVO\": 95.50}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADA"))
                .andExpect(jsonPath("$.cerradaPor").value(USUARIO_DEMO))
                .andExpect(jsonPath("$.cerradaEn").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode arqueo = json.readTree(cerrada);
        assertThat(arqueo.path("calculado").path("EFECTIVO").decimalValue())
                .isEqualByComparingTo("100");
        assertThat(arqueo.path("declarado").path("EFECTIVO").decimalValue())
                .isEqualByComparingTo("95.50");
        assertThat(arqueo.path("declarado").path("TARJETA").decimalValue())
                .isEqualByComparingTo("0");
        assertThat(arqueo.path("diferencia").path("EFECTIVO").decimalValue())
                .as("la diferencia se guarda, no se corrige")
                .isEqualByComparingTo("-4.50");

        // Ya no hay sesión abierta, y el historial la tiene de primera.
        mockMvc.perform(get(CAJAS + "/" + caja + "/sesion-abierta")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(CAJAS + "/" + caja + "/sesiones")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(sesionId))
                .andExpect(jsonPath("$[0].estado").value("CERRADA"));

        // Cerrar dos veces no es posible.
        mockMvc.perform(put(CAJAS + "/sesiones/" + sesionId + "/cierre")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"declarado\": {}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("sesion_ya_cerrada"));

        // Y se puede volver a abrir la caja: la siguiente sesión es otra.
        JsonNode segunda = abrir(caja, "50");
        assertThat(segunda.path("id").asString()).isNotEqualTo(sesionId);
    }

    @Test
    @DisplayName("Una sesión cerrada es inmutable por disparador, no solo por código")
    void unaSesionCerradaNoSeToca() throws Exception {
        String caja = crear("INM", MATRIZ);
        String sesionId = abrir(caja, "10").path("id").asString();
        mockMvc.perform(put(CAJAS + "/sesiones/" + sesionId + "/cierre")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"declarado\": {\"EFECTIVO\": 10}}"))
                .andExpect(status().isOk());

        // Directo a la base, bajo el contexto de la empresa (la tabla tiene RLS):
        // ni el arqueo se reescribe ni la sesión se borra.
        ContextoDePrueba.comoUsuarioDe(UUID.fromString(USUARIO_DEMO), CUENTA_DEMO,
                UUID.fromString(EMPRESA_ADMINISTRADA));
        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado ->
                jdbc.sql("update sesion_caja set declarado = '{\"EFECTIVO\": \"999\"}'::jsonb "
                                + "where id = ?::uuid")
                        .param(sesionId)
                        .update()))
                .hasMessageContaining("cerrada no se modifica");
        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado ->
                jdbc.sql("delete from sesion_caja where id = ?::uuid")
                        .param(sesionId)
                        .update()))
                .hasMessageContaining("cerrada no se modifica");
    }

    @Test
    @DisplayName("El monto inicial se valida en la puerta")
    void montoInicialInvalido() throws Exception {
        mockMvc.perform(post(CAJAS + "/" + CAJA_MATRIZ + "/sesiones")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montoInicial\": -5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.montoInicial").exists());

        mockMvc.perform(post(CAJAS + "/" + CAJA_MATRIZ + "/sesiones")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.montoInicial").exists());
    }

    @Test
    @DisplayName("Una caja inactiva no se abre")
    void cajaInactivaNoSeAbre() throws Exception {
        String caja = crear("OFF", MIRAFLORES);
        mockMvc.perform(put(CAJAS + "/" + caja + "/estado")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\": false}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(CAJAS + "/" + caja + "/sesiones")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montoInicial\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("caja_inactiva"));
    }

    /**
     * El alcance por establecimiento, que es lo que hace útil la caja por local:
     * quien opera acotado a Miraflores ni ve ni abre la caja de la matriz, y
     * tampoco puede crear una allí.
     */
    @Test
    @DisplayName("Quien está acotado a un establecimiento solo alcanza sus cajas")
    void alcancePorEstablecimiento() throws Exception {
        String cajaMiraflores = crear("MIR", MIRAFLORES);

        ContextoDePrueba.establecer(new ContextoOperacion(UUID.fromString(USUARIO_DEMO),
                SUB_DEMO, CUENTA_DEMO, 1L, UUID.fromString(EMPRESA_ADMINISTRADA),
                UUID.fromString(MIRAFLORES), null, true, false, "127.0.0.1"));

        assertThat(cajas.listar())
                .isNotEmpty()
                .allMatch(caja -> caja.sucursalId().equals(UUID.fromString(MIRAFLORES)))
                .anyMatch(caja -> caja.id().equals(UUID.fromString(cajaMiraflores)));

        assertThatThrownBy(() -> cajas.registrar("FUERA", "Fuera", UUID.fromString(MATRIZ)))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("tu establecimiento");

        // «No existe», no «prohibida»: decir prohibida confirmaría el identificador.
        assertThatThrownBy(() -> sesiones.abrir(UUID.fromString(CAJA_MATRIZ), BigDecimal.ZERO))
                .isInstanceOf(RecursoNoEncontrado.class);
    }

    @Test
    @DisplayName("El vendedor acotado ve la caja de su local y no puede crear otras")
    void elVendedorVeSuCajaYNoCreaOtras() throws Exception {
        // En la segunda empresa el usuario demo es Vendedor, acotado a un local
        // (V900): puede consultar, abrir y cerrar, pero no registrar.
        mockMvc.perform(get(CAJAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_COMO_VENDEDOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sucursalId").value(LOCAL_DEL_VENDEDOR));

        mockMvc.perform(post(CAJAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_COMO_VENDEDOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo": "NO", "nombre": "No", "sucursalId": "%s"}
                                """.formatted(LOCAL_DEL_VENDEDOR)))
                .andExpect(status().isForbidden());
    }
}
