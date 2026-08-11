package com.ondexia.domain.identidad;

import com.ondexia.domain.comun.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Asignacion de un usuario a una empresa, con un rol y un alcance.
 *
 * <p>Es la tabla que responde la pregunta que se hace en <em>cada</em> peticion:
 * este usuario, sobre esta empresa, con que puede. El token no la contesta —
 * porta identidad y nada mas (DTE §8.1).
 *
 * <p><strong>Quien concede es el administrador de la cuenta; quien tiene varias
 * empresas puede ser cualquiera.</strong> La distincion evita el caso que rompe
 * la regla estricta: un contador en planilla que lleva tres RUC del mismo grupo
 * necesita las tres empresas sin heredar la facturacion ni la gestion de
 * usuarios.
 */
@Entity
@Table(name = "usuario_empresa")
public class UsuarioEmpresa extends EntidadBase {

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Column(name = "rol_id", nullable = false)
    private UUID rolId;

    /**
     * Sucursal a la que se acota la asignacion. {@code null} significa
     * <strong>todas</strong>.
     *
     * <p>Es lo que permite expresar «Ventas solo en Miraflores». Quien atiende
     * un local no debe poder cambiarse a otro, porque eso decide la serie del
     * comprobante y que almacen descarga — no es una preferencia de interfaz.
     *
     * <p>El nulo como comodin obliga a un detalle en el indice unico: en
     * PostgreSQL {@code NULL} nunca es igual a {@code NULL}, asi que un
     * {@code UNIQUE} corriente dejaria crear dos asignaciones «todas las
     * sucursales» para el mismo par — y con dos filas, cual gana la resolucion
     * del contexto seria cuestion de suerte. La migracion V1 lo resuelve con
     * {@code UNIQUE NULLS NOT DISTINCT}.
     */
    @Column(name = "sucursal_id")
    private UUID sucursalId;

    protected UsuarioEmpresa() {
        // Requerido por JPA.
    }

    public UsuarioEmpresa(UUID usuarioId, UUID empresaId, UUID rolId, UUID sucursalId) {
        this.usuarioId = usuarioId;
        this.empresaId = empresaId;
        this.rolId = rolId;
        this.sucursalId = sucursalId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public UUID getRolId() {
        return rolId;
    }

    public UUID getSucursalId() {
        return sucursalId;
    }

    public boolean alcanzaTodasLasSucursales() {
        return sucursalId == null;
    }

    public void reasignar(UUID rolId, UUID sucursalId) {
        this.rolId = rolId;
        this.sucursalId = sucursalId;
    }
}
