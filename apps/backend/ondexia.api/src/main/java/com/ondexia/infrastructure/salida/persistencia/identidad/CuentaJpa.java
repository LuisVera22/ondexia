package com.ondexia.infrastructure.salida.persistencia.identidad;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import com.ondexia.domain.identidad.EstadoSuscripcion;
import com.ondexia.domain.identidad.PlanSuscripcion;
import java.util.UUID;

/** Fila de {@code cuenta}. El agregado es {@link com.ondexia.domain.identidad.Cuenta}. */
@Entity
@Table(name = "cuenta")
public class CuentaJpa extends EntidadJpaBase {

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    // STRING y nunca ORDINAL: con ORDINAL, insertar un valor en medio del
    // enumerado reescribe en silencio el significado de cada fila guardada.
    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 30)
    private PlanSuscripcion plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_suscripcion", nullable = false, length = 30)
    private EstadoSuscripcion estadoSuscripcion;

    @Column(name = "permisos_version", nullable = false)
    private long permisosVersion;

    protected CuentaJpa() {
    }

    public CuentaJpa(UUID id, String nombre, PlanSuscripcion plan, EstadoSuscripcion estado,
            long permisosVersion) {
        this.id = id;
        this.nombre = nombre;
        this.plan = plan;
        this.estadoSuscripcion = estado;
        this.permisosVersion = permisosVersion;
    }

    public String getNombre() {
        return nombre;
    }

    public PlanSuscripcion getPlan() {
        return plan;
    }

    public EstadoSuscripcion getEstadoSuscripcion() {
        return estadoSuscripcion;
    }

    public long getPermisosVersion() {
        return permisosVersion;
    }

    public void actualizarDesde(String nombre, PlanSuscripcion plan, EstadoSuscripcion estado) {
        this.nombre = nombre;
        this.plan = plan;
        this.estadoSuscripcion = estado;
    }
}
