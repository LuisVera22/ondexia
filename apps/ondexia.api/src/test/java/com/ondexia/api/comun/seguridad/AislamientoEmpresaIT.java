package com.ondexia.api.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.api.PruebaIntegracion;
import com.ondexia.domain.auditoria.Auditoria;
import com.ondexia.domain.auditoria.AuditoriaRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Row Level Security: la segunda barrera del aislamiento multiempresa.
 *
 * <p>La primera barrera es que cada consulta filtre por {@code empresa_id}. El
 * problema de esa barrera es que depende de que el desarrollador se acuerde, y
 * el olvido no falla: devuelve datos de otro cliente, con forma correcta, en la
 * respuesta equivocada. Estas pruebas comprueban que la segunda barrera existe
 * de verdad.
 *
 * <p>Viven en el mismo paquete que {@link ContextoActual} para poder fijar el
 * contexto directamente. Es la unica forma de probar el aislamiento sin pasar
 * por un endpoint — y en esta version todavia no hay ningun endpoint que
 * escriba en la bitacora.
 */
class AislamientoEmpresaIT extends PruebaIntegracion {

    private static final UUID EMPRESA_A = UUID.fromString(EMPRESA_ADMINISTRADA);
    private static final UUID EMPRESA_B = UUID.fromString(EMPRESA_COMO_VENDEDOR);

    @Autowired
    private AuditoriaRepository auditorias;

    @AfterEach
    void limpiarContexto() {
        ContextoActual.limpiar();
    }

    private void operarComo(UUID empresaId) {
        ContextoActual.establecer(new ContextoPeticion(
                UUID.fromString(USUARIO_DEMO),
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                1L,
                empresaId,
                null,
                null,
                true,
                "127.0.0.1"));
    }

    @Test
    @DisplayName("Una fila escrita por la empresa A no la ve la empresa B")
    void filaDeOtraEmpresaNoSeVe() {
        operarComo(EMPRESA_A);
        Auditoria registro = auditorias.save(new Auditoria(
                EMPRESA_A, UUID.fromString(USUARIO_DEMO), "prueba", UUID.randomUUID(),
                "registrar", null, "{\"campo\": \"valor\"}", "127.0.0.1"));

        // Misma consulta, mismo codigo, otro inquilino. Nadie escribio ningun
        // WHERE empresa_id: lo pone la politica.
        operarComo(EMPRESA_B);
        assertThat(auditorias.findById(registro.getId()))
                .as("la empresa B no debe alcanzar una fila de la empresa A")
                .isEmpty();

        operarComo(EMPRESA_A);
        assertThat(auditorias.findById(registro.getId()))
                .as("su propia empresa si debe verla")
                .isPresent();
    }

    @Test
    @DisplayName("Sin contexto no se ve nada: falla cerrado")
    void sinContextoNoSeVeNada() {
        operarComo(EMPRESA_A);
        Auditoria registro = auditorias.save(new Auditoria(
                EMPRESA_A, UUID.fromString(USUARIO_DEMO), "prueba", UUID.randomUUID(),
                "registrar", null, null, "127.0.0.1"));

        // Este es el caso que importa de verdad. Un fallo de configuracion —un
        // endpoint que quedo fuera del interceptor, una tarea programada que no
        // fija el contexto— deja al sistema SIN datos, que es ruidoso y se
        // arregla enseguida. Nunca con datos de mas, que no se nota.
        ContextoActual.limpiar();
        assertThat(auditorias.findById(registro.getId()))
                .as("sin empresa en el contexto, la politica no debe dejar pasar ninguna fila")
                .isEmpty();
    }

    @Test
    @DisplayName("No se puede escribir una fila a nombre de otra empresa")
    void noSePuedeEscribirParaOtraEmpresa() {
        operarComo(EMPRESA_A);

        // El WITH CHECK de la politica cubre la direccion contraria a la que
        // suele pensarse: no solo impide LEER lo ajeno, tambien impide ESCRIBIR
        // en el ambito ajeno. Sin el, un identificador equivocado en el codigo
        // sembraria filas dentro de los datos de otro cliente.
        assertThatThrownBy(() -> auditorias.save(new Auditoria(
                EMPRESA_B, UUID.fromString(USUARIO_DEMO), "prueba", UUID.randomUUID(),
                "registrar", null, null, "127.0.0.1")))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("La bitacora no se puede borrar")
    void laBitacoraEsDeSoloInsercion() {
        operarComo(EMPRESA_A);
        Auditoria registro = auditorias.save(new Auditoria(
                EMPRESA_A, UUID.fromString(USUARIO_DEMO), "prueba", UUID.randomUUID(),
                "registrar", null, null, "127.0.0.1"));

        // El repositorio hereda delete() de JpaRepository y no hay forma limpia
        // de quitarlo de la interfaz. La barrera esta en la base: un disparador
        // rechaza todo UPDATE y DELETE. Una bitacora editable no prueba nada.
        assertThatThrownBy(() -> auditorias.deleteById(registro.getId()))
                .isInstanceOf(Exception.class);
    }
}
