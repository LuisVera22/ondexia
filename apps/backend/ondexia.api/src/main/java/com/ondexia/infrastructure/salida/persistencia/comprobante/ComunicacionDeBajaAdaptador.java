package com.ondexia.infrastructure.salida.persistencia.comprobante;

import com.ondexia.domain.comprobante.ComunicacionDeBaja;
import com.ondexia.domain.comprobante.ComunicacionDeBajaRepositorio;
import com.ondexia.domain.comprobante.EstadoSunat;
import com.ondexia.domain.comprobante.TipoDocumento;
import com.ondexia.domain.comun.ProveedorDeContexto;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ComunicacionDeBajaAdaptador implements ComunicacionDeBajaRepositorio {

    private final ComunicacionDeBajaJpaRepository filas;
    private final ProveedorDeContexto contexto;

    public ComunicacionDeBajaAdaptador(ComunicacionDeBajaJpaRepository filas,
            ProveedorDeContexto contexto) {
        this.filas = filas;
        this.contexto = contexto;
    }

    @Override
    public Optional<ComunicacionDeBaja> buscarPorId(UUID id) {
        return filas.findById(id).map(ComunicacionDeBajaAdaptador::aDominio);
    }

    @Override
    public List<ComunicacionDeBaja> listarRecientes(int maximo) {
        return filas.findAllByOrderByCreadoEnDesc(PageRequest.of(0, maximo)).stream()
                .map(ComunicacionDeBajaAdaptador::aDominio).toList();
    }

    @Override
    public List<ComunicacionDeBaja> enCurso() {
        return filas.findAllByEstadoInOrderByCreadoEnAsc(
                        List.of(EstadoSunat.EN_COLA.name(), EstadoSunat.EN_PROCESO.name())).stream()
                .map(ComunicacionDeBajaAdaptador::aDominio).toList();
    }

    @Override
    public List<ComunicacionDeBaja> queIncluyen(UUID documentoId) {
        return filas.queIncluyen(documentoId).stream()
                .map(ComunicacionDeBajaAdaptador::aDominio).toList();
    }

    @Override
    public int siguienteNumeroDelDia(LocalDate fechaDeGeneracion) {
        return filas.ultimoNumeroDelDia(fechaDeGeneracion) + 1;
    }

    @Override
    @Transactional
    public ComunicacionDeBaja guardar(ComunicacionDeBaja comunicacion) {
        var fila = filas.findById(comunicacion.id()).orElse(null);
        if (fila == null) {
            UUID empresaId = contexto.obligatorio().empresaActivaObligatoria();
            fila = new ComunicacionDeBajaJpa(comunicacion.id(), empresaId,
                    comunicacion.fechaDeLosComprobantes(), comunicacion.fechaDeGeneracion(),
                    comunicacion.numeroDelDia(), comunicacion.solicitadaPor());
            for (var renglon : comunicacion.comprobantes()) {
                fila.agregar(new ComunicacionDeBajaItemJpa(UUID.randomUUID(), empresaId,
                        renglon.documentoId(), renglon.tipo().codigo(), renglon.serie(),
                        renglon.numero(), renglon.motivo()));
            }
        }
        fila.actualizarDesde(comunicacion.estado().name(), comunicacion.intentos(),
                comunicacion.encoladaEn(), comunicacion.respondidaEn(), comunicacion.ticket(),
                comunicacion.codigoSunat(), comunicacion.descripcionSunat(),
                comunicacion.claveXml(), comunicacion.claveCdr());
        return aDominio(filas.save(fila));
    }

    private static ComunicacionDeBaja aDominio(ComunicacionDeBajaJpa f) {
        var renglones = f.getItems().stream()
                .map(i -> new ComunicacionDeBaja.Renglon(i.getDocumentoId(),
                        TipoDocumento.porCodigo(i.getTipoDocumento()), i.getSerie(), i.getNumero(),
                        i.getMotivo()))
                .toList();
        return ComunicacionDeBaja.reconstruir(f.getId(), f.getEmpresaId(),
                f.getFechaComprobantes(), f.getFechaGeneracion(), f.getNumeroDelDia(), renglones,
                f.getSolicitadaPor(), EstadoSunat.valueOf(f.getEstado()), f.getIntentos(),
                f.getEncoladaEn(), f.getRespondidaEn(), f.getTicket(), f.getCodigoSunat(),
                f.getDescripcionSunat(), f.getClaveXml(), f.getClaveCdr());
    }
}
