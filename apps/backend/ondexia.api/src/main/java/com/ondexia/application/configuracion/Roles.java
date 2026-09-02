package com.ondexia.application.configuracion;

import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.Conflicto;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.identidad.CuentaRepositorio;
import com.ondexia.domain.identidad.NivelPermiso;
import com.ondexia.domain.identidad.Permiso;
import com.ondexia.domain.identidad.PermisoRepositorio;
import com.ondexia.domain.identidad.Rol;
import com.ondexia.domain.identidad.RolRepositorio;
import com.ondexia.domain.identidad.UsuarioEmpresaRepositorio;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roles a medida de la cuenta.
 *
 * <h2>Los predefinidos no se tocan, y no es una limitación técnica</h2>
 *
 * <p>Administrador, Vendedor, Almacenero y Contador tienen {@code cuenta_id}
 * nulo y los comparten todas las cuentas. Si un cliente pudiera editar
 * «Vendedor», la palabra dejaría de significar lo mismo entre clientes y
 * cualquier consulta de soporte empezaría por averiguar qué quiere decir aquí.
 * Lo que sí puede hacer es duplicarlo y ajustar la copia, que cubre el mismo
 * caso sin el efecto colateral.
 *
 * <h2>Cada cambio invalida la caché de permisos</h2>
 *
 * <p>{@link com.ondexia.application.identidad.PermisosEfectivos} cachea los
 * permisos por rol dentro del contenedor de Lambda, y compara contra
 * {@code permisos_version} de la cuenta para saber si lo que tiene sirve. Si un
 * cambio aquí no incrementara esa versión, <strong>un permiso revocado seguiría
 * concediéndose</strong> hasta que el contenedor se reciclara — que puede ser
 * dentro de horas, y no en todos a la vez.
 *
 * <p>Por eso el incremento acompaña a toda operación que altere permisos, y no
 * solo a la que los edita: duplicar crea un rol con permisos, y eliminar los
 * quita.
 */
@Service
public class Roles {

    private final RolRepositorio roles;
    private final PermisoRepositorio permisos;
    private final CuentaRepositorio cuentas;
    private final UsuarioEmpresaRepositorio asignaciones;
    private final RegistroDeAuditoria auditoria;
    private final ProveedorDeContexto contexto;

    public Roles(RolRepositorio roles, PermisoRepositorio permisos, CuentaRepositorio cuentas,
            RegistroDeAuditoria auditoria, ProveedorDeContexto contexto,
            UsuarioEmpresaRepositorio asignaciones) {
        this.roles = roles;
        this.permisos = permisos;
        this.cuentas = cuentas;
        this.auditoria = auditoria;
        this.contexto = contexto;
        this.asignaciones = asignaciones;
    }

    /**
     * @param enUso si alguien lo tiene asignado. La pantalla lo necesita para no
     *              ofrecer un botón de eliminar que acabaría en un 409
     */
    public record RolConDetalle(Rol rol, int cantidadPermisos, boolean enUso) {
    }

    public List<RolConDetalle> listar() {
        return roles.listarDisponibles(cuentaActual()).stream()
                .map(rol -> new RolConDetalle(
                        rol,
                        roles.permisosDe(rol.id()).size(),
                        // Un rol del sistema no se puede borrar nunca, así que no
                        // hace falta preguntar a la base por él.
                        !rol.esDelSistema() && roles.estaAsignadoAAlguien(rol.id())))
                .toList();
    }

    /** Un submódulo con las funciones que cuelgan de él. */
    public record SubmoduloDelCatalogo(Permiso submodulo, List<Permiso> funciones) {
    }

    /** Un módulo con sus submódulos. La forma en que se pinta la matriz. */
    public record ModuloDelCatalogo(Permiso modulo, List<SubmoduloDelCatalogo> submodulos) {
    }

