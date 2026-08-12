package com.ondexia.infrastructure.salida.persistencia.identidad;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.identidad.AsignacionEmpresa;
import com.ondexia.domain.identidad.Cuenta;
import com.ondexia.domain.identidad.CuentaAdministrador;
import com.ondexia.domain.identidad.CuentaAdministradorRepositorio;
import com.ondexia.domain.identidad.CuentaRepositorio;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.identidad.EmpresaRepositorio;
import com.ondexia.domain.identidad.Permiso;
import com.ondexia.domain.identidad.PermisoRepositorio;
import com.ondexia.domain.identidad.Permisos;
import com.ondexia.domain.identidad.Sucursal;
import com.ondexia.domain.identidad.SucursalRepositorio;
import com.ondexia.domain.identidad.Usuario;
import com.ondexia.domain.identidad.UsuarioEmpresa;
import com.ondexia.domain.identidad.UsuarioEmpresaRepositorio;
import com.ondexia.domain.identidad.UsuarioRepositorio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los adaptadores que implementan los puertos del dominio.
 *
 * <p>Se agrupan en un archivo porque cada uno son quince líneas de delegación y
 * siempre se leen juntos. Si alguno crece —cuando llegue una consulta con
 * filtros de verdad— se saca a su propio archivo.
 *
 * <h2>Aquí está el {@code @Transactional}, y a la vista</h2>
 *
 * Es el beneficio concreto de haber sustituido las interfaces de Spring Data por
 * adaptadores. Un método de consulta derivado no es transaccional, y sobre una
 * tabla con Row Level Security eso devuelve vacío sin dar ningún error: el
 * inquilino nunca se fija. En una clase normal la anotación se ve, y si falta se
 * nota leyendo el archivo.
 */
public final class AdaptadoresIdentidad {

    private AdaptadoresIdentidad() {
    }

    @Repository
    @Transactional(readOnly = true)
    public static class Usuarios implements UsuarioRepositorio {

        private final UsuarioJpaRepository filas;

        public Usuarios(UsuarioJpaRepository filas) {
            this.filas = filas;
        }

        @Override
        public Optional<Usuario> buscarPorId(UUID id) {
            return filas.findById(id).map(MapeadoresIdentidad::aDominio);
        }

        @Override
        public Optional<Usuario> buscarPorCognitoSub(String cognitoSub) {
            return filas.findByCognitoSub(cognitoSub).map(MapeadoresIdentidad::aDominio);
        }

        @Override
        public Optional<Usuario> buscarPorEmailEnCuenta(UUID cuentaId, String email) {
            return filas.findByCuentaIdAndEmailIgnoreCase(cuentaId, email)
                    .map(MapeadoresIdentidad::aDominio);
        }

        @Override
        @Transactional
        public Usuario guardar(Usuario usuario) {
            return MapeadoresIdentidad.aDominio(filas.save(MapeadoresIdentidad.aFila(usuario)));
        }
    }

    @Repository
    @Transactional(readOnly = true)
    public static class Cuentas implements CuentaRepositorio {

        private final CuentaJpaRepository filas;

        public Cuentas(CuentaJpaRepository filas) {
            this.filas = filas;
        }

        @Override
        public Optional<Cuenta> buscarPorId(UUID id) {
            return filas.findById(id).map(MapeadoresIdentidad::aDominio);
        }

        @Override
        @Transactional
        public Cuenta guardar(Cuenta cuenta) {
            return MapeadoresIdentidad.aDominio(filas.save(MapeadoresIdentidad.aFila(cuenta)));
        }

        @Override
        @Transactional
        public void invalidarCachePermisos(UUID cuentaId) {
            filas.incrementarPermisosVersion(cuentaId);
        }
    }

    @Repository
    @Transactional(readOnly = true)
    public static class Empresas implements EmpresaRepositorio {

        private final EmpresaJpaRepository filas;

        public Empresas(EmpresaJpaRepository filas) {
            this.filas = filas;
        }

        @Override
        public Optional<Empresa> buscarPorId(UUID id) {
            return filas.findById(id).map(MapeadoresIdentidad::aDominio);
        }

        @Override
        public Optional<Empresa> buscarPorRuc(Ruc ruc) {
            return filas.findByRuc(ruc.valor()).map(MapeadoresIdentidad::aDominio);
        }

        @Override
        public List<Empresa> listarDeCuenta(UUID cuentaId) {
            return filas.findByCuentaIdOrderByRazonSocial(cuentaId).stream()
                    .map(MapeadoresIdentidad::aDominio)
                    .toList();
        }

