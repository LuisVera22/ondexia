package com.ondexia.domain.identidad;

import com.ondexia.domain.comun.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

/**
 * El cliente que contrata Ondexia.
 *
 * <p><strong>La suscripcion cuelga de la cuenta, no de la empresa.</strong> Un
 * mismo contribuyente puede operar varios RUC —es lo normal en grupos
 * familiares y en cadenas—, y facturarle una suscripcion por cada uno seria
 * cobrarle varias veces por el mismo servicio. La cuenta es la unidad
 * comercial; la empresa es la unidad fiscal.
 */
@Entity
@Table(name = "cuenta")
public class Cuenta extends EntidadBase {

    @NotBlank
    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 30)
    private PlanSuscripcion plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_suscripcion", nullable = false, length = 30)
    private EstadoSuscripcion estadoSuscripcion;

    /**
     * Se incrementa al tocar cualquier rol o asignacion de permisos de esta
     * cuenta.
     *
     * <p>Existe para poder cachear los permisos en la memoria del contenedor de
     * Lambda sin servirlos rancios. Sin este numero hay dos opciones, ambas
     * malas: consultar los ~200 permisos en cada peticion, o cachearlos y que
     * un permiso revocado siga funcionando hasta que el contenedor muera —que
     * puede ser horas. Con la version en la clave del cache, revocar un permiso
     * invalida la entrada de inmediato y sin coordinacion entre contenedores.
     *
     * <p>Ver {@code EvaluadorPermisos} en {@code ondexia-api}.
     */
    @Column(name = "permisos_version", nullable = false)
    private long permisosVersion;

    protected Cuenta() {
        // Requerido por JPA.
    }

    public Cuenta(String nombre, PlanSuscripcion plan, EstadoSuscripcion estadoSuscripcion) {
        this.nombre = nombre;
        this.plan = plan;
        this.estadoSuscripcion = estadoSuscripcion;
        this.permisosVersion = 1L;
    }

    public String getNombre() {
        return nombre;
    }

    public void renombrar(String nombre) {
        this.nombre = nombre;
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

    /** La invoca quien modifica roles o asignaciones. Ver el campo. */
    public void invalidarCachePermisos() {
        this.permisosVersion++;
    }

    /**
     * Una cuenta suspendida o cancelada no opera.
     *
     * <p>Se comprueba en cada peticion, no solo al iniciar sesion: un JWT es
     * valido hasta que caduca, y cortar el servicio no puede esperar a la
     * renovacion del token.
     */
    public boolean estaOperativa() {
        return estadoSuscripcion == EstadoSuscripcion.ACTIVA
                || estadoSuscripcion == EstadoSuscripcion.EN_PRUEBA;
    }
}
