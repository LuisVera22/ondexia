package com.ondexia.infrastructure.salida.persistencia.comprobante;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "comprobante_electronico")
public class ComprobanteElectronicoJpa extends EntidadJpaBase {

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Column(name = "documento_id", nullable = false, updatable = false)
    private UUID documentoId;

    @Column(name = "tipo_documento", nullable = false, updatable = false, length = 2)
    private String tipoDocumento;

    @Column(name = "serie", nullable = false, updatable = false, length = 4)
    private String serie;

    @Column(name = "numero", nullable = false, updatable = false)
    private long numero;

    @Column(name = "estado", nullable = false, length = 12)
    private String estado;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "encolado_en")
    private Instant encoladoEn;

    @Column(name = "respondido_en")
    private Instant respondidoEn;

    @Column(name = "codigo_sunat", length = 10)
    private String codigoSunat;

    @Column(name = "descripcion_sunat")
    private String descripcionSunat;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "observaciones", nullable = false)
    private List<String> observaciones;

    @Column(name = "clave_xml", length = 300)
    private String claveXml;

    @Column(name = "clave_cdr", length = 300)
    private String claveCdr;

    @Column(name = "resumen_firma", length = 100)
    private String resumenFirma;

    protected ComprobanteElectronicoJpa() {
    }

    public ComprobanteElectronicoJpa(UUID id, UUID empresaId, UUID documentoId, String tipoDocumento,
            String serie, long numero) {
        this.id = id;
        this.empresaId = empresaId;
        this.documentoId = documentoId;
        this.tipoDocumento = tipoDocumento;
        this.serie = serie;
        this.numero = numero;
        this.observaciones = List.of();
    }

    /** Todo lo que cambia con cada intento y cada respuesta, de una vez. */
    public final void actualizarDesde(String estado, int intentos, Instant encoladoEn,
            Instant respondidoEn, String codigoSunat, String descripcionSunat,
            List<String> observaciones, String claveXml, String claveCdr, String resumenFirma) {
        this.estado = estado;
        this.intentos = intentos;
        this.encoladoEn = encoladoEn;
        this.respondidoEn = respondidoEn;
        this.codigoSunat = codigoSunat;
        this.descripcionSunat = descripcionSunat;
        this.observaciones = observaciones == null ? List.of() : List.copyOf(observaciones);
        this.claveXml = claveXml;
        this.claveCdr = claveCdr;
        this.resumenFirma = resumenFirma;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public UUID getDocumentoId() {
        return documentoId;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public String getSerie() {
        return serie;
    }

    public long getNumero() {
        return numero;
    }

    public String getEstado() {
        return estado;
    }

    public int getIntentos() {
        return intentos;
    }

    public Instant getEncoladoEn() {
        return encoladoEn;
    }

    public Instant getRespondidoEn() {
        return respondidoEn;
    }

    public String getCodigoSunat() {
        return codigoSunat;
    }

    public String getDescripcionSunat() {
        return descripcionSunat;
    }

    public List<String> getObservaciones() {
        return observaciones;
    }

    public String getClaveXml() {
        return claveXml;
    }

    public String getClaveCdr() {
        return claveCdr;
    }

    public String getResumenFirma() {
        return resumenFirma;
    }
}