        @Override
        @Transactional
        public Empresa guardar(Empresa empresa) {
            return MapeadoresIdentidad.aDominio(filas.save(MapeadoresIdentidad.aFila(empresa)));
        }
    }

    @Repository
    @Transactional(readOnly = true)
    public static class Sucursales implements SucursalRepositorio {

        private final SucursalJpaRepository filas;

        public Sucursales(SucursalJpaRepository filas) {
            this.filas = filas;
        }

        @Override
        public Optional<Sucursal> buscarPorId(UUID id) {
            return filas.findById(id).map(MapeadoresIdentidad::aDominio);
        }

        @Override
        public Optional<Sucursal> buscarPorCodigo(UUID empresaId, String codigo) {
            return filas.findByEmpresaIdAndCodigo(empresaId, codigo)
                    .map(MapeadoresIdentidad::aDominio);
        }

        @Override
        public List<Sucursal> listarDeEmpresa(UUID empresaId) {
            return filas.findByEmpresaIdOrderByNombre(empresaId).stream()
                    .map(MapeadoresIdentidad::aDominio)
                    .toList();
        }

        @Override
        @Transactional
        public Sucursal guardar(Sucursal sucursal) {
            return MapeadoresIdentidad.aDominio(filas.save(MapeadoresIdentidad.aFila(sucursal)));
        }
    }

    @Repository
    @Transactional(readOnly = true)
    public static class Asignaciones implements UsuarioEmpresaRepositorio {

        private final UsuarioEmpresaJpaRepository filas;

        public Asignaciones(UsuarioEmpresaJpaRepository filas) {
            this.filas = filas;
        }

        @Override
        public List<AsignacionEmpresa> listarAsignacionesDe(UUID usuarioId) {
            return filas.findAsignacionesByUsuarioId(usuarioId);
        }

        @Override
        public Optional<UsuarioEmpresa> buscarAsignacion(UUID usuarioId, UUID empresaId) {
            return filas.findByUsuarioIdAndEmpresaId(usuarioId, empresaId)
                    .map(MapeadoresIdentidad::aDominio);
        }

        @Override
        public List<UsuarioEmpresa> listarDeEmpresa(UUID empresaId) {
            return filas.findByEmpresaId(empresaId).stream()
                    .map(MapeadoresIdentidad::aDominio)
                    .toList();
        }

        @Override
        @Transactional
        public UsuarioEmpresa guardar(UsuarioEmpresa asignacion) {
            return MapeadoresIdentidad.aDominio(filas.save(MapeadoresIdentidad.aFila(asignacion)));
        }
    }

    @Repository
    @Transactional(readOnly = true)
    public static class Administradores implements CuentaAdministradorRepositorio {

        private final CuentaAdministradorJpaRepository filas;

        public Administradores(CuentaAdministradorJpaRepository filas) {
            this.filas = filas;
        }

        @Override
        public boolean esAdministrador(UUID cuentaId, UUID usuarioId) {
            return filas.existsByCuentaIdAndUsuarioId(cuentaId, usuarioId);
        }

        @Override
        public List<CuentaAdministrador> listarDeCuenta(UUID cuentaId) {
            return filas.findByCuentaId(cuentaId).stream()
                    .map(MapeadoresIdentidad::aDominio)
                    .toList();
        }

        @Override
        public long contarEnCuenta(UUID cuentaId) {
            return filas.countByCuentaId(cuentaId);
        }

        @Override
        @Transactional
        public CuentaAdministrador guardar(CuentaAdministrador administrador) {
            return MapeadoresIdentidad.aDominio(
                    filas.save(MapeadoresIdentidad.aFila(administrador)));
        }

        @Override
        @Transactional
        public void eliminar(UUID id) {
            filas.deleteById(id);
        }
    }

    @Repository
    @Transactional(readOnly = true)
    public static class Permisologia implements PermisoRepositorio {

        private final PermisoJpaRepository filas;

        public Permisologia(PermisoJpaRepository filas) {
            this.filas = filas;
        }

        @Override
        public Permisos permisosDelRol(UUID rolId) {
            return new Permisos(filas.findCodigosByRolId(rolId));
        }

        @Override
        public List<Permiso> listarCatalogo() {
            return filas.findAllByOrderByModuloAscAccionAsc().stream()
                    .map(MapeadoresIdentidad::aDominio)
                    .toList();
        }
    }
}
