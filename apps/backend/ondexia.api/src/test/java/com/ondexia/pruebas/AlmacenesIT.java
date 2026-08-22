package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.application.almacen.Almacenes;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.infrastructure.seguridad.ContextoDePrueba;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Entrega 2: almacenes.
 *
 * <p>La entidad es diminuta a propósito. Lo que se prueba aquí no es el CRUD —es
 * el mismo de siempre— sino que <strong>Row Level Security aísla una tabla de
 * negocio real</strong>. Si algo falla en esta clase, el fallo casi no puede
 * estar en otro sitio: no hay lógica donde esconderse.
 */
class AlmacenesIT extends PruebaIntegracion {

    private static final String ALMACENES = "/api/v1/almacen/almacenes";
    private static final UUID CUENTA = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    private Almacenes almacenes;

    @AfterEach
    void limpiar() {
        ContextoDePrueba.limpiar();
    }

    private void comoUsuarioEn(String empresa) {
        ContextoDePrueba.comoUsuarioDe(
                UUID.fromString(USUARIO_DEMO), CUENTA, UUID.fromString(empresa));
    }

    @Test
    @DisplayName("Alta, edición y desactivación de un almacén")
    void cicloCompleto() throws Exception {
        String creado = mockMvc.perform(post(ALMACENES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"princ\",\"nombre\":\"Almacén principal\"}"))
                .andExpect(status().isCreated())
                // Se guarda en mayúsculas aunque se mande en minúsculas: se
                // teclea a diario y no debe depender de cómo lo escribió quien
                // lo creó.
                .andExpect(jsonPath("$.codigo").value("PRINC"))
                .andExpect(jsonPath("$.activo").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        String id = creado.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(put(ALMACENES + "/" + id)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Almacén central\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Almacén central"))
                .andExpect(jsonPath("$.codigo").value("PRINC"));

        mockMvc.perform(put(ALMACENES + "/" + id + "/estado")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(get(ALMACENES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'PRINC')].activo").value(false));

        /*
         * Y vuelve. Antes era un DELETE sin vuelta atras, con el agravante de que
         * el dialogo prometia que el almacen «no se borra» al desactivarlo: era
         * cierto, y aun asi no habia ninguna forma de volver a usarlo.
         */
        mockMvc.perform(put(ALMACENES + "/" + id + "/estado")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.codigo").value("PRINC"))
                .andExpect(jsonPath("$.nombre").value("Almacén central"));
    }

    @Test
    @DisplayName("La política de RLS filtra sin que el código haga nada")
    void elAislamientoLoPoneLaBase() {
        /*
         * La prueba que justifica esta entrega.
         *
         * `AlmacenRepositorio.listar()` no recibe la empresa, y el adaptador no
         * escribe ningún WHERE sobre empresa_id. Si aquí se vieran filas de la
         * otra empresa, sabríamos que activar_aislamiento_empresa() no está
         * haciendo su trabajo — y lo sabríamos ahora, con una tabla de cinco
         * columnas, en vez de dentro de tres entregas con los correlativos de
         * por medio.
         */
        comoUsuarioEn(EMPRESA_ADMINISTRADA);
        almacenes.registrar("SOLO-A", "Solo de la empresa A", null);

        comoUsuarioEn(EMPRESA_COMO_VENDEDOR);
        assertThat(almacenes.listar())
                .as("la empresa B no debe ver un almacén de la empresa A")
                .noneMatch(almacen -> "SOLO-A".equals(almacen.codigo()));

        comoUsuarioEn(EMPRESA_ADMINISTRADA);
        assertThat(almacenes.listar())
                .as("su propia empresa sí debe verlo")
                .anyMatch(almacen -> "SOLO-A".equals(almacen.codigo()));
    }

    @Test
    @DisplayName("El mismo código puede repetirse en empresas distintas")
    void elCodigoEsUnicoPorEmpresa() {
        // La restricción es UNIQUE (empresa_id, codigo), no sobre el código
        // solo: dos clientes distintos pueden llamar «CENTRAL» a su almacén.
        comoUsuarioEn(EMPRESA_ADMINISTRADA);
        almacenes.registrar("CENTRAL", "Central de A", null);

        comoUsuarioEn(EMPRESA_COMO_VENDEDOR);
        var deB = almacenes.registrar("CENTRAL", "Central de B", null);

        assertThat(deB.codigo()).isEqualTo("CENTRAL");
        assertThat(deB.empresaId()).isEqualTo(UUID.fromString(EMPRESA_COMO_VENDEDOR));
    }

    @Test
    @DisplayName("Un código repetido en la misma empresa es conflicto")
    void codigoRepetido() throws Exception {
        mockMvc.perform(post(ALMACENES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"DEP\",\"nombre\":\"Depósito\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ALMACENES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"dep\",\"nombre\":\"Otro depósito\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("codigo_duplicado"));
    }

    @Test
    @DisplayName("No se puede colgar un almacén de un establecimiento de otra empresa")
    void noSePuedeUsarUnEstablecimientoAjeno() {
        /*
         * Va por el caso de uso y no por HTTP, por la misma razón que en la
         * Entrega 1: el rol Vendedor no tiene `almacen.almacen:registrar`, así
         * que por HTTP la autorización responde 403 y esta regla nunca llega a
         * ejecutarse. Un verde así no probaría nada.
         *
         * La regla importa porque `sucursal` queda FUERA de las políticas de
         * RLS —hay que leerla para saber cuál es la empresa—, de modo que aquí
         * el filtro sí es responsabilidad del código. Es exactamente el hueco
         * que el aislamiento cierra solo en las tablas que protege.
         */
        comoUsuarioEn(EMPRESA_COMO_VENDEDOR);

        // La sucursal 0000 pertenece a EMPRESA_ADMINISTRADA.
        UUID ajena = UUID.fromString("00000000-0000-4000-8000-000000000020");

        assertThatThrownBy(() -> almacenes.registrar("ROBADO", "Con establecimiento ajeno", ajena))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("no existe en esta empresa");
    }
}
