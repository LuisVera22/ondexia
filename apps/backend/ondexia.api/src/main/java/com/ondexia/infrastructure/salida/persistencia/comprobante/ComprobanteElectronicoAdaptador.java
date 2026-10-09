package com.ondexia.infrastructure.salida.persistencia.comprobante;

import com.ondexia.domain.comprobante.ComprobanteElectronico;
import com.ondexia.domain.comprobante.ComprobanteElectronicoRepositorio;
import com.ondexia.domain.comprobante.EstadoSunat;
import com.ondexia.domain.comprobante.TipoDocumento;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ComprobanteElectronicoAdaptador implements ComprobanteElectronicoRepositorio {

    private final ComprobanteElectronicoJpaRepository filas;

    public ComprobanteElectronicoAdaptador(ComprobanteElectronicoJpaRepository filas) {
        this.filas = filas;
    }

    @Override
    public Optional<ComprobanteElectronico> buscarPorId(UUID id) {
        return filas.findById(id).map(ComprobanteElectronicoAdaptador::aDominio);
    }

    @Override
    public Optional<ComprobanteElectronico> buscarPorDocumento(UUID documentoId) {
        return filas.findByDocumentoId(documentoId).map(ComprobanteElectronicoAdaptador::aDominio);
    }

    @Override
    public Map<UUID, EstadoSunat> estadosDe(Collection<UUID> documentoIds) {
        var estados = new HashMap<UUID, EstadoSunat>();
        if (documentoIds.isEmpty()) {
            return estados;
        }
        for (var fila : filas.findAllByDocumentoIdIn(documentoIds)) {
            estados.put(fila.getDocumentoId(), EstadoSunat.valueOf(fila.getEstado()));
        }
        return estados;
    }

    @Override
    public List<ComprobanteElectronico> listarPorAtender() {
        var pendientes = Arrays.stream(EstadoSunat.values())
                .filter(estado -> estado != EstadoSunat.ACEPTADO && estado != EstadoSunat.ANULADO)
                .map(Enum::name)
                .toList();
        return filas.findAllByEstadoInOrderByCreadoEnDesc(pendientes).stream()
                .map(ComprobanteElectronicoAdaptador::aDominio)
                .toList();
    }

    @Override
    @Transactional
    public ComprobanteElectronico guardar(ComprobanteElectronico c) {
        var fila = filas.findById(c.id()).orElseGet(() -> new ComprobanteElectronicoJpa(
                c.id(), c.empresaId(), c.documentoId(), c.tipo().codigo(), c.serie(), c.numero()));
        fila.actualizarDesde(c.estado().name(), c.intentos(), c.encoladoEn(), c.respondidoEn(),
                c.codigoSunat(), c.descripcionSunat(), c.observaciones(), c.claveXml(),
                c.claveCdr(), c.resumenFirma());
        return aDominio(filas.save(fila));
    }

    private static ComprobanteElectronico aDominio(ComprobanteElectronicoJpa f) {
        return ComprobanteElectronico.reconstruir(f.getId(), f.getEmpresaId(), f.getDocumentoId(),
                TipoDocumento.porCodigo(f.getTipoDocumento()), f.getSerie(), f.getNumero(),
                EstadoSunat.valueOf(f.getEstado()), f.getIntentos(), f.getEncoladoEn(),
                f.getRespondidoEn(), f.getCodigoSunat(), f.getDescripcionSunat(),
                f.getObservaciones(), f.getClaveXml(), f.getClaveCdr(), f.getResumenFirma());
    }
}
