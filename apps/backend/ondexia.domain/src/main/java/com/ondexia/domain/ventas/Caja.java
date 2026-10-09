package com.ondexia.domain.ventas;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.util.Objects;
import java.util.UUID;

/**
 * Un punto de cobro dentro de un establecimiento.
 *
 * <h2>Qué es y qué no es</h2>
 *
 * <p>La caja es donde se cobra: el mostrador, el terminal, la mesa de la feria.
 * Un establecimiento tiene una o varias (doc 12 §3.4). No decide la serie del
 * comprobante —eso lo hace el establecimiento, que es lo que SUNAT espera y lo
 * que ya está construido— ni el almacén que descarga. Lo que sí decide es a qué
 * arqueo se atribuye cada cobro: una venta ocurre siempre dentro de una
 * {@link SesionCaja} abierta sobre una caja.
 *
 * <p>Por eso cuelga de un establecimiento y no de la empresa suelta: una caja
 * sin local sería un cobro sin sitio.
 */
public class Caja {

    private final UUID id;
    private final UUID empresaId;
    private final UUID sucursalId;
    private final String codigo;
    private String nombre;
    private boolean activa;

    public Caja(UUID id, UUID empresaId, UUID sucursalId, String codigo, String nombre) {
        this.id = Objects.requireNonNull(id, "id");
        this.empresaId = Objects.requireNonNull(empresaId, "empresaId");
        this.sucursalId = exigirSucursal(sucursalId);
        this.codigo = normalizarCodigo(codigo);
        this.nombre = exigirNombre(nombre);
        this.activa = true;
    }

    /** Reconstrucción desde persistencia. Solo lo usa el adaptador. */
    public Caja(UUID id, UUID empresaId, UUID sucursalId, String codigo, String nombre,
            boolean activa) {
        this.id = id;
        this.empresaId = empresaId;
        this.sucursalId = sucursalId;
        this.codigo = codigo;
        this.nombre = nombre;
        this.activa = activa;
    }

    public UUID id() {
        return id;
    }

    public UUID empresaId() {
        return empresaId;
    }

    public UUID sucursalId() {
        return sucursalId;
    }

    public String codigo() {
        return codigo;
    }

    public String nombre() {
        return nombre;
    }

    public boolean estaActiva() {
        return activa;
    }

    /**
     * Solo el nombre. El código identifica a la caja en cada sesión y en cada
     * venta ya registrada, y el establecimiento decide la serie de lo que ya se
     * emitió desde ella: ninguno de los dos se cambia.
     */
    public void renombrar(String nombre) {
        this.nombre = exigirNombre(nombre);
    }

    public void desactivar() {
        this.activa = false;
    }

    public void activar() {
        this.activa = true;
    }

    private static UUID exigirSucursal(UUID sucursalId) {
        if (sucursalId == null) {
            throw new ReglaDeNegocioViolada(
                    "establecimiento_requerido",
                    "La caja tiene que pertenecer a un establecimiento.");
        }
        return sucursalId;
    }

    private static String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new ReglaDeNegocioViolada("codigo_requerido", "El código de la caja es obligatorio.");
        }
        String limpio = codigo.trim().toUpperCase();
        if (!limpio.matches("[A-Z0-9-]{1,20}")) {
            throw new ReglaDeNegocioViolada(
                    "codigo_invalido",
                    "El código de la caja admite letras, números y guiones, hasta 20 caracteres.");
        }
        return limpio;
    }

    private static String exigirNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaDeNegocioViolada("nombre_requerido", "La caja necesita un nombre.");
        }
        return nombre.trim();
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Caja caja && id.equals(caja.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
