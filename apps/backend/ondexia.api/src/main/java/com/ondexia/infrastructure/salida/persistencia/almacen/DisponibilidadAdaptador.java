package com.ondexia.infrastructure.salida.persistencia.almacen;

import com.ondexia.domain.almacen.DisponibilidadEnLocal;
import com.ondexia.domain.almacen.DisponibilidadRepositorio;
import com.ondexia.domain.comun.ProveedorDeContexto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class DisponibilidadAdaptador implements DisponibilidadRepositorio {

    private final DisponibilidadJpaRepository filas;
    private final ProveedorDeContexto contexto;

    public DisponibilidadAdaptador(DisponibilidadJpaRepository filas, ProveedorDeContexto contexto) {
        this.filas = filas;
        this.contexto = contexto;
    }

    @Override
    public Optional<DisponibilidadEnLocal> buscar(UUID productoId, UUID sucursalId) {
        return filas.findByProductoIdAndSucursalId(productoId, sucursalId)
                .map(DisponibilidadAdaptador::aDominio);
    }

    @Override
    public List<DisponibilidadEnLocal> listarDeProducto(UUID productoId) {
        return filas.findAllByProductoId(productoId).stream()
                .map(DisponibilidadAdaptador::aDominio).toList();
    }

    @Override
    public List<DisponibilidadEnLocal> listarDisponiblesEn(UUID sucursalId) {
        return filas.findAllBySucursalIdAndDisponibleTrue(sucursalId).stream()
                .map(DisponibilidadAdaptador::aDominio).toList();
    }

    @Override
    @Transactional
    public DisponibilidadEnLocal guardar(DisponibilidadEnLocal disponibilidad) {
        var fila = filas.findById(disponibilidad.id()).orElseGet(() -> new DisponibilidadJpa(
                disponibilidad.id(), contexto.obligatorio().empresaActivaObligatoria(),
                disponibilidad.productoId(), disponibilidad.sucursalId()));
        fila.fijar(disponibilidad.estaDisponible(), disponibilidad.precio());
        return aDominio(filas.save(fila));
    }

    private static DisponibilidadEnLocal aDominio(DisponibilidadJpa fila) {
        return new DisponibilidadEnLocal(fila.getId(), fila.getProductoId(), fila.getSucursalId(),
                fila.isDisponible(), fila.getPrecio());
    }
}
