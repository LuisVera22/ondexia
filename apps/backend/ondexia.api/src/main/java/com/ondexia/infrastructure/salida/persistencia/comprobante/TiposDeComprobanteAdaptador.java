package com.ondexia.infrastructure.salida.persistencia.comprobante;

import com.ondexia.domain.comprobante.TipoDocumento;
import com.ondexia.domain.comprobante.TiposDeComprobanteRepositorio;
import com.ondexia.domain.comun.ProveedorDeContexto;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de los tipos que emite la empresa.
 *
 * <p>Cada método lleva {@code @Transactional} por lo mismo que en almacenes: sin
 * transacción no hay variable de sesión, y la política de RLS —que falla
 * cerrado— devuelve cero filas sin lanzar nada. Aquí ese vacío se leería como
 * «no hay ningún tipo apagado», es decir, como que la empresa emite todo.
 */
@Repository
@Transactional(readOnly = true)
public class TiposDeComprobanteAdaptador implements TiposDeComprobanteRepositorio {

    private final TipoComprobanteEmpresaJpaRepository filas;
    private final ProveedorDeContexto contexto;

    public TiposDeComprobanteAdaptador(TipoComprobanteEmpresaJpaRepository filas,
            ProveedorDeContexto contexto) {
        this.filas = filas;
        this.contexto = contexto;
    }

    /**
     * Solo las filas apagadas, y sin filtrar por empresa: lo hace la política de
     * RLS. Todo lo que no aparezca aquí está habilitado.
     */
    @Override
    public Set<TipoDocumento> desactivados() {
        return filas.findByActivoFalse().stream()
                .map(fila -> TipoDocumento.porCodigo(fila.getTipoDocumento()))
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void fijar(TipoDocumento tipo, boolean activo) {
        var existente = filas.findByTipoDocumento(tipo.codigo()).orElse(null);

        if (existente == null) {
            // Se guarda también la decisión de «activo», aunque coincida con lo
            // predeterminado. Borrar la fila al reactivar dejaría el mismo
            // resultado y perdería el dato de que alguien lo decidió, que es lo
            // que hace legible la bitácora meses después.
            filas.save(new TipoComprobanteEmpresaJpa(
                    UUID.randomUUID(),
                    contexto.obligatorio().empresaActivaObligatoria(),
                    tipo.codigo(),
                    activo));
            return;
        }

        existente.cambiarEstado(activo);
        filas.save(existente);
    }
}
