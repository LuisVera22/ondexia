package com.ondexia.infrastructure.salida.persistencia.almacen;

import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "almacen")
public class AlmacenJpa extends EntidadJpaBase {

    /**
     * Se escribe aunque la política de RLS ya la impone.
     *
     * <p>La política de la tabla lleva {@code WITH CHECK (empresa_id =
     * empresa_actual())}, así que una fila con la empresa equivocada se rechaza
     * en la base. Mandarla igualmente convierte esa red de seguridad en lo que
     * debe ser —una red— en lugar de en el único mecanismo.
     */
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Column(name = "codigo", nullable = false, updatable = false, length = 20)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "sucursal_id")
    private UUID sucursalId;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected AlmacenJpa() {
    }

    public AlmacenJpa(UUID id, UUID empresaId, String codigo, String nombre, UUID sucursalId,
            boolean activo) {
        this.id = id;
        this.empresaId = empresaId;
        this.codigo = codigo;
        actualizarDesde(nombre, sucursalId, activo);
    }

    public final void actualizarDesde(String nombre, UUID sucursalId, boolean activo) {
        this.nombre = nombre;
        this.sucursalId = sucursalId;
        this.activo = activo;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public UUID getSucursalId() {
        return sucursalId;
    }

    public boolean isActivo() {
        return activo;
    }
}