    /**
     * El catálogo entero, ya como árbol de tres niveles.
     *
     * <p>Se arma aquí y no en el cliente porque la relación padre-hijo la define
     * el código —{@code almacen.producto} cuelga de {@code almacen}— y esa regla
     * ya vive en {@link Permiso}. Reconstruirla en TypeScript partiendo cadenas
     * sería una segunda implementación de la misma regla, que es como acaban
     * divergiendo.
     */
    public List<ModuloDelCatalogo> catalogoDePermisos() {
        var catalogo = permisos.listarCatalogo();

        var funcionesPorSubmodulo = catalogo.stream()
                .filter(p -> p.nivel() == NivelPermiso.FUNCION)
                .collect(Collectors.groupingBy(Permiso::modulo));

        var submodulosPorModulo = catalogo.stream()
                .filter(p -> p.nivel() == NivelPermiso.SUBMODULO)
                .collect(Collectors.groupingBy(p -> Permiso.moduloDe(p.modulo())));

        return catalogo.stream()
                .filter(p -> p.nivel() == NivelPermiso.MODULO)
                .map(modulo -> new ModuloDelCatalogo(
                        modulo,
                        submodulosPorModulo.getOrDefault(modulo.modulo(), List.of()).stream()
                                .map(sub -> new SubmoduloDelCatalogo(
                                        sub,
                                        funcionesPorSubmodulo.getOrDefault(sub.modulo(), List.of())))
                                .toList()))
                .toList();
    }

    public Set<UUID> permisosDe(UUID rolId) {
        exigirVisible(rolId);
        return roles.permisosDe(rolId);
    }

    /**
     * Duplica un rol —del sistema o propio— como rol de esta cuenta.
     *
     * <p>La copia arrastra los permisos del original. Empezar en blanco sería más
     * simple de programar y peor de usar: quien duplica «Vendedor» quiere
     * «Vendedor y además esto», no reconstruir cuarenta casillas.
     */
    @Transactional
    public Rol duplicar(UUID rolOrigenId, String nombre) {
        var origen = exigirVisible(rolOrigenId);
        var cuentaId = cuentaActual();

        String nombreLimpio = exigirTexto(nombre, "nombre_requerido",
                "El rol nuevo necesita un nombre.");

        var copia = origen.duplicarPara(
                UUID.randomUUID(), cuentaId, codigoLibre(cuentaId, nombreLimpio), nombreLimpio);

        var guardado = roles.guardar(copia);
        roles.reemplazarPermisos(guardado.id(), roles.permisosDe(origen.id()));
        cuentas.invalidarCachePermisos(cuentaId);

        auditoria.registrarCreacion("rol", guardado.id(),
                new Instantanea(guardado.codigo(), guardado.nombre(),
                        roles.permisosDe(guardado.id()).size()));
        return guardado;
    }

    @Transactional
    public Rol renombrar(UUID rolId, String nombre, String descripcion) {
        var rol = exigirPropio(rolId);
        var antes = new Instantanea(rol.codigo(), rol.nombre(), roles.permisosDe(rolId).size());

        // renombrar() vuelve a comprobar que no sea del sistema. Es redundante a
        // propósito: la regla vive en el agregado, y exigirPropio solo da un
        // mensaje mejor antes de llegar a ella.
        rol.renombrar(exigirTexto(nombre, "nombre_requerido", "El rol necesita un nombre."),
                descripcion);

        var guardado = roles.guardar(rol);
        auditoria.registrarActualizacion("rol", rolId, antes,
                new Instantanea(guardado.codigo(), guardado.nombre(),
                        roles.permisosDe(rolId).size()));
        return guardado;
    }

    /**
     * Deja el rol exactamente con estos permisos, <strong>podados</strong>.
     *
     * @param permisoIds estado final de la matriz, no un incremento. Vacío es
     *                   válido: un rol sin ningún permiso no puede nada, que es
     *                   una configuración legítima mientras se compone
     */
    @Transactional
    public Rol cambiarPermisos(UUID rolId, Set<UUID> permisoIds) {
        var rol = exigirPropio(rolId);
        impedirTocarSuPropioRol(rolId);
        var antes = new Instantanea(rol.codigo(), rol.nombre(), roles.permisosDe(rolId).size());

        var catalogo = permisos.listarCatalogo();
        var porId = catalogo.stream().collect(Collectors.toMap(Permiso::id, p -> p));

        var desconocidos = permisoIds.stream().filter(id -> !porId.containsKey(id)).toList();
        if (!desconocidos.isEmpty()) {
            throw new ReglaDeNegocioViolada(
                    "permiso_desconocido",
                    "Hay " + desconocidos.size() + " permiso(s) que no están en el catálogo.");
        }

        var podados = podar(permisoIds, porId);

        roles.reemplazarPermisos(rolId, podados);
        cuentas.invalidarCachePermisos(cuentaActual());

        auditoria.registrarActualizacion("rol", rolId, antes,
                new Instantanea(rol.codigo(), rol.nombre(), podados.size()));
        return rol;
    }

