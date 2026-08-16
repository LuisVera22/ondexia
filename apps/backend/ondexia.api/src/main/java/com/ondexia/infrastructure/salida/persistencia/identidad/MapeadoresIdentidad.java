package com.ondexia.infrastructure.salida.persistencia.identidad;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.Ubigeo;
import com.ondexia.domain.identidad.Cuenta;
import com.ondexia.domain.identidad.CuentaAdministrador;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.identidad.Permiso;
import com.ondexia.domain.identidad.Rol;
import com.ondexia.domain.identidad.Sucursal;
import com.ondexia.domain.identidad.Usuario;
import com.ondexia.domain.identidad.UsuarioEmpresa;

/**
 * Traducción entre los agregados del dominio y las filas de la base.
 *
 * <p>Todos los mapeos de identidad viven en un archivo a propósito: repartirlos
 * en un mapeador por agregado son siete archivos de veinte líneas que siempre
 * se leen juntos. La regla se rompería si el paquete creciera; con identidad no
 * va a crecer.
 *
 * <h2>El detalle que importa: al reconstruir NO se valida</h2>
 *
 * {@code Ruc} valida el dígito verificador en su constructor. Al leer de la base
 * eso vuelve a ejecutarse, y es lo correcto: si una fila tiene un RUC inválido
 * —porque entró por un script, o porque la regla cambió— queremos enterarnos al
 * cargarla, no al emitir un comprobante con ella.
 *
 * <p>Es una decisión consciente, no un descuido: hace que la base y el dominio
 * no puedan divergir en silencio.
 */
final class MapeadoresIdentidad {

    private MapeadoresIdentidad() {
    }

    // ── Cuenta ─────────────────────────────────────────────────────────────

    static Cuenta aDominio(CuentaJpa fila) {
        return new Cuenta(fila.getId(), fila.getNombre(), fila.getPlan(),
                fila.getEstadoSuscripcion(), fila.getPermisosVersion());
    }

    static CuentaJpa aFila(Cuenta cuenta) {
        return new CuentaJpa(cuenta.id(), cuenta.nombre(), cuenta.plan(),
                cuenta.estadoSuscripcion(), cuenta.permisosVersion());
    }

    // ── Empresa ────────────────────────────────────────────────────────────

    static Empresa aDominio(EmpresaJpa fila) {
        return new Empresa(
                fila.getId(), fila.getCuentaId(), new Ruc(fila.getRuc()), fila.getRazonSocial(),
                fila.getNombreComercial(), fila.getDomicilioFiscal(),
                fila.getUbigeo() == null ? null : new Ubigeo(fila.getUbigeo()),
                fila.getSecretArnCertificado(), fila.getUsuarioSol(), fila.getModoSunat(),
                fila.isActivo());
    }

    static EmpresaJpa aFila(Empresa empresa) {
        return new EmpresaJpa(
                empresa.id(), empresa.cuentaId(), empresa.ruc().valor(), empresa.razonSocial(),
                empresa.nombreComercial(), empresa.domicilioFiscal(),
                empresa.ubigeo() == null ? null : empresa.ubigeo().valor(),
                empresa.secretArnCertificado(), empresa.usuarioSol(), empresa.modoSunat(),
                empresa.estaActiva());
    }

    // ── Sucursal ───────────────────────────────────────────────────────────

    static Sucursal aDominio(SucursalJpa fila) {
        return new Sucursal(fila.getId(), fila.getEmpresaId(), fila.getCodigo(), fila.getNombre(),
                fila.getDireccion(),
                fila.getUbigeo() == null ? null : new Ubigeo(fila.getUbigeo()),
                fila.isActivo());
    }

    static SucursalJpa aFila(Sucursal sucursal) {
        return new SucursalJpa(sucursal.id(), sucursal.empresaId(), sucursal.codigo(),
                sucursal.nombre(), sucursal.direccion(),
                sucursal.ubigeo() == null ? null : sucursal.ubigeo().valor(),
                sucursal.estaActiva());
    }

    // ── Usuario ────────────────────────────────────────────────────────────

    static Usuario aDominio(UsuarioJpa fila) {
        return new Usuario(fila.getId(), fila.getCuentaId(), fila.getCognitoSub(), fila.getEmail(),
                fila.getNombre(), fila.getTelefono(), fila.isActivo());
    }

    static UsuarioJpa aFila(Usuario usuario) {
        return new UsuarioJpa(usuario.id(), usuario.cuentaId(), usuario.cognitoSub(),
                usuario.email(), usuario.nombre(), usuario.telefono(), usuario.estaActivo());
    }

    // ── Asignación ─────────────────────────────────────────────────────────

    static UsuarioEmpresa aDominio(UsuarioEmpresaJpa fila) {
        return new UsuarioEmpresa(fila.getId(), fila.getUsuarioId(), fila.getEmpresaId(),
                fila.getRolId(), fila.getSucursalId());
    }

    static UsuarioEmpresaJpa aFila(UsuarioEmpresa asignacion) {
        return new UsuarioEmpresaJpa(asignacion.id(), asignacion.usuarioId(),
                asignacion.empresaId(), asignacion.rolId(), asignacion.sucursalId());
    }

    // ── Administrador ──────────────────────────────────────────────────────

    static CuentaAdministrador aDominio(CuentaAdministradorJpa fila) {
        return new CuentaAdministrador(fila.getId(), fila.getCuentaId(), fila.getUsuarioId());
    }

    static CuentaAdministradorJpa aFila(CuentaAdministrador administrador) {
        return new CuentaAdministradorJpa(administrador.id(), administrador.cuentaId(),
                administrador.usuarioId());
    }

    // ── Permiso ────────────────────────────────────────────────────────────

    static Permiso aDominio(PermisoJpa fila) {
        return new Permiso(
                fila.getId(),
                com.ondexia.domain.identidad.NivelPermiso.valueOf(fila.getNivel()),
                fila.getModulo(),
                fila.getAccion(),
                fila.getNombre(),
                fila.getDescripcion());
    }

    // ── Rol ────────────────────────────────────────────────────────────────

    static Rol aDominio(RolJpa fila) {
        return new Rol(fila.getId(), fila.getCuentaId(), fila.getCodigo(), fila.getNombre(),
                fila.getDescripcion());
    }
}
