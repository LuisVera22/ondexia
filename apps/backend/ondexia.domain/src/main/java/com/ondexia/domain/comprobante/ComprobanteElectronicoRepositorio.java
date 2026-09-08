package com.ondexia.domain.comprobante;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ComprobanteElectronicoRepositorio {

    Optional<ComprobanteElectronico> buscarPorId(UUID id);

    Optional<ComprobanteElectronico> buscarPorDocumento(UUID documentoId);

    /** El estado ante SUNAT de varios documentos de una vez, para los listados. */
    Map<UUID, EstadoSunat> estadosDe(Collection<UUID> documentoIds);

    ComprobanteElectronico guardar(ComprobanteElectronico comprobante);
}
