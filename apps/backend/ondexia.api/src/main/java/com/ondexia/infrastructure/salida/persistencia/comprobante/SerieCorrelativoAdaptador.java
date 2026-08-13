package com.ondexia.infrastructure.salida.persistencia.comprobante;

import com.ondexia.domain.comprobante.SerieCorrelativo;
import com.ondexia.domain.comprobante.SerieCorrelativoRepositorio;
import com.ondexia.domain.comprobante.TipoDocumento;
import com.ondexia.domain.comun.ProveedorDeContexto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de series.
 *
 * <p>Cada método lleva {@code @Transactional} por lo mismo que en almacenes: sin
 * transacción no hay variable de sesión, y la política de RLS —que falla
 * cerrado— devuelve cero filas sin lanzar nada.
 *
 * <p>Aquí ese descuido sería peor que un listado vacío. {@link #bloquearParaEmitir}
 * fuera de transacción no bloquearía nada: PostgreSQL libera el {@code FOR
 * UPDATE} al terminar la sentencia, así que el bloqueo existiría durante
 * microsegundos y las emisiones simultáneas volverían a pisarse. Un bloqueo que
 * no bloquea es indistinguible de uno que funciona hasta el día que hay carga.
 */
@Repository
@Transactional(readOnly = true)
public class SerieCorrelativoAdaptador implements SerieCorrelativoRepositorio {

    private final SerieCorrelativoJpaRepository filas;
    private final ProveedorDeContexto contexto;

    public SerieCorrelativoAdaptador(SerieCorrelativoJpaRepository filas,
            ProveedorDeContexto contexto) {
        this.filas = filas;
        this.contexto = contexto;
    }

    @Override
    public Optional<SerieCorrelativo> buscarPorId(UUID id) {
        return filas.findById(id).map(SerieCorrelativoAdaptador::aDominio);
    }

    @Override
    public Optional<SerieCorrelativo> buscarPorSerie(TipoDocumento tipoDocumento, String serie) {
        return filas.findByTipoDocumentoAndSerie(tipoDocumento.codigo(), serie)
                .map(SerieCorrelativoAdaptador::aDominio);
    }

    @Override
    public List<SerieCorrelativo> listar() {
        return filas.findAllByOrderByTipoDocumentoAscSerieAsc().stream()
                .map(SerieCorrelativoAdaptador::aDominio)
                .toList();
    }

    /**
     * {@code @Transactional} sin {@code readOnly}: la transacción va a escribir
     * el número nuevo, y una marcada de solo lectura haría que Hibernate
     * descartara el cambio en silencio.
     */
    @Override
    @Transactional
    public Optional<SerieCorrelativo> bloquearParaEmitir(UUID id) {
        return filas.bloquearPorId(id).map(SerieCorrelativoAdaptador::aDominio);
    }

    @Override
    @Transactional
    public SerieCorrelativo guardar(SerieCorrelativo serie) {
        var fila = filas.findById(serie.id()).orElse(null);

        if (fila == null) {
            fila = new SerieCorrelativoJpa(
                    serie.id(),
                    // La empresa sale del contexto, nunca del agregado: es la
                    // misma contra la que la política va a comprobar.
                    contexto.obligatorio().empresaActivaObligatoria(),
                    serie.sucursalId(),
                    serie.tipoDocumento().codigo(),
                    serie.serie(),
                    serie.ultimoNumero(),
                    serie.estaActiva());
        } else {
            fila.actualizarDesde(serie.ultimoNumero(), serie.estaActiva());
        }

        return aDominio(filas.save(fila));
    }

    private static SerieCorrelativo aDominio(SerieCorrelativoJpa fila) {
        return new SerieCorrelativo(
                fila.getId(),
                fila.getEmpresaId(),
                fila.getSucursalId(),
                TipoDocumento.porCodigo(fila.getTipoDocumento()),
                fila.getSerie(),
                fila.getUltimoNumero(),
                fila.isActiva());
    }
}
