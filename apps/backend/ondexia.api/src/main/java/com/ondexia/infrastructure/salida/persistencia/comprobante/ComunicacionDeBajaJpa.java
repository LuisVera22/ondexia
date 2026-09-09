package com.ondexia.infrastructure.salida.persistencia.comprobante;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "comunicacion_baja")
public class ComunicacionDeBajaJpa extends EntidadJpaBase {

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Column(name = "fecha_comprobantes", nullable = false, updatable = false)
    private LocalDate fechaComprobantes;

    @Column(name = "fecha_generacion", nullable = false, updatable = false)
    private LocalDate fechaGeneracion;

    @Column(name = "numero_del_dia", nullable = false, updatable = false)
    private int numeroDelDia;

    @Column(name = "estado", nullable = false, length = 12)
    private String estado;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "encolada_en")
    private Instant encoladaEn;

    @Column(name = "respondida_en")
    private Instant respondidaEn;

    @Column(name = "ticket", length = 50)
    private String ticket;

    @Column(name = "codigo_sunat", length = 10)
    private String codigoSunat;

    @Column(name = "descripcion_sunat")
    private String descripcionSunat;

    @Column(name = "clave_xml", length = 300)
    private String claveXml;

    @Column(name = "clave_cdr", length = 300)
    private String claveCdr;

    @Column(name = "solicitada_por", nullable = false, updatable = false)
    private UUID solicitadaPor;

    /**
     * Los renglones se cargan siempre con la comunicación: son pocos —las
     * facturas de un día que se dan de baja a la vez— y no hay ninguna vista
     * que muestre la cabecera sin ellos.
     */
    @OneToMany(mappedBy = "comunicacion", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("serie, numero")
    private List<ComunicacionDeBajaItemJpa> items = new ArrayList<>();

    protected ComunicacionDeBajaJpa() {
    }

    public ComunicacionDeBajaJpa(UUID id, UUID empresaId, LocalDate fechaComprobantes,
            LocalDate fechaGeneracion, int numeroDelDia, UUID solicitadaPor) {
        this.id = id;
        this.empresaId = empresaId;
        this.fechaComprobantes = fechaComprobantes;
        this.fechaGeneracion = fechaGeneracion;
        this.numeroDelDia = numeroDelDia;
        this.solicitadaPor = solicitadaPor;
    }

    public void agregar(ComunicacionDeBajaItemJpa item) {
        item.asignarA(this);
        items.add(item);
    }

    /** Todo lo que cambia con cada intento y cada respuesta, de una vez. */
    public final void actualizarDesde(String estado, int intentos, Instant encoladaEn,
            Instant respondidaEn, String ticket, String codigoSunat, String descripcionSunat,
            String claveXml, String claveCdr) {
        this.estado = estado;
        this.intentos = intentos;
        this.encoladaEn = encoladaEn;
        this.respondidaEn = respondidaEn;
        this.ticket = ticket;
        this.codigoSunat = codigoSunat;
        this.descripcionSunat = descripcionSunat;
        this.claveXml = claveXml;
        this.claveCdr = claveCdr;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public LocalDate getFechaComprobantes() {
        return fechaComprobantes;
    }

    public LocalDate getFechaGeneracion() {
        return fechaGeneracion;
    }

    public int getNumeroDelDia() {
        return numeroDelDia;
    }

    public String getEstado() {
        return estado;
    }

    public int getIntentos() {
        return intentos;
    }

    public Instant getEncoladaEn() {
        return encoladaEn;
    }

    public Instant getRespondidaEn() {
        return respondidaEn;
    }

    public String getTicket() {
        return ticket;
    }

    public String getCodigoSunat() {
        return codigoSunat;
    }

    public String getDescripcionSunat() {
        return descripcionSunat;
    }

    public String getClaveXml() {
        return claveXml;
    }

    public String getClaveCdr() {
        return claveCdr;
    }

    public UUID getSolicitadaPor() {
        return solicitadaPor;
    }

    public List<ComunicacionDeBajaItemJpa> getItems() {
        return items;
    }
}
