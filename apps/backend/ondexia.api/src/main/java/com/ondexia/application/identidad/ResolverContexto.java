package com.ondexia.application.identidad;

import com.ondexia.domain.comun.ContextoOperacion;
import com.ondexia.domain.comun.error.AccesoDenegado;
import com.ondexia.domain.comun.error.NoAutenticado;
import com.ondexia.domain.identidad.AsignacionEmpresa;
import com.ondexia.domain.identidad.Cuenta;
import com.ondexia.domain.identidad.CuentaAdministradorRepositorio;
import com.ondexia.domain.identidad.CuentaRepositorio;
import com.ondexia.domain.identidad.Usuario;
import com.ondexia.domain.identidad.UsuarioEmpresaRepositorio;
import com.ondexia.domain.identidad.UsuarioRepositorio;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traduce «un token de Cognito» a «este usuario, sobre esta empresa».
 *
 * <p>Es el único sitio del sistema donde una cabecera enviada por el cliente se
 * convierte en autorización, y por eso concentra todas las comprobaciones.
 */
@Service
public class ResolverContexto {

    private final UsuarioRepositorio usuarios;
    private final CuentaRepositorio cuentas;
    private final UsuarioEmpresaRepositorio asignaciones;
    private final CuentaAdministradorRepositorio administradores;

    public ResolverContexto(UsuarioRepositorio usuarios, CuentaRepositorio cuentas,
            UsuarioEmpresaRepositorio asignaciones,
            CuentaAdministradorRepositorio administradores) {
        this.usuarios = usuarios;
        this.cuentas = cuentas;
        this.asignaciones = asignaciones;
        this.administradores = administradores;
    }

    /**
     * @param cognitoSub    el {@code sub} del token, ya validado por Spring
     *                      Security: que la firma sea correcta está comprobado
     *                      cuando se llega aquí
     * @param empresaPedida empresa que el cliente <em>dice</em> tener activa
     */
    @Transactional(readOnly = true)
    public ContextoOperacion ejecutar(String cognitoSub, UUID empresaPedida, String ip) {
        Usuario usuario = usuarios.buscarPorCognitoSub(cognitoSub)
                .orElseThrow(() -> new NoAutenticado(
                        "El token es valido pero no corresponde a ningun usuario registrado."));

        // Se comprueba en cada petición, no solo al iniciar sesión: desactivar a
        // alguien tiene que cortarle el acceso ya, y Cognito no revoca un token
        // de acceso ya emitido.
        if (!usuario.estaActivo()) {
            throw new AccesoDenegado("El usuario esta desactivado.");
        }

        Cuenta cuenta = cuentas.buscarPorId(usuario.cuentaId())
                .orElseThrow(() -> new IllegalStateException(
                        "El usuario " + usuario.id() + " apunta a una cuenta inexistente"));

        if (!cuenta.estaOperativa()) {
            throw new AccesoDenegado(
                    "La suscripcion de la cuenta esta " + cuenta.estadoSuscripcion() + ".");
        }

        boolean esAdministrador = administradores.esAdministrador(cuenta.id(), usuario.id());
        AsignacionEmpresa activa = elegirEmpresa(usuario.id(), empresaPedida);

        return new ContextoOperacion(
                usuario.id(),
                cuenta.id(),
                cuenta.permisosVersion(),
                activa == null ? null : activa.empresaId(),
                activa == null ? null : activa.sucursalId(),
                activa == null ? null : activa.rolId(),
                esAdministrador,
                ip);
    }

    /**
     * Decide sobre qué empresa opera la petición.
     *
     * <p><strong>Ser administrador de la cuenta no concede acceso a las
     * empresas.</strong> El administrador es quien <em>concede</em>; para
     * <em>usar</em> una empresa tiene que asignarse a sí mismo, como cualquiera.
     * Que conceder y ejercer sean actos distintos es lo que deja rastro de quién
     * podía hacer qué y desde cuándo.
     */
    private AsignacionEmpresa elegirEmpresa(UUID usuarioId, UUID empresaPedida) {
        List<AsignacionEmpresa> disponibles = asignaciones.listarAsignacionesDe(usuarioId);

        if (empresaPedida != null) {
            return disponibles.stream()
                    .filter(a -> a.empresaId().equals(empresaPedida))
                    .findFirst()
                    // Mismo mensaje para «no existe» y «existe pero no es tuya»:
                    // distinguirlos permitiría averiguar qué RUC están dados de
                    // alta en Ondexia probando identificadores.
                    .orElseThrow(() -> new AccesoDenegado(
                            "No tienes acceso a la empresa solicitada"));
        }

        // Con una sola empresa no hay ambigüedad que resolver.
        if (disponibles.size() == 1) {
            return disponibles.get(0);
        }

        // Con varias, o ninguna, el contexto queda sin empresa. Los endpoints que
        // la necesiten fallarán con un mensaje claro; /contexto sigue funcionando,
        // que es el que devuelve la lista para elegir.
        return null;
    }
}
