package com.ondexia.infrastructure.salida.persistencia.ventas;

import com.ondexia.domain.almacen.AfectacionIgv;
import com.ondexia.domain.almacen.UnidadDeMedida;
import com.ondexia.domain.comprobante.TipoDocumento;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.ventas.Cliente;
import com.ondexia.domain.ventas.ClienteRepositorio;
import com.ondexia.domain.ventas.DocumentoVenta;
import com.ondexia.domain.ventas.DocumentoVentaRepositorio;
import com.ondexia.domain.ventas.EstadoDocumento;
import com.ondexia.domain.ventas.FormaDePago;
import com.ondexia.domain.ventas.LineaDeVenta;
import com.ondexia.domain.ventas.Pago;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class DocumentoVentaAdaptador implements DocumentoVentaRepositorio {

    private final DocumentoVentaJpaRepository filas;
    private final ClienteRepositorio clientes;
    private final ProveedorDeContexto contexto;

    public DocumentoVentaAdaptador(DocumentoVentaJpaRepository filas, ClienteRepositorio clientes,
            ProveedorDeContexto contexto) {
        this.filas = filas;
        this.clientes = clientes;
        this.contexto = contexto;
    }

    @Override
    public Optional<DocumentoVenta> buscarPorId(UUID id) {
        return filas.findById(id).map(this::aDominio);
    }

    @Override
    public List<DocumentoVenta> listarRecientes(TipoDocumento tipo, int maximo) {
        var pagina = PageRequest.of(0, maximo);
        var lista = tipo == null
                ? filas.findAllByOrderByEmitidoEnDesc(pagina)
                : filas.findAllByTipoDocumentoOrderByEmitidoEnDesc(tipo.codigo(), pagina);
        return lista.stream().map(this::aDominio).toList();
    }

    @Override
    @Transactional
    public DocumentoVenta guardar(DocumentoVenta d) {
        UUID empresaId = contexto.obligatorio().empresaActivaObligatoria();
        var existente = filas.findById(d.id());
        if (existente.isPresent()) {
            // Lo único que cambia después de emitido. El disparador lo vigila.
            var fila = existente.get();
            fila.cambiarEstado(d.estado().name());
            return aDominio(filas.save(fila));
        }
        var fila = new DocumentoVentaJpa(d.id(), empresaId, d.sucursalId(), d.sesionCajaId(),
                d.tipo().codigo(), d.esFiscal(), d.serie(), d.numero(),
                d.cliente() == null ? null : d.cliente().id(), d.fechaEmision(), d.emitidoEn(),
                d.emitidoPor(), d.totalGravado(), d.totalExonerado(), d.totalInafecto(),
                d.totalDescuento(), d.totalIgv(), d.total(), d.observaciones(),
                d.documentoOrigenId(), d.estado().name());
        for (LineaDeVenta l : d.lineas()) {
            fila.agregarLinea(new DetalleVentaJpa(UUID.randomUUID(), empresaId, l.orden(),
                    l.productoId(), l.codigo(), l.descripcion(), l.unidad().codigo(), l.cantidad(),
                    l.precioUnitario(), l.valorUnitario(), l.descuento(), l.afectacion().codigo(),
                    l.valorVenta(), l.igv(), l.total(), l.descargaExistencias()));
        }
        for (Pago p : d.pagos()) {
            fila.agregarPago(new PagoJpa(UUID.randomUUID(), empresaId, p.forma().name(), p.monto(),
                    p.referencia()));
        }
        return aDominio(filas.save(fila));
    }

    private DocumentoVenta aDominio(DocumentoVentaJpa fila) {
        Cliente cliente = fila.getClienteId() == null ? null
                : clientes.buscarPorId(fila.getClienteId()).orElse(null);
        return DocumentoVenta.reconstruir(fila.getId(), fila.getEmpresaId(), fila.getSucursalId(),
                fila.getSesionCajaId(), TipoDocumento.porCodigo(fila.getTipoDocumento()),
                fila.getSerie(), fila.getNumero(), cliente, fila.getFechaEmision(),
                fila.getEmitidoEn(), fila.getEmitidoPor(),
                fila.getLineas().stream().map(DocumentoVentaAdaptador::aLinea).toList(),
                fila.getPagos().stream().map(p -> new Pago(FormaDePago.valueOf(p.getForma()),
                        p.getMonto(), p.getReferencia())).toList(),
                fila.getObservaciones(), fila.getDocumentoOrigenId(),
                EstadoDocumento.valueOf(fila.getEstado()));
    }

    private static LineaDeVenta aLinea(DetalleVentaJpa l) {
        return new LineaDeVenta(l.getOrden(), l.getProductoId(), l.getCodigo(), l.getDescripcion(),
                UnidadDeMedida.porCodigo(l.getUnidadMedida()), l.getCantidad(), l.getPrecioUnitario(),
                l.getValorUnitario(), l.getDescuento(), AfectacionIgv.porCodigo(l.getAfectacionIgv()),
                l.getValorVenta(), l.getIgv(), l.getTotal(), l.isDescargaExistencias());
    }
}
