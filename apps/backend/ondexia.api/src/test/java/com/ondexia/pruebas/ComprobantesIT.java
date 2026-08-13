package com.ondexia.pruebas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.infrastructure.seguridad.ContextoDePrueba;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Qué tipos de comprobante emite la empresa. Parte «parcial» del plan 07 §1.1.
 *
 * <p>Se toca solo la guía de remisión (09) y la nota de débito (08), que ninguna
 * otra clase de prueba usa. La suite comparte un contenedor y no deshace nada
 * entre clases: apagar un tipo que {@code SeriesIT} necesita la haría fallar
 * según el orden de ejecución, que es la peor clase de prueba frágil — la que
 * pasa hasta el día que no.
 */
class ComprobantesIT extends PruebaIntegracion {

    private static final String COMPROBANTES = "/api/v1/configuracion/comprobantes";
    private static final String SERIES = "/api/v1/configuracion/series";
    private static final UUID SUCURSAL_MATRIZ =
            UUID.fromString("00000000-0000-4000-8000-000000000020");

    @AfterEach
    void limpiar() {
        ContextoDePrueba.limpiar();
    }

    @Test
    @DisplayName("Sin ninguna decisión guardada, la empresa emite los cinco tipos")
    void porDefectoEmiteTodos() throws Exception {
        /*
         * La asimetría que explica la V5: la tabla guarda decisiones, y la
         * AUSENCIA de fila significa habilitado. Sin esto, RegistrarCuenta
         * tendría que sembrar cinco filas y no puede — corre sin contexto de
         * empresa, así que RLS las rechazaría, igual que rechaza la bitácora.
         */
        mockMvc.perform(get(COMPROBANTES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.codigo == '08')].emite").value(true));
    }

    @Test
    @DisplayName("Apagar un tipo impide crear series de ese tipo")
    void unTipoApagadoNoAdmiteSeries() throws Exception {
        // Guía de remisión, que ninguna otra prueba usa.
        mockMvc.perform(put(COMPROBANTES + "/09")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emite\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emite").value(false));

        // Lo que hace que la pantalla signifique algo. Sin esta comprobación
        // sería una declaración que el sistema ignora, y el cliente se enteraría
        // al ver series de un tipo que había apagado.
        mockMvc.perform(post(SERIES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sucursalId":"%s","tipoDocumento":"09","serie":"T001"}"""
                                .formatted(SUCURSAL_MATRIZ)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("tipo_no_habilitado"));

        // Y al volver a encenderlo, la serie entra.
        mockMvc.perform(put(COMPROBANTES + "/09")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emite\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(SERIES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sucursalId":"%s","tipoDocumento":"09","serie":"T001"}"""
                                .formatted(SUCURSAL_MATRIZ)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Un tipo con series activas no se puede apagar")
    void noSeApagaConSeriesVivas() throws Exception {
        // Nota de débito, que tampoco usa nadie más.
        mockMvc.perform(post(SERIES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sucursalId":"%s","tipoDocumento":"08","serie":"FD01"}"""
                                .formatted(SUCURSAL_MATRIZ)))
                .andExpect(status().isCreated());

        // Apagarlo dejando las series emitiendo haría que la pantalla dijera una
        // cosa y el sistema hiciera otra, que es peor que no tener la pantalla.
        mockMvc.perform(put(COMPROBANTES + "/08")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emite\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("tipo_con_series_activas"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("Desactívalas primero")));
    }

    @Test
    @DisplayName("Un tipo fuera del catálogo 01 se rechaza")
    void tipoInexistente() throws Exception {
        mockMvc.perform(put(COMPROBANTES + "/99")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emite\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("tipo_documento_invalido"));
    }
}
