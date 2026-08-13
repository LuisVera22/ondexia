package com.ondexia.domain.identidad;

import java.util.Objects;
import java.util.UUID;

/**
 * Asignación de un usuario a una empresa, con rol y alcance.
 *
 * <p>Responde la pregunta que se hace en <em>cada</em> petición: este usuario,
 * sobre esta empresa, qué puede.
 *
 * <p>Quien <strong>concede</strong> es el administrador de la cuenta; quien
 * <strong>tiene</strong> varias empresas puede ser cualquiera. La distinción
 * cubre el caso que rompe la regla estricta: un contador en planilla que lleva
 * tres RUC del mismo grupo necesita las tres sin heredar la facturación.
 */
public class UsuarioEmpresa {

    private final UUID id;
    private final UUID usuarioId;
    private final UUID empresaId;
    private UUID rolId;

    /**
     * Sucursal a la que se acota. {@code null} significa <strong>todas</strong>.
     *
     * <p>Es lo que permite expresar «Ventas solo en Miraflores»: quien atiende un
     * local no debe poder cambiarse a otro, porque eso decide la serie del
     * comprobante y qué almacén descarga.
     */
    private UUID sucursalId;

    public UsuarioEmpresa(UUID id, UUID usuarioId, UUID empresaId, UUID rolId, UUID sucursalId) {
        this.id = Objects.requireNonNull(id, "id");
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.empresaId = Objects.requireNonNull(empresaId, "empresaId");
        this.rolId = Objects.requireNonNull(rolId, "rolId");
        this.sucursalId = sucursalId;
    }

    public UUID id() {
        return id;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public UUID empresaId() {
        return empresaId;
    }

    public UUID rolId() {
        return rolId;
    }

    public UUID sucursalId() {
        return sucursalId;
    }

    public boolean alcanzaTodasLasSucursales() {
        return sucursalId == null;
    }

    public void reasignar(UUID rolId, UUID sucursalId) {
        this.rolId = Objects.requireNonNull(rolId, "rolId");
        this.sucursalId = sucursalId;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof UsuarioEmpresa otra && id.equals(otra.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
