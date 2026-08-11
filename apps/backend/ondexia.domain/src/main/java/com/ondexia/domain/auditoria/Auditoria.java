package com.ondexia.domain.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Registro de quien cambio que y cuando.
 *
 * <p><strong>Solo se inserta.</strong> No hay UPDATE ni DELETE, y no es una
 * convencion: la migracion lo impone con permisos revocados sobre la tabla. Una
 * bitacora que se puede editar no prueba nada, y en un sistema que emite
 * documentos con valor tributario esa prueba es justamente el motivo por el que
 * la tabla existe.
 *
 * <p>Por eso no hereda de {@code EntidadBase}: {@code actualizado_en} no tiene
 * sentido en una fila que nunca se actualiza, y tenerlo invitaria a intentarlo.
 */
@Entity
@Table(name = "auditoria")
public class Auditoria {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Empresa sobre la que ocurrio el cambio.
     *
     * <p>Admite nulo para las acciones que son anteriores a toda empresa —
     * crear la cuenta, dar de alta al primer administrador. Es el unico caso.
     */
    @Column(name = "empresa_id", updatable = false)
    private UUID empresaId;

    @Column(name = "usuario_id", updatable = false)
    private UUID usuarioId;

    /** Nombre de la entidad afectada, por ejemplo {@code documento_venta}. */
    @Column(name = "entidad", nullable = false, updatable = false, length = 80)
    private String entidad;

    @Column(name = "entidad_id", updatable = false)
    private UUID entidadId;

    @Column(name = "accion", nullable = false, updatable = false, length = 40)
    private String accion;

    /**
     * Estado anterior y posterior, en JSON.
     *
     * <p>Se guarda el documento completo y no un diff calculado: el diff depende
     * de la version del codigo que lo calculo, y dentro de cinco anos —el plazo
     * de conservacion fiscal— ese codigo no existe. El JSON crudo se sigue
     * leyendo.
     *
     * <p>{@code jsonb} y no {@code text} porque permite consultar dentro del
     * documento e indexarlo con GIN. Que la columna sea consultable es lo que
     * convierte la bitacora en una herramienta de soporte y no en un vertedero.
     *
     * <p>Quien escribe aqui es responsable de no incluir datos que no deben
     * quedar registrados — claves SOL, contenido de certificados. Ver DTE §8.4.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_antes", updatable = false)
    private String datosAntes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_despues", updatable = false)
    private String datosDespues;

    @Column(name = "ip", updatable = false, length = 45)
    private String ip;

    @CreationTimestamp(source = org.hibernate.annotations.SourceType.DB)
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected Auditoria() {
        // Requerido por JPA.
    }

    public Auditoria(UUID empresaId, UUID usuarioId, String entidad, UUID entidadId,
            String accion, String datosAntes, String datosDespues, String ip) {
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
