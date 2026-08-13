package com.ondexia.application.configuracion;

import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.Conflicto;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.identidad.CuentaAdministradorRepositorio;
import com.ondexia.domain.identidad.MiembroEmpresa;
import com.ondexia.domain.identidad.Rol;
import com.ondexia.domain.identidad.RolRepositorio;
import com.ondexia.domain.identidad.SucursalRepositorio;
import com.ondexia.domain.identidad.Usuario;
import com.ondexia.domain.identidad.UsuarioEmpresa;
import com.ondexia.domain.identidad.UsuarioEmpresaRepositorio;
import com.ondexia.domain.identidad.UsuarioRepositorio;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quién entra en la empresa activa, con qué rol y hasta dónde.
 *
 * <h2>El alta no crea la cuenta de acceso, y eso se nota</h2>
 *
 * <p>Aquí se crea la fila {@code usuario} y su asignación; la identidad la crea
 * la propia persona registrándose en Cognito, y se vincula sola en su primer
 * ingreso. Mientras eso no ocurra, {@code cognito_sub} es nulo y la pantalla
 * muestra «invitado» en vez de «activo».
 *
 * <p>No es una simplificación: la Lambda no tiene salida a internet (DTE §4.8) y
 * llamar a la API de Cognito exigiría un endpoint de interfaz a ~7.30 USD/mes,
 * más caro que la NAT que se evitó (plan 07, Entrega 4). El modelo ya contaba con
 * ello — {@code cognitoSub} admite nulo desde la V1.
 *
 * <h2>Dos puertas que no se pueden cerrar por dentro</h2>
 *
 * <p>Nadie puede desactivarse a sí mismo ni retirarse el propio acceso, y el
 * último administrador de la cuenta no se puede desactivar. Sin esas tres
 * comprobaciones, un despiste deja la cuenta sin nadie capaz de arreglarla desde
 * dentro, y la única salida sería soporte tocando la base a mano.
 */
@Service
public class Usuarios {

    private final UsuarioRepositorio usuarios;
    private final UsuarioEmpresaRepositorio asignaciones;
    private final RolRepositorio roles;
    private final SucursalRepositorio sucursales;
    private final CuentaAdministradorRepositorio administradores;
    private final RegistroDeAuditoria auditoria;
    private final ProveedorDeContexto contexto;

    public Usuarios(UsuarioRepositorio usuarios, UsuarioEmpresaRepositorio asignaciones,
            RolRepositorio roles, SucursalRepositorio sucursales,
            CuentaAdministradorRepositorio administradores, RegistroDeAuditoria auditoria,
            ProveedorDeContexto contexto) {
        this.usuarios = usuarios;
        this.asignaciones = asignaciones;
        this.roles = roles;
        this.sucursales = sucursales;
        this.administradores = administradores;
        this.auditoria = auditoria;
        this.contexto = contexto;
    }

    public List<MiembroEmpresa> listar() {
        return asignaciones.listarMiembrosDe(empresaActiva());
    }

    /** Los roles que esta cuenta puede asignar: los predefinidos más los suyos. */
    public List<Rol> rolesAsignables() {
        return roles.listarDisponibles(cuentaActual());
    }

