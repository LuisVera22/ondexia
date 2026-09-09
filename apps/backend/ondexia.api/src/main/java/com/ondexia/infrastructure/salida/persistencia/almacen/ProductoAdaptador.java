package com.ondexia.infrastructure.salida.persistencia.almacen;

import com.ondexia.domain.almacen.AfectacionIgv;
import com.ondexia.domain.almacen.Producto;
import com.ondexia.domain.almacen.ProductoRepositorio;
import com.ondexia.domain.almacen.UnidadDeMedida;
import com.ondexia.domain.comun.ProveedorDeContexto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ProductoAdaptador implements ProductoRepositorio {

    private final ProductoJpaRepository filas;
    private final ProveedorDeContexto contexto;

    public ProductoAdaptador(ProductoJpaRepository filas, ProveedorDeContexto contexto) {
        this.filas = filas;
        this.contexto = contexto;
    }

    @Override
    public Optional<Producto> buscarPorId(UUID id) {
        return filas.findById(id).map(ProductoAdaptador::aDominio);
    }

    @Override
    public Optional<Producto> buscarPorCodigo(String codigo) {
        return filas.findByCodigo(codigo).map(ProductoAdaptador::aDominio);
    }

    @Override
    public List<Producto> listar() {
        // Sin filtro por empresa: lo pone la política de RLS.
        return filas.findAllByOrderByCodigoAsc().stream().map(ProductoAdaptador::aDominio).toList();
    }

    @Override
    public List<Producto> buscar(String texto, int maximo) {
        return filas.buscar(texto, maximo).stream().map(ProductoAdaptador::aDominio).toList();
    }

    @Override
    @Transactional
    public Producto guardar(Producto producto) {
        var fila = filas.findById(producto.id()).orElseGet(() -> new ProductoJpa(producto.id(),
                contexto.obligatorio().empresaActivaObligatoria(), producto.codigo()));
        fila.actualizarDesde(producto.nombre(), producto.descripcion(), producto.unidad().codigo(),
                producto.afectacion().codigo(), producto.precioLista(), producto.controlaStock(),
                producto.estaActivo());
        return aDominio(filas.save(fila));
    }

    static Producto aDominio(ProductoJpa fila) {
        return new Producto(fila.getId(), fila.getEmpresaId(), fila.getCodigo(), fila.getNombre(),
                fila.getDescripcion(), UnidadDeMedida.porCodigo(fila.getUnidadMedida()),
                AfectacionIgv.porCodigo(fila.getAfectacionIgv()), fila.getPrecioLista(),
                fila.isControlaStock(), fila.isActivo());
    }
}
