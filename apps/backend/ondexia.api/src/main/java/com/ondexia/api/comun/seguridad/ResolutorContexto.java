package com.ondexia.api.comun.seguridad;

import com.ondexia.api.comun.error.AccesoDenegadoException;
import com.ondexia.api.comun.error.NoAutenticadoException;
import com.ondexia.domain.identidad.AsignacionEmpresa;
import com.ondexia.domain.identidad.Cuenta;
import com.ondexia.domain.identidad.CuentaAdministradorRepository;
import com.ondexia.domain.identidad.CuentaRepository;
import com.ondexia.domain.identidad.Usuario;
import com.ondexia.domain.identidad.UsuarioEmpresaRepository;
import com.ondexia.domain.identidad.UsuarioRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traduce «un token de Cognito» a «este usuario, sobre esta empresa, con este
 * alcance».
 *
 * <p>Es el unico sitio del sistema donde una cabecera enviada por el cliente se
 * convierte en autorizacion, y por eso concentra todas las comprobaciones.
 */
@Service
public class ResolutorContexto {

    private final UsuarioRepository usuarios;
    private final CuentaRepository cuentas;
    private final UsuarioEmpresaRepository asignaciones;
    private final CuentaAdministradorRepository administradores;

    public ResolutorContexto(UsuarioRepository usuarios, CuentaRepository cuentas,
            UsuarioEmpresaRepository asignaciones, CuentaAdministradorRepository administradores) {
        this.usuarios = usuarios;
        this.cuentas = cuentas;
        this.asignaciones = asignaciones;
        this.administradores = administradores;
    }

    /**
     * @param cognitoSub    el {@code sub} del token ya validado por Spring
     *                      Security. Que la firma sea correcta ya esta
     *                      comprobado cuando se llega aqui
     * @param empresaPedida empresa que el cliente dice tener activa, o
     *                      {@code null}
     */
    @Transactional(readOnly = true)
    public ContextoPeticion resolver(String cognitoSub, UUID empresaPedida, String ip) {
        Usuario usuario = usuarios.findByCognitoSub(cognitoSub)
                .orElseThrow(() -> new NoAutenticadoException(
                        "El token es valido pero no corresponde a ningun usuario registrado"));

        // Se comprueba en cada peticion, no solo al iniciar sesion: desactivar a
        // alguien tiene que cortarle el acceso ya, y Cognito no revoca un token
        // de acceso ya emitido.
        if (!usuario.estaActivo()) {
            throw new AccesoDenegadoException("El usuario esta desactivado");
        }

        Cuenta cuenta = cuentas.findById(usuario.getCuentaId())
                .orElseThrow(() -> new IllegalStateException(
                        "El usuario " + usuario.getId() + " apunta a una cuenta inexistente"));

        if (!cuenta.estaOperativa()) {
            throw new AccesoDenegadoException(
                    "La suscripcion de la cuenta esta " + cuenta.getEstadoSuscripcion());
        }

        boolean esAdministrador =
                administradores.existsByCuentaIdAndUsuarioId(cuenta.getId(), usuario.getId());

        AsignacionEmpresa activa = elegirEmpresaActiva(usuario.getId(), empresaPedida);

        return new ContextoPeticion(
                usuario.getId(),
                cuenta.getId(),
                cuenta.getPermisosVersion(),
                activa == null ? null : activa.empresaId(),
                activa == null ? null : activa.sucursalId(),
                activa == null ? null : activa.rolId(),
                esAdministrador,
                ip);
    }

    /**
     * Decide sobre que empresa opera la peticion.
     *
     * <p>Nota deliberada: <strong>ser administrador de la cuenta no concede
     * acceso a las empresas</strong>. El administrador es quien <em>concede</em>
     * el acceso; para <em>usar</em> una empresa necesita asignarse a si mismo,
     * como cualquiera. Que conceder y ejercer sean actos distintos es lo que
     * deja rastro en {@code usuario_empresa} de quien podia hacer que y desde
     * cuando — y eso es lo que se necesita el dia que haya que explicar quien
     * emitio un comprobante.
     */
    private AsignacionEmpresa elegirEmpresaActiva(UUID usuarioId, UUID empresaPedida) {
        List<AsignacionEmpresa> disponibles = asignaciones.findAsignacionesByUsuarioId(usuarioId);

        if (empresaPedida != null) {
            return disponibles.stream()
                    .filter(a -> a.empresaId().equals(empresaPedida))
                    .findFirst()
                    // Mismo mensaje para «no existe» y «existe pero no es tuya»:
                    // distinguirlos permitiria averiguar que RUC estan dados de
                    // alta en Ondexia probando identificadores.
                    .orElseThrow(() -> new AccesoDenegadoException(
                            "No tienes acceso a la empresa solicitada"));
        }

        // Con una sola empresa no tiene sentido obligar al cliente a elegirla: no
        // hay ambiguedad que resolver ni decision que registrar.
        if (disponibles.size() == 1) {
            return disponibles.get(0);
        }

        // Con varias, o con ninguna, el contexto queda sin empresa. Los endpoints
        // que la necesiten fallaran con un mensaje claro; los que no —el propio
        // /contexto, que es el que devuelve la lista para elegir— siguen
        // funcionando.
        return null;
    }
}