    /**
     * Da de alta a alguien en la empresa activa.
     *
     * <p>Si el correo ya existe en la cuenta se reutiliza esa persona en vez de
     * crear otra. Es lo correcto y además lo único posible: el índice único de
     * {@code usuario} es {@code (cuenta_id, lower(email))}, y dos filas para la
     * misma persona harían que cuál gana al resolver el contexto fuera cuestión
     * de suerte.
     *
     * @param sucursalId {@code null} = alcanza todos los establecimientos
     */
    @Transactional
    public MiembroEmpresa invitar(String email, String nombre, UUID rolId, UUID sucursalId) {
        var cuentaId = cuentaActual();
        var empresaId = empresaActiva();

        String correo = normalizarCorreo(email);
        var rol = validarRol(rolId);
        var sucursal = validarSucursal(sucursalId);

        var usuario = usuarios.buscarPorEmailEnCuenta(cuentaId, correo)
                .orElseGet(() -> usuarios.guardar(new Usuario(
                        UUID.randomUUID(), cuentaId, correo,
                        exigirTexto(nombre, "nombre_requerido", "El nombre es obligatorio."))));

        asignaciones.buscarAsignacion(usuario.id(), empresaId).ifPresent(existente -> {
            throw new Conflicto(
                    "usuario_ya_asignado",
                    correo + " ya tiene acceso a esta empresa. Edita su rol en vez de "
                            + "volver a agregarlo.");
        });

        var asignacion = asignaciones.guardar(new UsuarioEmpresa(
                UUID.randomUUID(), usuario.id(), empresaId, rol.id(), sucursal));

        auditoria.registrarCreacion("usuario_empresa", asignacion.id(),
                new Instantanea(correo, rol.codigo(), sucursal, usuario.estaActivo()));

        return buscarMiembro(asignacion.id());
    }

    /** Cambia el rol o el alcance de alguien que ya está en la empresa. */
    @Transactional
    public MiembroEmpresa reasignar(UUID asignacionId, UUID rolId, UUID sucursalId) {
        var asignacion = exigirAsignacionDeEstaEmpresa(asignacionId);
        var antes = buscarMiembro(asignacionId);

        var rol = validarRol(rolId);
        asignacion.reasignar(rol.id(), validarSucursal(sucursalId));
        asignaciones.guardar(asignacion);

        var despues = buscarMiembro(asignacionId);
        auditoria.registrarActualizacion("usuario_empresa", asignacionId,
                new Instantanea(antes.email(), antes.rolCodigo(), antes.sucursalId(),
                        antes.activo()),
                new Instantanea(despues.email(), despues.rolCodigo(), despues.sucursalId(),
                        despues.activo()));
        return despues;
    }

