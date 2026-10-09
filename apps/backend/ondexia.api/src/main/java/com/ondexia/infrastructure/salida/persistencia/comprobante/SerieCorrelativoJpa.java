package com.ondexia.infrastructure.salida.persistencia.comprobante;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Fila de {@code serie_correlativo}.
 *
 * <p>Sin {@code @Version}. El bloqueo optimista sería lo natural en casi
 * cualquier otra entidad, y aquí es justamente lo que no se quiere: ante
 * conflicto lanza y obliga a reintentar, y un reintento en medio de la emisión
 * de un comprobante deja al cajero con un error en la cara. El correlativo se
 * protege con bloqueo pesimista al leer —{@code SELECT … FOR UPDATE}—, que hace
 * esperar en vez de fallar.
 */
@Entity
@Table(name = "serie_correlativo")
public class SerieCorrelativoJpa extends EntidadJpaBase {

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Column(name = "sucursal_id", nullable = false, updatable = false)
    private UUID sucursalId;

    /** El código del catálogo 01, no el nombre del enumerado. */
    @Column(name = "tipo_documento", nullable = false, updatable = false, length = 2)
    private String tipoDocumento;

    @Column(name = "serie", nullable = false, updatable = false, length = 4)
    private String serie;

    @Column(name = "ultimo_numero", nullable = false)
    private long ultimoNumero;

    @Column(name = "activa", nullable = false)
    private boolean activa;

    protected SerieCorrelativoJpa() {
    }

    public SerieCorrelativoJpa(UUID id, UUID empresaId, UUID sucursalId, String tipoDocumento,
            String serie, long ultimoNumero, boolean activa) {
        this.id = id;
        this.empresaId = empresaId;
        this.sucursalId = sucursalId;
        this.tipoDocumento = tipoDocumento;
        this.serie = serie;
        actualizarDesde(ultimoNumero, activa);
    }

    /**
     * Solo el número y el estado son mutables. El establecimiento, el tipo y la
     * serie identifican a los comprobantes ya emitidos: cambiarlos reescribiría
     * documentos con valor tributario.
     */
    public final void actualizarDesde(long ultimoNumero, boolean activa) {
        this.ultimoNumero = ultimoNumero;
        this.activa = activa;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public UUID getSucursalId() {
        return sucursalId;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public String getSerie() {
        return serie;
    }

    public long getUltimoNumero() {
        return ultimoNumero;
    }

    public boolean isActiva() {
        return activa;
    }
}
