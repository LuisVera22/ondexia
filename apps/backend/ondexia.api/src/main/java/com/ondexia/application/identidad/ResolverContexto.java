package com.ondexia.application.identidad;

import com.ondexia.domain.comun.ContextoOperacion;
import com.ondexia.domain.comun.error.AccesoDenegado;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
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
        /*
         * «Token válido, usuario sin registrar» NO es un fallo de autenticación.
         *
         * Es el estado normal de quien acaba de crear su cuenta en Cognito y
         * todavía no ha completado el alta: identidad probada, sin fila en
         * nuestra base. Antes salía como 401, y eso mandaba al SPA a cerrar la
         * sesión y volver al acceso — donde Cognito lo dejaba entrar otra vez,
         * porque la sesión de Cognito sí es válida. Un bucle.
         *
         * Sale como 404 con código propio para que el frontend lo distinga y
         * lleve a la pantalla de registro. El 401 queda para lo que de verdad
         * es: token ausente, caducado o mal firmado.
         */
        Usuario usuario = usuarios.buscarPorCognitoSub(cognitoSub)
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "usuario_no_registrado",
                        "El token es valido pero todavia no hay una cuenta asociada. "
                                + "Falta completar el registro."));

        // Se comprueba en cada petición, no solo al iniciar sesión: desactivar a
        // alguien tiene que cortarle el acceso ya, y Cognito no revoca un token
        // de acceso ya emitido.
        if (!usuario.estaActivo()) {
            throw new AccesoDenegado("El usuario esta desactivado.");
        }

        Cuenta cuenta = cuentas.buscarPorId(usuario.cuentaId())
                .orElseThrow(() -> new IllegalStateException(
                        "El usuario " + usuario.id() + " apunta a una cuenta inexistente"));

        /*
         * Una suscripción caída ya NO cierra la puerta.
         *
         * Antes esto lanzaba AccesoDenegado y la cuenta suspendida no podía ni
         * mirar sus comprobantes. Es la decisión del doc 09 §5.1: el cliente
         * responde ante SUNAT de documentos que debe conservar cinco años, así que
         * dejarle fuera de sus propios datos por una factura impaga le convierte un
         * problema comercial en uno tributario.
         *
         * Entra, y el contexto queda marcado como solo lectura. El recorte lo
         * aplica PermisosEfectivos, no este método: aquí se resuelve QUIÉN es, no
         * qué puede.
         */
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
                !cuenta.permiteEscritura(),
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
