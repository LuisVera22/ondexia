package com.ondexia.domain.comprobante;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ComprobanteElectronicoRepositorio {

    Optional<ComprobanteElectronico> buscarPorId(UUID id);

    Optional<ComprobanteElectronico> buscarPorDocumento(UUID documentoId);

    /** El estado ante SUNAT de varios documentos de una vez, para los listados. */
    Map<UUID, EstadoSunat> estadosDe(Collection<UUID> documentoIds);

    /**
     * Los que no han terminado bien: en cola, rechazados o con error de envío.
     *
     * <p>Es lo que el panel enseña y lo único que exige una acción de alguien.
     * Un aceptado —con reparos o sin ellos— no aparece: ya está declarado.
     */
    List<ComprobanteElectronico> listarPorAtender();

    ComprobanteElectronico guardar(ComprobanteElectronico comprobante);
}
