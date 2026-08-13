package com.ondexia.domain.almacen;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.util.Objects;
import java.util.UUID;

/**
 * Dónde están físicamente las existencias.
 *
 * <p>No es lo mismo que un establecimiento. El establecimiento es la dirección
 * que SUNAT conoce y la que decide la serie del comprobante; el almacén es el
 * sitio del que sale la mercadería. En un mismo local puede haber varios —
 * mostrador, depósito, mercadería en tránsito— y hay almacenes que no cuelgan
 * de ningún anexo, de ahí que la sucursal sea opcional.
 *
 * <p>Confundirlos tiene consecuencias: descargar existencias del almacén
 * equivocado deja el kardex cuadrando en total y mintiendo por ubicación, que
 * es peor que no cuadrar — nadie lo nota hasta el inventario físico.
 */
public class Almacen {

    private final UUID id;
    private final UUID empresaId;
    private final String codigo;
    private UUID sucursalId;
    private String nombre;
    private boolean activo;

    public Almacen(UUID id, UUID empresaId, String codigo, String nombre, UUID sucursalId) {
        this.id = Objects.requireNonNull(id, "id");
        this.empresaId = Objects.requireNonNull(empresaId, "empresaId");
        this.codigo = normalizarCodigo(codigo);
        this.nombre = exigirNombre(nombre);
        this.sucursalId = sucursalId;
        this.activo = true;
    }

    /** Reconstrucción desde persistencia. */
    public Almacen(UUID id, UUID empresaId, String codigo, String nombre, UUID sucursalId,
            boolean activo) {
        this.id = id;
        this.empresaId = empresaId;
        this.codigo = codigo;
        this.nombre = nombre;
        this.sucursalId = sucursalId;
        this.activo = activo;
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

    public UUID sucursalId() {
        return sucursalId;
    }

    public boolean estaActivo() {
        return activo;
    }

    /**
     * El código no cambia: identifica al almacén en los movimientos de stock ya
     * registrados, y esos no se reescriben.
     */
    public void actualizar(String nombre, UUID sucursalId) {
        this.nombre = exigirNombre(nombre);
        this.sucursalId = sucursalId;
    }

    /**
     * Desactiva, nunca borra. Un almacén aparece en cada movimiento de stock que
     * lo tocó; borrarlo dejaría el kardex apuntando a nada.
     */
    public void desactivar() {
        this.activo = false;
    }

    public void activar() {
        this.activo = true;
    }

    /**
     * Código corto y en mayúsculas: se teclea en cada movimiento de mercadería,
     * y ahí la brevedad es la diferencia entre usarlo y no usarlo.
     */
    private static String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new ReglaDeNegocioViolada(
                    "codigo_requerido", "El almacén necesita un código.");
        }
        String limpio = codigo.trim().toUpperCase();
        if (!limpio.matches("[A-Z0-9-]{1,20}")) {
            throw new ReglaDeNegocioViolada(
                    "codigo_invalido",
                    "El código del almacén admite letras, números y guiones, hasta 20 caracteres.");
        }
        return limpio;
    }

    private static String exigirNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaDeNegocioViolada(
                    "nombre_requerido", "El almacén necesita un nombre.");
        }
        return nombre.trim();
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Almacen almacen && id.equals(almacen.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
