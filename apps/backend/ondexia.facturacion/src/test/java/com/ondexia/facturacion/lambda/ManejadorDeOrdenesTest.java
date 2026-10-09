package com.ondexia.facturacion.lambda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ManejadorDeOrdenesTest {

    @Test
    @DisplayName("Saca las claves del evento de S3 y deshace la codificación de URL")
    void clavesDelEvento() {
        Map<String, Object> evento = Map.of("Records", List.of(
                Map.of("s3", Map.of("object", Map.of(
                        "key", "pendientes/00000000-0000-4000-8000-000000000010/abc.json"))),
                Map.of("s3", Map.of("object", Map.of("key", "certificados/2010%3A0000009.pfx")))));
        assertThat(ManejadorDeOrdenes.clavesDe(evento)).containsExactly(
                "pendientes/00000000-0000-4000-8000-000000000010/abc.json",
                "certificados/2010:0000009.pfx");
        assertThat(ManejadorDeOrdenes.clavesDe(null)).isEmpty();
        assertThat(ManejadorDeOrdenes.clavesDe(Map.of())).isEmpty();
    }
}
