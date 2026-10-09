package com.ondexia.domain.identidad;

import java.util.Objects;
import java.util.UUID;

/**
 * El cliente que contrata Ondexia.
 *
 * <p>La suscripción cuelga de la cuenta, no de la empresa: un contribuyente
 * puede operar varios RUC —normal en grupos familiares y cadenas— y cobrarle
 * una suscripción por cada uno sería cobrarle varias veces por lo mismo. La
 * cuenta es la unidad comercial; la empresa, la unidad fiscal.
 */
public class Cuenta {

    private final UUID id;
    private String nombre;
    private PlanSuscripcion plan;
    private EstadoSuscripcion estadoSuscripcion;
    private long permisosVersion;

    public Cuenta(UUID id, String nombre, PlanSuscripcion plan, EstadoSuscripcion estado) {
        this.id = Objects.requireNonNull(id, "id");
        this.nombre = nombre;
        this.plan = plan;
        this.estadoSuscripcion = estado;
        this.permisosVersion = 1L;
    }

    public Cuenta(UUID id, String nombre, PlanSuscripcion plan, EstadoSuscripcion estado,
            long permisosVersion) {
        this(id, nombre, plan, estado);
        this.permisosVersion = permisosVersion;
    }

    public UUID id() {
        return id;
    }

    public String nombre() {
        return nombre;
    }

    public PlanSuscripcion plan() {
        return plan;
    }

    public EstadoSuscripcion estadoSuscripcion() {
        return estadoSuscripcion;
    }

    /**
     * Se incrementa al tocar cualquier rol de la cuenta.
     *
     * <p>Permite cachear los permisos en memoria del contenedor de Lambda sin
     * servirlos rancios: con la versión en la clave del caché, revocar un
     * permiso invalida la entrada de inmediato y sin coordinación entre
     * contenedores.
     */
    public long permisosVersion() {
        return permisosVersion;
    }

    public void renombrar(String nombre) {
        this.nombre = nombre;
    }

    public void invalidarCachePermisos() {
        this.permisosVersion++;
    }

    /**
     * Una cuenta suspendida o cancelada no opera.
     *
     * <p>Se comprueba en cada petición, no solo al iniciar sesión: un JWT es
     * válido hasta que caduca, y cortar el servicio no puede esperar a la
     * renovación del token.
     */
    public boolean estaOperativa() {
        return estadoSuscripcion == EstadoSuscripcion.ACTIVA
                || estadoSuscripcion == EstadoSuscripcion.EN_PRUEBA;
    }

    /**
     * Si la cuenta puede modificar datos.
     *
     * <h2>Por qué no es lo mismo que poder entrar</h2>
     *
     * <p>Hasta el doc 09 §5.1, {@code estaOperativa()} se usaba para negar el
     * acceso entero: una cuenta suspendida recibía un 403 en cada petición y no
     * podía ni mirar sus comprobantes. Eso trasladaba un problema comercial
     * —una factura impaga— a uno tributario, porque los comprobantes tienen
     * obligación de conservación de cinco años y quien responde por ellos ante
     * SUNAT es el cliente, no nosotros.
     *
     * <p>Ahora entra y consulta; lo que se corta es lo que genera obligaciones
     * nuevas. La distinción entre las dos es la misma que hay entre cobrar y
     * castigar.
     */
    public boolean permiteEscritura() {
        return estaOperativa();
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Cuenta otra && id.equals(otra.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
