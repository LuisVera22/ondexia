package com.ondexia.domain.almacen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lo que define al bien ante SUNAT y lo que la ficha no deja pasar (doc 12 §3.5). */
class ProductoTest {

    private static Producto nuevo(String codigo, String precio) {
        return new Producto(UUID.randomUUID(), UUID.randomUUID(), codigo, "Cemento Portland",
                null, UnidadDeMedida.BG, AfectacionIgv.GRAVADO, new BigDecimal(precio), true);
    }

    @Test
    @DisplayName("El código se guarda en mayúsculas y nace activo")
    void codigoNormalizado() {
        var producto = nuevo("cem-001", "32.5");

        assertThat(producto.codigo()).isEqualTo("CEM-001");
        assertThat(producto.estaActivo()).isTrue();
        assertThat(producto.afectacion().llevaIgv()).isTrue();
    }

    @Test
    @DisplayName("Un precio negativo o con más de seis decimales no entra")
    void precio() {
        assertThatThrownBy(() -> nuevo("A", "-1"))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("negativo");
        assertThatThrownBy(() -> nuevo("A", "1.1234567"))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("seis decimales");
        // Seis exactos sí: es lo que admite NUMERIC(18,6) y lo que SUNAT permite.
        assertThat(nuevo("A", "1.123456").precioLista()).isEqualByComparingTo("1.123456");
    }

    @Test
    @DisplayName("Los catálogos de SUNAT se resuelven por código y rechazan lo desconocido")
    void catalogos() {
        assertThat(UnidadDeMedida.porCodigo("niu")).isEqualTo(UnidadDeMedida.NIU);
        assertThat(AfectacionIgv.porCodigo("20")).isEqualTo(AfectacionIgv.EXONERADO);
        assertThat(AfectacionIgv.porCodigo("INAFECTO").llevaIgv()).isFalse();

        assertThatThrownBy(() -> UnidadDeMedida.porCodigo("XYZ"))
                .isInstanceOf(ReglaDeNegocioViolada.class);
        assertThatThrownBy(() -> AfectacionIgv.porCodigo("11"))
                .as("las gratuitas son de la operación, no del bien")
                .isInstanceOf(ReglaDeNegocioViolada.class);
    }

    @Test
    @DisplayName("La disponibilidad por local: sin precio propio rige el de lista")
    void disponibilidad() {
        var producto = nuevo("A", "10");
        var local = new DisponibilidadEnLocal(UUID.randomUUID(), producto.id(), UUID.randomUUID(),
                true, null);

        assertThat(local.precioEfectivo(producto.precioLista())).isEqualByComparingTo("10");
        local.fijar(true, new BigDecimal("9.5"));
        assertThat(local.precioEfectivo(producto.precioLista())).isEqualByComparingTo("9.5");
        assertThatThrownBy(() -> local.fijar(true, new BigDecimal("-1")))
                .isInstanceOf(ReglaDeNegocioViolada.class);
    }

    @Test
    @DisplayName("Un movimiento de existencias mueve algo")
    void movimientoSinCantidad() {
        assertThatThrownBy(() -> MovimientoStock.ajuste(UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.ZERO, null, null, null))
                .isInstanceOf(ReglaDeNegocioViolada.class);
    }
}
