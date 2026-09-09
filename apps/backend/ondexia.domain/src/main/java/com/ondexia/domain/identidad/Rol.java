package com.ondexia.domain.identidad;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.util.Objects;
import java.util.UUID;

/**
 * Conjunto de permisos con nombre, que se asigna a un usuario sobre una empresa.
 *
 * <p>Con {@code cuentaId} nulo es un rol <strong>predefinido del sistema</strong>:
 * lo ven todas las cuentas y ninguna puede modificarlo. Esa inmutabilidad es
 * intencionada — si un cliente pudiera editar «Vendedor», la palabra dejaría de
 * significar lo mismo entre clientes y el soporte se volvería adivinanza.
 *
 * <p>Lo que sí puede hacer un cliente es duplicarlo y ajustar la copia.
 */
public class Rol {

    private final UUID id;
    private final UUID cuentaId;
    private final String codigo;

    private String nombre;
    private String descripcion;

    public Rol(UUID id, UUID cuentaId, String codigo, String nombre, String descripcion) {
        this.id = Objects.requireNonNull(id, "id");
        this.cuentaId = cuentaId;
        this.codigo = Objects.requireNonNull(codigo, "codigo");
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public UUID id() {
        return id;
    }

    public UUID cuentaId() {
        return cuentaId;
    }

    public String codigo() {
        return codigo;
    }

    public String nombre() {
        return nombre;
    }

    public String descripcion() {
        return descripcion;
    }

    public boolean esDelSistema() {
        return cuentaId == null;
    }

    public void renombrar(String nombre, String descripcion) {
        exigirModificable();
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    /** Copia este rol como rol propio de una cuenta, ya modificable. */
    public Rol duplicarPara(UUID id, UUID cuentaId, String codigo, String nombre) {
        return new Rol(id, Objects.requireNonNull(cuentaId, "cuentaId"), codigo, nombre,
                this.descripcion);
    }

    private void exigirModificable() {
        if (esDelSistema()) {
            throw new ReglaDeNegocioViolada(
                    "rol_del_sistema",
                    "El rol predefinido '" + codigo + "' no se modifica: duplícalo y ajusta la copia.");
        }
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Rol otro2 && id.equals(otro2.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
