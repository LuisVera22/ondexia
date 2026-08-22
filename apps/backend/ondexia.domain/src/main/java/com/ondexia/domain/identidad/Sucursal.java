package com.ondexia.domain.identidad;

import com.ondexia.domain.comun.Ubigeo;
import java.util.Objects;
import java.util.UUID;

/**
 * Establecimiento anexo de una empresa.
 *
 * <p>No es organización cosmética: la sucursal decide <strong>qué serie</strong>
 * lleva el comprobante y <strong>qué almacén</strong> se descarga. Por eso el
 * alcance de un usuario puede acotarse a una.
 *
 * <p>El {@code codigo} es el del establecimiento anexo ante SUNAT, no un número
 * interno: aparece en el comprobante y SUNAT lo valida contra su registro.
 */
public class Sucursal {

    private final UUID id;
    private final UUID empresaId;
    private final String codigo;

    private String nombre;
    private String direccion;
    private Ubigeo ubigeo;
    private boolean activa;

    public Sucursal(UUID id, UUID empresaId, String codigo, String nombre, String direccion) {
        this.id = Objects.requireNonNull(id, "id");
        this.empresaId = Objects.requireNonNull(empresaId, "empresaId");
        this.codigo = Objects.requireNonNull(codigo, "codigo");
        this.nombre = nombre;
        this.direccion = direccion;
        this.activa = true;
    }

    public Sucursal(UUID id, UUID empresaId, String codigo, String nombre, String direccion,
            Ubigeo ubigeo, boolean activa) {
        this.id = id;
        this.empresaId = empresaId;
        this.codigo = codigo;
        this.nombre = nombre;
        this.direccion = direccion;
        this.ubigeo = ubigeo;
        this.activa = activa;
    }

    public UUID id() {
        return id;
    }

    public UUID empresaId() {
        return empresaId;
    }

    public String codigo() {
        return codigo;
    }

    public String nombre() {
        return nombre;
    }

    public String direccion() {
        return direccion;
    }

    public Ubigeo ubigeo() {
        return ubigeo;
    }

    public boolean estaActiva() {
        return activa;
    }

    public void actualizar(String nombre, String direccion, Ubigeo ubigeo) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.ubigeo = ubigeo;
    }

    public void desactivar() {
        this.activa = false;
    }

    /**
     * Vuelve a ponerlo en servicio.
     *
     * <p>Faltaba, y no era un olvido inocuo: sin esto, desactivar un
     * establecimiento no tenia vuelta atras en ninguna capa. Quien se equivocara
     * de fila perdia el local para siempre, con sus series colgando de el.
     *
     * <p>No hay nada que restaurar al reactivar. El codigo, las series y su
     * numeracion siguieron ahi todo el tiempo: desactivar solo lo saca de los
     * desplegables.
     */
    public void activar() {
        this.activa = true;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Sucursal otra && id.equals(otra.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
