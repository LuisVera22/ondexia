package com.ondexia.application.identidad;

import com.ondexia.domain.comun.ContextoOperacion;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.identidad.AsignacionEmpresa;
import com.ondexia.domain.identidad.CuentaRepositorio;
import com.ondexia.domain.identidad.Sucursal;
import com.ondexia.domain.identidad.SucursalRepositorio;
import com.ondexia.domain.identidad.Usuario;
import com.ondexia.domain.identidad.UsuarioEmpresaRepositorio;
import com.ondexia.domain.identidad.UsuarioRepositorio;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reúne todo lo que el frontend necesita nada más iniciar sesión.
 *
 * <p>Es una sola llamada a propósito: un endpoint para el usuario, otro para sus
 * empresas y otro para sus permisos obligaría al SPA a encadenar tres peticiones
 * antes de pintar el menú, y con arranque en frío de Lambda son tres esperas en
 * serie sobre la primera pantalla que ve el usuario.
 */
@Service
public class ConsultarContexto {

    private final UsuarioRepositorio usuarios;
    private final UsuarioEmpresaRepositorio asignaciones;
    private final SucursalRepositorio sucursales;
    private final ProveedorDeContexto contexto;
    private final PermisosEfectivos permisos;
    private final CuentaRepositorio cuentas;

    public ConsultarContexto(UsuarioRepositorio usuarios, UsuarioEmpresaRepositorio asignaciones,
            SucursalRepositorio sucursales, ProveedorDeContexto contexto,
            PermisosEfectivos permisos, CuentaRepositorio cuentas) {
        this.usuarios = usuarios;
        this.asignaciones = asignaciones;
        this.sucursales = sucursales;
        this.contexto = contexto;
        this.permisos = permisos;
        this.cuentas = cuentas;
    }

    @Transactional(readOnly = true)
    public ContextoResuelto ejecutar() {
        ContextoOperacion actual = contexto.obligatorio();

        Usuario usuario = usuarios.buscarPorId(actual.usuarioId())
                .orElseThrow(() -> new RecursoNoEncontrado("usuario", actual.usuarioId()));

        List<AsignacionEmpresa> empresas = asignaciones.listarAsignacionesDe(actual.usuarioId());

        return new ContextoResuelto(
                usuario.id(),
                usuario.nombre(),
                usuario.email(),
                actual.cuentaId(),
                actual.esAdministradorCuenta(),
                actual.empresaId(),
                actual.sucursalId(),
                empresas,
                establecimientosAlcanzables(actual),
                // Sin empresa activa el conjunto va vacío, y el frontend no debe
                // mostrar ningún módulo: no es que el usuario no pueda nada, es
                // que todavía no ha dicho sobre qué empresa opera.
                permisos.actuales().codigos(),
                // Una consulta más, y solo en este endpoint. El estado se necesita
                // para redactar el anuncio —suspendida y cancelada no dicen lo
                // mismo— y no cabe en el contexto, que se resuelve en cada
                // petición y no debe engordar por algo que se lee una vez.
                cuentas.buscarPorId(actual.cuentaId())
                        .map(cuenta -> cuenta.estadoSuscripcion().name())
                        .orElse(null),
                actual.soloLectura());
    }

    /**
     * Los establecimientos entre los que el usuario puede moverse.
     *
     * <p>Un {@code sucursalId} con valor en el contexto significa «solo esta», y
     * entonces no hay nada que elegir. A nulo significa «todas», y cuáles son
     * todas es justo lo que hasta ahora esta respuesta no decía: el frontend
     * recibía la asignación y de ahí no se puede deducir el catálogo, así que
     * quien alcanzaba todos los establecimientos se quedaba sin selector — que
     * es exactamente al revés de lo que corresponde.
     *
     * <p>Los inactivos se descartan. Un establecimiento desactivado sigue
     * apareciendo en los comprobantes que ya lo referencian, pero no se puede
     * emitir desde él, y ofrecerlo aquí terminaría en un error al elegir serie.
     *
     * <p>Sin empresa activa se devuelve vacío, igual que los permisos: no hay
     * empresa de la que listar establecimientos.
     */
    private List<Sucursal> establecimientosAlcanzables(ContextoOperacion actual) {
        if (!actual.tieneEmpresaActiva()) {
            return List.of();
        }

        return sucursales.listarDeEmpresa(actual.empresaId()).stream()
                .filter(Sucursal::estaActiva)
                .filter(sucursal -> actual.alcanzaTodasLasSucursales()
                        || sucursal.id().equals(actual.sucursalId()))
                .toList();
    }
}
