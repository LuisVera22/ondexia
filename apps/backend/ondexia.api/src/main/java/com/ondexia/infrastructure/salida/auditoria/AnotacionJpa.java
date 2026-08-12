package com.ondexia.infrastructure.salida.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SourceType;
import org.hibernate.type.SqlTypes;

/**
 * Fila de {@code auditoria}.
 *
 * <p>No hereda de {@code EntidadJpaBase} porque {@code actualizado_en} no tiene
 * sentido en una fila que nunca se actualiza — y tenerlo invitaría a intentarlo.
 * La migración V1 instala un disparador que rechaza todo UPDATE y DELETE.
 */
@Entity
@Table(name = "auditoria")
public class AnotacionJpa {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "empresa_id", updatable = false)
    private UUID empresaId;

    @Column(name = "usuario_id", updatable = false)
    private UUID usuarioId;

    @Column(name = "entidad", nullable = false, updatable = false, length = 80)
    private String entidad;

    @Column(name = "entidad_id", updatable = false)
    private UUID entidadId;

    @Column(name = "accion", nullable = false, updatable = false, length = 40)
    private String accion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_antes", updatable = false)
    private String datosAntes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_despues", updatable = false)
    private String datosDespues;

    @Column(name = "ip", updatable = false, length = 45)
    private String ip;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected AnotacionJpa() {
    }

    public AnotacionJpa(UUID id, UUID empresaId, UUID usuarioId, String entidad, UUID entidadId,
            String accion, String datosAntes, String datosDespues, String ip) {
        this.id = id;
        this.empresaId = empresaId;
        this.usuarioId = usuarioId;
        this.entidad = entidad;
        this.entidadId = entidadId;
        this.accion = accion;
        this.datosAntes = datosAntes;
        this.datosDespues = datosDespues;
        this.ip = ip;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getEntidad() {
        return entidad;
    }

    public UUID getEntidadId() {
        return entidadId;
    }

    public String getAccion() {
        return accion;
    }

    public String getDatosAntes() {
        return datosAntes;
    }

    public String getDatosDespues() {
        return datosDespues;
    }

    public String getIp() {
        return ip;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
