package com.ondexia.domain.ventas;

import java.util.Objects;
import java.util.UUID;

/** Datos públicos fijados al emitir; nunca contiene credenciales ni permisos. */
public record DatosHistoricosDocumento(EmisorHistorico emisor, LocalHistorico local,
        ClienteHistorico cliente) {
    public DatosHistoricosDocumento {
        Objects.requireNonNull(emisor, "emisor");
        Objects.requireNonNull(local, "local");
    }

    public record EmisorHistorico(String ruc, String razonSocial, String nombreComercial,
            String domicilioFiscal, String ubigeo) { }

    public record LocalHistorico(UUID id, String nombre, String direccion, String ubigeo,
            String codigo) { }

    public record ClienteHistorico(UUID id, String tipoDocumento, String numeroDocumento,
            String nombre, String direccion) { }
}
