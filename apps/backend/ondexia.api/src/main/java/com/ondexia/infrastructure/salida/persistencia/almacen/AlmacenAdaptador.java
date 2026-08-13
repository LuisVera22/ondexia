package com.ondexia.infrastructure.salida.persistencia.almacen;

import com.ondexia.domain.almacen.Almacen;
import com.ondexia.domain.almacen.AlmacenRepositorio;
import com.ondexia.domain.comun.ProveedorDeContexto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de almacenes.
 *
 * <h2>Por qué cada método lleva {@code @Transactional}</h2>
 *
 * <p>Porque sin transacción no hay aislamiento. La política de RLS compara
 * contra una variable de sesión que {@code GestorTransaccionesConAislamiento}
 * fija <strong>al abrir la transacción</strong>; una consulta fuera de
 * transacción se ejecuta sin esa variable y la política, que falla cerrado,
 * devuelve <em>cero filas</em>.
 *
 * <p>El modo de fallo es el peor posible: no lanza nada. Un listado vacío parece
 * «no hay almacenes» y no «olvidé la anotación». Ya costó una tarde en el
 * esqueleto — los métodos derivados de Spring Data no son transaccionales por
 * su cuenta, y eso no se ve leyendo la interfaz.
 */
@Repository
@Transactional(readOnly = true)
public class AlmacenAdaptador implements AlmacenRepositorio {

    private final AlmacenJpaRepository filas;
    private final ProveedorDeContexto contexto;

    public AlmacenAdaptador(AlmacenJpaRepository filas, ProveedorDeContexto contexto) {
        this.filas = filas;
        this.contexto = contexto;
    }

    @Override
    public Optional<Almacen> buscarPorId(UUID id) {
        return filas.findById(id).map(AlmacenAdaptador::aDominio);
    }

    @Override
    public Optional<Almacen> buscarPorCodigo(String codigo) {
        return filas.findByCodigo(codigo).map(AlmacenAdaptador::aDominio);
    }

    @Override
    public List<Almacen> listar() {
        // Sin filtro por empresa: lo pone la política de RLS. Añadirlo aquí
        // daría la falsa impresión de que el aislamiento depende del código.
        return filas.findAllByOrderByCodigoAsc().stream()
                .map(AlmacenAdaptador::aDominio)
                .toList();
    }

    @Override
    @Transactional
    public Almacen guardar(Almacen almacen) {
        var fila = filas.findById(almacen.id()).orElse(null);

        if (fila == null) {
            fila = new AlmacenJpa(
                    almacen.id(),
                    // La empresa sale del contexto, nunca del agregado: es la
                    // misma contra la que la política va a comprobar.
                    contexto.obligatorio().empresaActivaObligatoria(),
                    almacen.codigo(),
                    almacen.nombre(),
                    almacen.sucursalId(),
                    almacen.estaActivo());
        } else {
            fila.actualizarDesde(almacen.nombre(), almacen.sucursalId(), almacen.estaActivo());
        }

        return aDominio(filas.save(fila));
    }

    private static Almacen aDominio(AlmacenJpa fila) {
        return new Almacen(
                fila.getId(),
                fila.getEmpresaId(),
                fila.getCodigo(),
                fila.getNombre(),
                fila.getSucursalId(),
                fila.isActivo());
    }
}