    /**
     * Quita lo que no tenga a sus padres concedidos.
     *
     * <h2>Por qué poda en vez de completar</h2>
     *
     * <p>La alternativa evidente sería la contraria: si marcas una función,
     * conceder de paso su submódulo y su módulo. Sería más cómodo y rompería lo
     * que el modelo promete — desmarcar «Almacén» dejando las casillas de dentro
     * marcadas volvería a encender el módulo al guardar, y entonces el
     * interruptor de área no apagaría nada.
     *
     * <p>Podando, <strong>lo guardado coincide siempre con lo que está en
     * vigor</strong>. No existe el estado en que la matriz muestra una casilla
     * marcada y la autorización dice que no, que es el modo de fallo caro de este
     * diseño: una denegación que no se explica mirando donde uno mira.
     *
     * <p>La pantalla hace lo mismo en vivo —pinta en gris lo que cuelga de un
     * módulo apagado— así que en el uso normal esto no llega a quitar nada. Está
     * para lo demás: una petición hecha a mano, un cliente antiguo, un error.
     */
    private static Set<UUID> podar(Set<UUID> solicitados, Map<UUID, Permiso> porId) {
        var codigosConcedidos = solicitados.stream()
                .map(id -> porId.get(id).codigo())
                .collect(Collectors.toSet());

        // Dos pasadas y no una: una función depende de su submódulo, que a su vez
        // depende de su módulo. Filtrar de golpe dejaría pasar la función cuyo
        // submódulo está marcado pero cuyo módulo no.
        var submodulosVivos = solicitados.stream()
                .map(porId::get)
                .filter(p -> p.nivel() == NivelPermiso.SUBMODULO)
                .filter(p -> codigosConcedidos.contains(p.codigoDelPadre()))
                .map(Permiso::codigo)
                .collect(Collectors.toSet());

        return solicitados.stream()
                .filter(id -> {
                    var permiso = porId.get(id);
                    return switch (permiso.nivel()) {
                        case MODULO -> true;
                        case SUBMODULO -> submodulosVivos.contains(permiso.codigo());
                        case FUNCION -> submodulosVivos.contains(permiso.codigoDelPadre());
                    };
                })
                .collect(Collectors.toSet());
    }

    /**
     * Elimina un rol propio. Aquí sí se borra —no se desactiva— porque un rol no
     * aparece en ningún documento emitido: es un permiso vigente, y uno retirado
     * no tiene por qué seguir existiendo.
     */
    @Transactional
    public void eliminar(UUID rolId) {
        var rol = exigirPropio(rolId);

        if (roles.estaAsignadoAAlguien(rolId)) {
            throw new Conflicto(
                    "rol_en_uso",
                    "Hay usuarios con el rol '" + rol.nombre() + "'. Cámbiales el rol antes "
                            + "de eliminarlo.");
        }

        var antes = new Instantanea(rol.codigo(), rol.nombre(), roles.permisosDe(rolId).size());
        roles.eliminar(rolId);
        cuentas.invalidarCachePermisos(cuentaActual());

        auditoria.registrar("rol", rolId, "ELIMINAR", antes, null);
    }

    // ── Comprobaciones ──────────────────────────────────────────────────────

