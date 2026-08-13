package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.auditoria.Anotacion;
import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.infrastructure.seguridad.ContextoDePrueba;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Row Level Security: la segunda barrera del aislamiento multiempresa.
 *
 * <p>La primera es que cada consulta filtre por {@code empresa_id}. El problema
 * de esa barrera es que depende de que el desarrollador se acuerde, y el olvido
 * no falla: devuelve datos de otro cliente, con forma correcta, en la respuesta
 * equivocada. Estas pruebas comprueban que la segunda existe de verdad.
 */
class AislamientoEmpresaIT extends PruebaIntegracion {

    private static final UUID CUENTA = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID USUARIO = UUID.fromString(USUARIO_DEMO);
    private static final UUID EMPRESA_A = UUID.fromString(EMPRESA_ADMINISTRADA);
    private static final UUID EMPRESA_B = UUID.fromString(EMPRESA_COMO_VENDEDOR);

    @Autowired
    private RegistroDeAuditoria auditoria;

    @Autowired
    private TransactionTemplate transacciones;

    @AfterEach
    void limpiar() {
        ContextoDePrueba.limpiar();
    }

    private UUID anotarComo(UUID empresa) {
        ContextoDePrueba.comoUsuarioDe(USUARIO, CUENTA, empresa);
        UUID entidadId = UUID.randomUUID();
        transacciones.executeWithoutResult(estado ->
                auditoria.registrarCreacion("prueba", entidadId, new Dato("valor")));
        return entidadId;
    }

    private record Dato(String campo) {
    }

    @Test
    @DisplayName("Una fila escrita por la empresa A no la ve la empresa B")
    void filaDeOtraEmpresaNoSeVe() {
        UUID entidadId = anotarComo(EMPRESA_A);

        // Misma consulta, mismo código, otro inquilino. Nadie escribió ningún
        // WHERE empresa_id: lo pone la política.
        ContextoDePrueba.comoUsuarioDe(USUARIO, CUENTA, EMPRESA_B);
        assertThat(auditoria.historialDe(EMPRESA_A, "prueba", entidadId))
                .as("la empresa B no debe alcanzar una fila de la empresa A")
                .isEmpty();

        ContextoDePrueba.comoUsuarioDe(USUARIO, CUENTA, EMPRESA_A);
        assertThat(auditoria.historialDe(EMPRESA_A, "prueba", entidadId))
                .as("su propia empresa si debe verla")
                .hasSize(1);
    }

    @Test
    @DisplayName("Sin contexto no se ve nada: falla cerrado")
    void sinContextoNoSeVeNada() {
        UUID entidadId = anotarComo(EMPRESA_A);

        // El caso que de verdad importa. Un fallo de configuración —un endpoint
        // fuera del interceptor, una tarea programada que no fija el contexto—
        // deja al sistema SIN datos, que es ruidoso y se arregla enseguida.
        // Nunca con datos de más, que no se nota.
        ContextoDePrueba.limpiar();
        assertThat(auditoria.historialDe(EMPRESA_A, "prueba", entidadId))
                .as("sin empresa en el contexto no debe pasar ninguna fila")
                .isEmpty();
    }

    // La versión anterior probaba que el WITH CHECK de la política impide
    // ESCRIBIR en el ámbito de otra empresa. Esa prueba ya no se puede escribir
    // a través del puerto, y es una buena noticia: el adaptador toma el
    // empresa_id del contexto, así que **no hay forma de pedir que se escriba a
    // nombre de otra**. Lo que antes se comprobaba en tiempo de ejecución ahora
    // lo impide la firma del método.
    //
    // El WITH CHECK sigue en la migración V1 como red de seguridad para
    // cualquier escritura que no pase por aquí.

    @Test
    @DisplayName("La bitácora exige transacción abierta")
    void laBitacoraExigeTransaccion() {
        ContextoDePrueba.comoUsuarioDe(USUARIO, CUENTA, EMPRESA_A);

        // Propagation.MANDATORY. Escribir en su propia transacción dejaría la
        // anotación viva aunque el cambio se deshiciera.
        assertThatThrownBy(() ->
                auditoria.registrarCreacion("prueba", UUID.randomUUID(), new Dato("x")))
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
    }
}
