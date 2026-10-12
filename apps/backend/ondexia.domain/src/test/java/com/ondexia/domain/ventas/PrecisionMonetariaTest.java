package com.ondexia.domain.ventas;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondexia.domain.almacen.AfectacionIgv;
import com.ondexia.domain.almacen.UnidadDeMedida;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Oráculos decimales independientes para F03, según doc 13 §4.3. */
class PrecisionMonetariaTest {

    @ParameterizedTest(name = "{0}: {1} × {2} − {3} = {4}")
    @CsvSource({
            "dos-bolsas, 2, 32.50, 0, 65.00",
            "decima, 3, 0.10, 0, 0.30",
            "mitad-centimo, 1, 10.075000, 0, 10.08",
            "cantidad-fraccionaria, 0.5, 20.150000, 0, 10.08",
            "descuento, 1, 20.075000, 10, 10.08",
            "precio-seis-decimales, 5000, 0.000001, 0, 0.01",
            "importe-grande, 1, 123456789012.344999, 0, 123456789012.34"
    })
    void conservaElTotalDecimal(String caso, String cantidad, String precio,
            String descuento, String esperado) {
        var linea = LineaDeVenta.calcular(1, UUID.randomUUID(), caso, "Ejemplo F03",
                UnidadDeMedida.NIU, AfectacionIgv.GRAVADO,
                new BigDecimal(cantidad), new BigDecimal(precio), new BigDecimal(descuento), false);

        assertThat(linea.total()).isEqualByComparingTo(esperado);
        assertThat(linea.precioUnitario()).isEqualByComparingTo(precio);
        assertThat(linea.total().scale()).isEqualTo(2);
        assertThat(linea.precioUnitario().scale()).isEqualTo(6);
    }
}
