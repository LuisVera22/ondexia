package com.ondexia.domain.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La tabla de prefijos del RUC y lo que cada uno puede hacer (doc 12 §3.1 y
 * decisión 4 de §10.1). Los RUC de prueba tienen el dígito verificador correcto
 * para que {@link Ruc} los admita y la única variable sea el prefijo.
 */
class TipoDeContribuyenteTest {

    @Test
    @DisplayName("10 es persona natural: se registra y puede estar en el Nuevo RUS")
    void personaNatural() {
        var tipo = new Ruc("10123456781").tipoDeContribuyente();

        assertThat(tipo).isEqualTo(TipoDeContribuyente.PERSONA_NATURAL);
        assertThat(tipo.puedeRegistrarse()).isTrue();
        assertThat(tipo.puedeEstarEnNuevoRus()).isTrue();
    }

    @Test
    @DisplayName("20 es persona jurídica: se registra y no puede estar en el Nuevo RUS")
    void personaJuridica() {
        var tipo = new Ruc("20123456786").tipoDeContribuyente();

        assertThat(tipo).isEqualTo(TipoDeContribuyente.PERSONA_JURIDICA);
        assertThat(tipo.puedeRegistrarse()).isTrue();
        assertThat(tipo.puedeEstarEnNuevoRus()).isFalse();
    }

    @Test
    @DisplayName("15 y 17 se reconocen y no se registran")
    void otrosDocumentos() {
        for (String ruc : new String[] {"15123456782", "17123456785"}) {
            var tipo = new Ruc(ruc).tipoDeContribuyente();

            assertThat(tipo).as(ruc).isEqualTo(TipoDeContribuyente.OTRO_DOCUMENTO_DE_IDENTIDAD);
            assertThat(tipo.puedeRegistrarse()).as(ruc).isFalse();
        }
    }

    @Test
    @DisplayName("Un prefijo que SUNAT no emite se rechaza con su código")
    void prefijoDesconocido() {
        // 25 no existe (doc 12 §10.1, decisión 4); el verificador cuadra a propósito.
        var ruc = new Ruc("25123456788");

        assertThatThrownBy(ruc::tipoDeContribuyente)
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("empieza por 25");
    }
}
