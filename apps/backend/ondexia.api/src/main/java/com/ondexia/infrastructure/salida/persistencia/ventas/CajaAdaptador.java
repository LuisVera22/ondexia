package com.ondexia.infrastructure.salida.persistencia.ventas;

import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.ventas.Caja;
import com.ondexia.domain.ventas.CajaRepositorio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class CajaAdaptador implements CajaRepositorio {

    private final CajaJpaRepository filas;
    private final ProveedorDeContexto contexto;

    public CajaAdaptador(CajaJpaRepository filas, ProveedorDeContexto contexto) {
        this.filas = filas;
        this.contexto = contexto;
    }

    @Override
    public Optional<Caja> buscarPorId(UUID id) {
        return filas.findById(id).map(CajaAdaptador::aDominio);
    }

    @Override
    public Optional<Caja> buscarPorCodigo(UUID sucursalId, String codigo) {
        return filas.findBySucursalIdAndCodigo(sucursalId, codigo).map(CajaAdaptador::aDominio);
    }

    @Override
    public List<Caja> listar() {
        // Sin filtro por empresa: lo pone la política de RLS.
        return filas.findAllByOrderByCodigoAsc().stream().map(CajaAdaptador::aDominio).toList();
    }

    @Override
    @Transactional
    public Caja guardar(Caja caja) {
        var fila = filas.findById(caja.id()).orElse(null);
        if (fila == null) {
            fila = new CajaJpa(caja.id(),
                    // La empresa sale del contexto: es la misma contra la que la
                    // política va a comprobar la fila.
                    contexto.obligatorio().empresaActivaObligatoria(),
                    caja.sucursalId(), caja.codigo(), caja.nombre(), caja.estaActiva());
        } else {
            fila.actualizarDesde(caja.nombre(), caja.estaActiva());
        }
        return aDominio(filas.save(fila));
    }

    static Caja aDominio(CajaJpa fila) {
        return new Caja(fila.getId(), fila.getEmpresaId(), fila.getSucursalId(), fila.getCodigo(),
                fila.getNombre(), fila.isActivo());
    }
}
