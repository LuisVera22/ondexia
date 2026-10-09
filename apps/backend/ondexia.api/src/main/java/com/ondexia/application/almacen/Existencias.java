package com.ondexia.application.almacen;

import com.ondexia.domain.almacen.Almacen;
import com.ondexia.domain.almacen.AlmacenRepositorio;
import com.ondexia.domain.almacen.Existencia;
import com.ondexia.domain.almacen.ExistenciasRepositorio;
import com.ondexia.domain.almacen.MovimientoStock;
import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cuánto hay y cómo cambió (doc 12 §3.5).
 *
 * <p>Solo el ajuste por conteo existe hoy: la venta descargará en la iteración
 * 4 por el mismo puerto. El ajuste no fija la cantidad: registra la diferencia
 * entre lo contado y lo que había, como un movimiento más del libro, y la
 * proyección se mueve con él. Así el libro sigue explicando la proyección.
 */
@Service
public class Existencias {

    private static final int MAXIMO_MOVIMIENTOS = 200;

    private final ExistenciasRepositorio existencias;
    private final AlmacenRepositorio almacenes;
    private final Productos productos;
    private final RegistroDeAuditoria auditoria;
    private final ProveedorDeContexto contexto;
    private final Clock reloj;

    public Existencias(ExistenciasRepositorio existencias, AlmacenRepositorio almacenes,
            Productos productos, RegistroDeAuditoria auditoria, ProveedorDeContexto contexto,
            Clock reloj) {
        this.existencias = existencias;
        this.almacenes = almacenes;
        this.productos = productos;
        this.auditoria = auditoria;
        this.contexto = contexto;
        this.reloj = reloj;
    }

    public List<Existencia> de(UUID productoId) {
        productos.exigir(productoId);
        return existencias.existenciasDe(productoId);
    }

    public List<MovimientoStock> movimientosDe(UUID productoId) {
        productos.exigir(productoId);
        return existencias.movimientosDe(productoId, MAXIMO_MOVIMIENTOS);
    }

    /**
     * Lo contado manda: la diferencia con lo que había es el movimiento.
     *
     * @return la existencia resultante, que es lo contado
     */
    @Transactional
    public Existencia ajustar(UUID productoId, UUID almacenId, BigDecimal contado, String motivo) {
        var producto = productos.exigir(productoId);
        if (!producto.controlaStock()) {
            throw new ReglaDeNegocioViolada(
                    "producto_sin_existencias",
                    "Este producto no controla existencias: no hay nada que ajustar.");
        }
        if (contado == null || contado.signum() < 0) {
            throw new ReglaDeNegocioViolada(
                    "cantidad_invalida", "Lo contado no puede ser negativo.", "cantidad");
        }
        Almacen almacen = almacenes.buscarPorId(almacenId)
                .filter(Almacen::estaActivo)
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "almacen_invalido", "El almacén no existe o está desactivado.", "almacenId"));

        BigDecimal habia = existencias.buscar(almacen.id(), productoId)
                .map(Existencia::cantidad)
                .orElse(BigDecimal.ZERO);
        BigDecimal diferencia = contado.subtract(habia);
        if (diferencia.signum() == 0) {
            // Contar lo mismo que había no es un movimiento: no se anota nada.
            return new Existencia(almacen.id(), productoId, habia);
        }

        var actual = contexto.obligatorio();
        var resultado = existencias.mover(MovimientoStock.ajuste(almacen.id(), productoId,
                diferencia, motivo, actual.usuarioId(), reloj.instant()));
        auditoria.registrar("stock", productoId, "AJUSTAR",
                new Instantanea(almacen.id(), habia),
                new Instantanea(almacen.id(), resultado.cantidad()));
        return resultado;
    }

    private record Instantanea(UUID almacenId, BigDecimal cantidad) {
    }
}
