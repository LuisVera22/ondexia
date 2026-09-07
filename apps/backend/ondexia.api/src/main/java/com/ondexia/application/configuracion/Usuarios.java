package com.ondexia.application.configuracion;

import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.Conflicto;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.identidad.CuentaAdministradorRepositorio;
import com.ondexia.domain.identidad.PermisoRepositorio;
import com.ondexia.domain.identidad.Permisos;
import com.ondexia.domain.identidad.CuentaAdministrador;
import java.util.Set;
import java.util.stream.Collectors;
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
 * la propia persona registrándose en Cognito, y la engancha
 * {@code VincularInvitacion} cuando llega. Mientras eso no ocurra,
 * {@code cognito_sub} es nulo y la pantalla muestra «invitado» en vez de
 * «activo».
 *
 * <p>Ese enganche no existió durante un tiempo, aunque este párrafo ya lo daba
 * por hecho: la fila se creaba, la persona se registraba, y como nadie
 * relacionaba su {@code sub} nuevo con este correo, terminaba en el formulario
 * de empresa nueva creándose una segunda cuenta. Vale la pena recordarlo — una
 * invitación que no se puede aceptar no da ningún error, solo un cliente
 * confundido.
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
    private final PermisoRepositorio permisos;

    public Usuarios(UsuarioRepositorio usuarios, UsuarioEmpresaRepositorio asignaciones,
            RolRepositorio roles, SucursalRepositorio sucursales,
            CuentaAdministradorRepositorio administradores, RegistroDeAuditoria auditoria,
            ProveedorDeContexto contexto, PermisoRepositorio permisos) {
        this.usuarios = usuarios;
        this.asignaciones = asignaciones;
        this.roles = roles;
        this.sucursales = sucursales;
        this.permisos = permisos;
        this.administradores = administradores;
        this.auditoria = auditoria;
        this.contexto = contexto;
    }

    public List<MiembroEmpresa> listar() {
        return asignaciones.listarMiembrosDe(empresaActiva());
    }

    /**
     * Quiénes son Propietarios de la cuenta: {@code cuenta_administrador}, con
     * el nombre que le da el producto (doc 12 §6.1). La pantalla los marca y no
     * les ofrece «cambiar rol»: no tienen rol, tienen la cuenta.
     */
    public Set<UUID> propietarios() {
        return administradores.listarDeCuenta(cuentaActual()).stream()
                .map(CuentaAdministrador::usuarioId)
                .collect(Collectors.toUnmodifiableSet());
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
    public MiembroEmpresa invitar(String email, String nombre, String apellido, UUID rolId,
            UUID sucursalId) {
        var cuentaId = cuentaActual();
        var empresaId = empresaActiva();

        String correo = normalizarCorreo(email);
        var rol = validarRol(rolId);
        exigirQueNoEleve(rol);
        var sucursal = validarSucursal(sucursalId);

        /*
         * El nombre y el apellido solo se usan si la persona es nueva. Si ya
         * existe en la cuenta se reutiliza su fila tal cual: el administrador la
         * está añadiendo a OTRA empresa, no rebautizándola. Dejar que estos
         * campos pisaran los suyos permitiría cambiarle el nombre a alguien
         * desde una empresa en la que ni siquiera trabaja todavía.
         */
        var usuario = usuarios.buscarPorEmailEnCuenta(cuentaId, correo)
                .orElseGet(() -> usuarios.guardar(new Usuario(
                        UUID.randomUUID(), cuentaId, correo,
                        exigirTexto(nombre, "nombre_requerido", "El nombre es obligatorio."),
                        exigirTexto(apellido, "apellido_requerido",
                                "El apellido es obligatorio."))));

        asignaciones.buscarAsignacion(usuario.id(), empresaId).ifPresent(existente -> {
            throw new Conflicto(
                    "usuario_ya_asignado",
                    correo + " ya tiene acceso a esta empresa. Edita su rol en vez de "
                            + "volver a agregarlo.",
                    "email");
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
        impedirQueSeAscienda(asignacion);
        var antes = buscarMiembro(asignacionId);

        var rol = validarRol(rolId);
        exigirQueNoEleve(rol);
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
    /**
     * Nadie se cambia su propio rol (hallazgo M1).
     *
     * <h2>Lo que se podía hacer</h2>
     *
     * <p>Quien tuviera {@code configuracion.usuario:editar} —un permiso que se le
     * da a un supervisor para que gestione a su equipo— podía llamar a
     * {@code reasignar} sobre <strong>su propia</strong> asignación y ponerse el
     * rol ADMINISTRADOR. Es decir: cualquiera que pudiera administrar usuarios
     * podía convertirse en administrador de la empresa, que es un conjunto de
     * permisos estrictamente mayor.
     *
     * <p>Las otras dos operaciones sí se protegían: {@code retirar} y
     * {@code cambiarEstado} comprueban «yo mismo» desde el principio. Faltaba
     * justamente la que sube de nivel, que es la que importa — retirarse el
     * acceso a uno mismo es un incordio; ascenderse es una escalada.
     *
     * <h2>Por qué prohibir y no comprobar el nivel</h2>
     *
     * <p>La alternativa era una regla de no elevación: que el rol destino sea un
     * subconjunto de los permisos de quien actúa. Es más flexible y más difícil
     * de sostener — hay que compararla en cada cambio del catálogo de permisos, y
     * el día que dos roles se solapen parcialmente hay que decidir qué significa
     * «subconjunto».
     *
     * <p>Prohibir tocarse a sí mismo no tiene ese problema y no le quita nada a
     * nadie: cambiar de rol es algo que hace otra persona. Es la misma regla que
     * ya seguían las otras dos operaciones.
     */
    private void impedirQueSeAscienda(UsuarioEmpresa asignacion) {
        if (asignacion.usuarioId().equals(usuarioActual())) {
            throw new ReglaDeNegocioViolada(
                    "no_puedes_cambiarte_el_rol",
                    "No puedes cambiar tu propio rol ni tu alcance. Pídeselo a otro "
                            + "administrador.");
        }
    }

    /**
     * Un rol solo puede conceder permisos que su portador tiene (doc 12 §6.3).
     *
     * <p>Es la variante de M1 que quedó a medias: prohibir tocarse a sí mismo
     * cerraba dos de las tres vías, pero quien tenía {@code usuario:registrar}
     * podía invitar a una segunda identidad suya como Administrador. Con la regla
     * de cobertura la vía se cierra sin depender de a quién se invita.
     *
     * <p>El Propietario —administrador de la cuenta— está fuera de la matriz y
     * puede conceder cualquier rol: alguien tiene que poder nombrar al primer
     * Administrador. Lo fija {@code UsuariosIT.nadieConcedeLoQueNoTiene}.
     */
    private void exigirQueNoEleve(Rol rol) {
        var actual = contexto.obligatorio();
        if (actual.esAdministradorCuenta()) {
            return;
        }
        var propios = actual.rolId() == null ? Permisos.ninguno()
                : permisos.permisosDelRol(actual.rolId());
        if (!propios.cubre(permisos.permisosDelRol(rol.id()))) {
            throw new ReglaDeNegocioViolada(
                    "rol_excede_tus_permisos",
                    "El rol " + rol.nombre() + " concede permisos que tú no tienes. Solo se "
                            + "puede asignar un rol con permisos iguales o menores a los propios.");
        }
    }

    private void impedirQueSeCierrePorDentro(Usuario usuario) {
        if (usuario.id().equals(usuarioActual())) {
            throw new ReglaDeNegocioViolada(
                    "no_puedes_desactivarte",
                    "No puedes desactivarte a ti mismo. Pídeselo a otro administrador.");
        }

        /*
         * Con la cuenta bloqueada y contando solo ACTIVOS (hallazgo M6).
         *
         * Antes contaba filas de cuenta_administrador sin bloquear nada: dos
         * administradores desactivandose el uno al otro a la vez leian «dos» y
         * los dos pasaban, y ademas un administrador ya desactivado seguia
         * contando como salida. El disparador de la base protege los BORRADOS de
         * cuenta_administrador, no `usuario.activo`, asi que esta era la unica
         * defensa y tenia una carrera.
         */
        var cuentaId = cuentaActual();
        if (administradores.esAdministrador(cuentaId, usuario.id())
                && administradores.contarActivosEnCuentaBloqueando(cuentaId) <= 1) {
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
