package com.ondexia.domain.ventas;

import com.ondexia.domain.comprobante.TipoDocumento;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentoVentaRepositorio {

    Optional<DocumentoVenta> buscarPorId(UUID id);

    /** Los últimos, del más reciente al más antiguo; con tipo, solo de ese tipo. */
    List<DocumentoVenta> listarRecientes(TipoDocumento tipo, int maximo);

    /** Inserta. Un documento no se actualiza después de emitido salvo su estado. */
    DocumentoVenta guardar(DocumentoVenta documento);
}