    /**
     * Activa o desactiva a la persona <strong>en toda la cuenta</strong>, no solo
     * en esta empresa. Es la única forma de revocar el acceso antes de que caduque
     * su token: Cognito no revoca un token de acceso ya emitido.
     */
    @Transactional
    public MiembroEmpresa cambiarEstado(UUID asignacionId, boolean activo) {
        var asignacion = exigirAsignacionDeEstaEmpresa(asignacionId);
        var usuario = usuarios.buscarPorId(asignacion.usuarioId())
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "usuario_no_encontrado", "El usuario no existe."));

        if (!activo) {
            impedirQueSeCierrePorDentro(usuario);
        }

        if (usuario.estaActivo() == activo) {
            return buscarMiembro(asignacionId); // Idempotente.
        }

        var antes = new Instantanea(usuario.email(), null, null, usuario.estaActivo());
        if (activo) {
            usuario.activar();
        } else {
            usuario.desactivar();
        }
        usuarios.guardar(usuario);

        auditoria.registrar("usuario", usuario.id(), activo ? "ACTIVAR" : "DESACTIVAR",
                antes, new Instantanea(usuario.email(), null, null, usuario.estaActivo()));

        return buscarMiembro(asignacionId);
    }

    /**
     * Retira el acceso a esta empresa. La persona sigue existiendo en la cuenta y
     * conserva las demás empresas que tuviera.
     */
    @Transactional
    public void retirar(UUID asignacionId) {
        var asignacion = exigirAsignacionDeEstaEmpresa(asignacionId);

        if (asignacion.usuarioId().equals(usuarioActual())) {
            throw new ReglaDeNegocioViolada(
                    "no_puedes_retirarte",
                    "No puedes retirarte el acceso a ti mismo. Pídeselo a otro administrador.");
        }

        var miembro = buscarMiembro(asignacionId);
        asignaciones.eliminar(asignacionId);

        auditoria.registrar("usuario_empresa", asignacionId, "RETIRAR",
                new Instantanea(miembro.email(), miembro.rolCodigo(), miembro.sucursalId(),
                        miembro.activo()),
                null);
    }

    // ── Comprobaciones ──────────────────────────────────────────────────────

    /**
     * Las dos formas de quedarse fuera de la propia cuenta.
     *
     * <p>La segunda no la puede defender la base: el disparador de la V1 protege
     * la tabla {@code cuenta_administrador}, y desactivar al usuario no borra su
     * fila de administrador — la deja intacta y sin poder entrar, que es
     * exactamente el estado que el disparador existía para impedir.
     */
    private void impedirQueSeCierrePorDentro(Usuario usuario) {
        if (usuario.id().equals(usuarioActual())) {
            throw new ReglaDeNegocioViolada(
                    "no_puedes_desactivarte",
                    "No puedes desactivarte a ti mismo. Pídeselo a otro administrador.");
        }

        var cuentaId = cuentaActual();
        if (administradores.esAdministrador(cuentaId, usuario.id())
                && administradores.contarEnCuenta(cuentaId) <= 1) {
            throw new ReglaDeNegocioViolada(
                    "ultimo_administrador",
                    "Es el único administrador de la cuenta. Nombra a otro antes de "
                            + "desactivarlo, o nadie podrá volver a administrarla.");
        }
    }

    /**
     * El rol tiene que ser de esta cuenta o del sistema.
     *
     * <p>Sin esto se podría asignar el rol a medida de otro cliente pasando su
     * identificador: la tabla {@code rol} no está bajo RLS —hay que poder leer los
     * predefinidos, que no son de nadie— así que el filtro es del código.
     */
    private Rol validarRol(UUID rolId) {
        if (rolId == null) {
            throw new ReglaDeNegocioViolada("rol_requerido", "Hay que indicar un rol.");
        }
        return roles.listarDisponibles(cuentaActual()).stream()
                .filter(r -> r.id().equals(rolId))
                .findFirst()
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "rol_invalido", "El rol indicado no está disponible para esta cuenta."));
    }

    /** {@code null} es válido y significa «todos los establecimientos». */
    private UUID validarSucursal(UUID sucursalId) {
        if (sucursalId == null) {
            return null;
        }
        return sucursales.buscarPorId(sucursalId)
                .filter(s -> s.empresaId().equals(empresaActiva()))
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "establecimiento_invalido",
                        "El establecimiento indicado no existe en esta empresa."))
                .id();
    }

    /**
     * {@code usuario_empresa} queda fuera de RLS —es de las tablas que hay que
     * leer <em>para saber</em> cuál es la empresa—, así que el filtro es de aquí.
     */
    private UsuarioEmpresa exigirAsignacionDeEstaEmpresa(UUID asignacionId) {
        var asignacion = asignaciones.buscarPorId(asignacionId)
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "asignacion_no_encontrada", "El usuario no está asignado a esta empresa."));

        if (!asignacion.empresaId().equals(empresaActiva())) {
            throw RecursoNoEncontrado.con(
                    "asignacion_no_encontrada", "El usuario no está asignado a esta empresa.");
        }
        return asignacion;
    }

    private MiembroEmpresa buscarMiembro(UUID asignacionId) {
        return asignaciones.listarMiembrosDe(empresaActiva()).stream()
                .filter(m -> m.asignacionId().equals(asignacionId))
                .findFirst()
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "asignacion_no_encontrada", "El usuario no está asignado a esta empresa."));
    }

    private static String normalizarCorreo(String email) {
        if (email == null || email.isBlank()) {
            throw new ReglaDeNegocioViolada("correo_requerido", "El correo es obligatorio.");
        }
        // En minúsculas porque el índice único es sobre lower(email): dos
        // usuarios con el mismo correo escrito distinto son la misma persona.
        return email.trim().toLowerCase();
    }

    private static String exigirTexto(String valor, String codigo, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaDeNegocioViolada(codigo, mensaje);
        }
        return valor.trim();
    }

    private UUID empresaActiva() {
        return contexto.obligatorio().empresaActivaObligatoria();
    }

    private UUID cuentaActual() {
        return contexto.obligatorio().cuentaId();
    }

    private UUID usuarioActual() {
        return contexto.obligatorio().usuarioId();
    }

    private record Instantanea(String email, String rolCodigo, UUID sucursalId, boolean activo) {
    }
}