    /**
     * Visible = del sistema o de esta cuenta.
     *
     * <p>La tabla {@code rol} no está bajo RLS: hay que poder leer los
     * predefinidos, que no pertenecen a ninguna cuenta. El precio de esa
     * excepción es este método — sin él, pasar el identificador del rol a medida
     * de otro cliente lo dejaría leerlo.
     */
    private Rol exigirVisible(UUID rolId) {
        var rol = roles.buscarPorId(rolId)
                .orElseThrow(() -> RecursoNoEncontrado.con("rol_no_encontrado",
                        "El rol no existe."));

        if (!rol.esDelSistema() && !rol.cuentaId().equals(cuentaActual())) {
            // «No existe» y no «prohibido»: decir prohibido confirmaría que ese
            // identificador es de otra cuenta.
            throw RecursoNoEncontrado.con("rol_no_encontrado", "El rol no existe.");
        }
        return rol;
    }

    private Rol exigirPropio(UUID rolId) {
        var rol = exigirVisible(rolId);
        if (rol.esDelSistema()) {
            throw new Conflicto(
                    "rol_del_sistema",
                    "'" + rol.nombre() + "' es un rol predefinido y no se modifica. "
                            + "Duplícalo y ajusta la copia.");
        }
        return rol;
    }

    /**
     * Deriva un código a partir del nombre y le añade un sufijo si ya existe.
     *
     * <p>El código no lo escribe el usuario: es un identificador interno que
     * además tiene índice único por cuenta, y pedírselo solo serviría para que
     * choque. Se deriva del nombre para que la bitácora se pueda leer sin
     * consultar la tabla de roles.
     */
    private String codigoLibre(UUID cuentaId, String nombre) {
        String base = nombre.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        if (base.isEmpty()) {
            base = "ROL";
        }
        if (base.length() > 50) {
            base = base.substring(0, 50);
        }

        if (!roles.existeCodigoEnCuenta(cuentaId, base)) {
            return base;
        }
        for (int sufijo = 2; sufijo < 1000; sufijo++) {
            String candidato = base + "_" + sufijo;
            if (!roles.existeCodigoEnCuenta(cuentaId, candidato)) {
                return candidato;
            }
        }
        throw new Conflicto("codigo_de_rol_agotado",
                "Ya hay demasiados roles con ese nombre. Usa otro.");
    }

    private static String exigirTexto(String valor, String codigo, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaDeNegocioViolada(codigo, mensaje);
        }
        return valor.trim();
    }

    /**
     * Nadie edita los permisos del rol que él mismo tiene (hallazgo M1).
     *
     * <h2>Lo que se podía hacer</h2>
     *
     * <p>Quien tuviera {@code configuracion.rol:editar} podía abrir <em>su
     * propio</em> rol y marcarse las casillas que le faltaran. No hacía falta
     * cambiarse de rol —eso lo cierra {@code Usuarios.reasignar}—: bastaba con
     * ensanchar el que ya tenía, y el efecto es el mismo pero además silencioso,
     * porque no aparece ningún cambio de asignación en la bitácora de usuarios.
     *
     * <p>Es la variante de M1 que menos se ve, y la más fácil de ejecutar: la
     * pantalla de roles ya está ahí y no avisa de nada.
     *
     * <h2>Por qué mira todas sus empresas y no solo la activa</h2>
     *
     * <p>Los roles pertenecen a la CUENTA, no a la empresa: el mismo rol se usa
     * en las empresas que la cuenta tenga. Comprobar solo la empresa activa
     * dejaría abierto cambiar de empresa y editar desde allí el rol que se usa
     * aquí.
     */
    private void impedirTocarSuPropioRol(UUID rolId) {
        boolean esElSuyo = asignaciones
                .listarAsignacionesDe(contexto.obligatorio().usuarioId()).stream()
                .anyMatch(asignacion -> rolId.equals(asignacion.rolId()));

        if (esElSuyo) {
            throw new ReglaDeNegocioViolada(
                    "no_puedes_editar_tu_rol",
                    "No puedes cambiar los permisos del rol que tú mismo tienes. "
                            + "Duplícalo, ajusta la copia y pide que te la asignen.");
        }
    }

    private UUID cuentaActual() {
        return contexto.obligatorio().cuentaId();
    }

    private record Instantanea(String codigo, String nombre, int cantidadPermisos) {
    }
}
