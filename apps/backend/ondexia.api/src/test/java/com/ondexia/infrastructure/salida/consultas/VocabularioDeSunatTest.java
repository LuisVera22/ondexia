package com.ondexia.infrastructure.salida.consultas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.consultas.CondicionDomicilio;
import com.ondexia.domain.consultas.ConsultaNoDisponible;
import com.ondexia.domain.consultas.EstadoContribuyente;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * La traducción de las cadenas del padrón.
 *
 * <h2>Por qué merece pruebas propias</h2>
 *
 * <p>Porque es donde una diferencia de puntuación se convierte en una decisión
 * fiscal. Cada proveedor escribe lo mismo distinto —con tilde y sin ella, con
 * «DE OFICIO» y con «PROV.»— y aquí es donde eso se reduce a un enumerado del
 * que depende si una empresa puede emitir o no.
 */
class VocabularioDeSunatTest {

    @Test
    void lo_normal() {
        assertThat(VocabularioDeSunat.estado("ACTIVO")).isEqualTo(EstadoContribuyente.ACTIVO);
        assertThat(VocabularioDeSunat.condicion("HABIDO")).isEqualTo(CondicionDomicilio.HABIDO);
    }

    /** La tilde no la pone el mismo proveedor dos veces igual. */
    @ParameterizedTest
    @ValueSource(strings = {
        "SUSPENSION TEMPORAL", "SUSPENSIÓN TEMPORAL", "suspensión temporal",
        "  SUSPENSION   TEMPORAL  "
    })
    void la_tilde_el_espacio_y_la_caja_no_cambian_el_significado(String crudo) {
        assertThat(VocabularioDeSunat.estado(crudo))
                .isEqualTo(EstadoContribuyente.SUSPENSION_TEMPORAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "BAJA PROVISIONAL DE OFICIO", "BAJA PROV. DE OFICIO",
        "BAJA PROVISIONAL - OFICIO", "BAJA PROV POR OFICIO"
    })
    void las_variantes_de_la_baja_de_oficio_caen_en_el_mismo_sitio(String crudo) {
        assertThat(VocabularioDeSunat.estado(crudo))
                .isEqualTo(EstadoContribuyente.BAJA_PROVISIONAL_OFICIO);
    }

    @Test
    void la_baja_provisional_a_secas_no_es_la_de_oficio() {
        assertThat(VocabularioDeSunat.estado("BAJA PROVISIONAL"))
                .isEqualTo(EstadoContribuyente.BAJA_PROVISIONAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NO HABIDO", "no habido", "NO HABIDO - OFICIO"})
    void el_no_habido_en_sus_formas(String crudo) {
        assertThat(VocabularioDeSunat.condicion(crudo))
                .isEqualTo(CondicionDomicilio.NO_HABIDO);
    }

    @Test
    void no_hallado_no_es_no_habido() {
        assertThat(VocabularioDeSunat.condicion("NO HALLADO"))
                .isEqualTo(CondicionDomicilio.NO_HALLADO);
    }

    /**
     * Lo importante de todo el archivo.
     *
     * <p>La tentación es que lo desconocido caiga en «no activo»: parece seguro
     * porque rechaza. No lo es — el día que un proveedor cambie «ACTIVO» por
     * «Activo (habido)» se rechazaría a todo el mundo y ningún registro diría por
     * qué. Fallando, el problema aparece con nombre en el primer intento.
     */
    @Test
    @DisplayName("lo que no se entiende falla; no se parece a nada")
    void lo_desconocido_no_se_adivina() {
        assertThatThrownBy(() -> VocabularioDeSunat.estado("ACTIVO CON OBSERVACIONES"))
                .isInstanceOfSatisfying(ConsultaNoDisponible.class, fallo -> {
                    assertThat(fallo.getCodigo()).isEqualTo("consulta_respuesta_ilegible");
                    assertThat(fallo.esReintentable())
                            .withFailMessage("volver a preguntar devolvería la misma cadena")
                            .isFalse();
                    assertThat(fallo.getMessage())
                            .withFailMessage("hay que poder ver qué cadena llegó")
                            .contains("ACTIVO CON OBSERVACIONES");
                });
    }

    @Test
    void un_campo_ausente_tambien_falla() {
        assertThatThrownBy(() -> VocabularioDeSunat.estado(null))
                .isInstanceOf(ConsultaNoDisponible.class);
        assertThatThrownBy(() -> VocabularioDeSunat.condicion(""))
                .isInstanceOf(ConsultaNoDisponible.class);
    }
}
