package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.application.configuracion.Usuarios;
import com.ondexia.domain.comun.ContextoOperacion;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.identidad.RolRepositorio;
import com.ondexia.infrastructure.seguridad.ContextoDePrueba;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Entrega 4: usuarios y asignaciones.
 *
 * <p>Lo que de verdad se prueba aquí no es el CRUD sino las tres puertas que no
 * se pueden cerrar por dentro. Un sistema multiempresa en el que un
 * administrador puede desactivarse a sí mismo o retirarse el acceso queda sin
 * nadie capaz de arreglarlo, y la única salida es soporte tocando la base.
 */
class UsuariosIT extends PruebaIntegracion {

    private static final String USUARIOS = "/api/v1/configuracion/usuarios";
    private static final UUID CUENTA = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID SUCURSAL_MATRIZ =
            UUID.fromString("00000000-0000-4000-8000-000000000020");

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private RolRepositorio roles;

    @Autowired
    private com.ondexia.application.configuracion.Roles rolesUso;

    @Autowired
    private com.ondexia.domain.identidad.PermisoRepositorio permisos;

    @Autowired
    private org.springframework.jdbc.core.simple.JdbcClient jdbc;

    private UUID permiso(String modulo, String accion) {
        return permisos.listarCatalogo().stream()
                .filter(p -> p.modulo().equals(modulo) && p.accion().equals(accion))
                .map(com.ondexia.domain.identidad.Permiso::id)
                .findFirst()
                .orElseThrow();
    }

    @AfterEach
    void limpiar() {
        ContextoDePrueba.limpiar();
    }

    private void comoDemo() {
        ContextoDePrueba.comoUsuarioDe(
                UUID.fromString(USUARIO_DEMO), CUENTA, UUID.fromString(EMPRESA_ADMINISTRADA));
    }

    private UUID rol(String codigo) {
        return codigo.equals("ADMINISTRADOR")
                ? roles.buscarPredefinido(codigo).orElseThrow().id()
                : rolDeLaCuenta(codigo).id();
    }

    // ── Alta ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Quien se da de alta figura como invitado hasta su primer ingreso")
    void elAltaDejaAlUsuarioInvitado() throws Exception {
        String cuerpo = """
                {"email":"nueva.persona@ejemplo.com","nombre":"Nueva","apellido":"Persona",
                 "rolId":"%s","sucursalId":"%s"}""".formatted(rol("VENDEDOR"), SUCURSAL_MATRIZ);

        mockMvc.perform(post(USUARIOS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nueva.persona@ejemplo.com"))
                // El listado enseña el nombre unido: se dan de alta por separado
                // porque partirlos después obligaría a adivinar dónde acaba cada
                // uno, pero la tabla no gana nada con dos columnas.
                .andExpect(jsonPath("$.nombre").value("Nueva Persona"))
                // Lo que explica que alguien «no pueda entrar» estando activo: la
                // fila existe, la identidad de Cognito todavía no.
                .andExpect(jsonPath("$.invitado").value(true))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.rolNombre").value("Vendedor"))
                .andExpect(jsonPath("$.todosLosEstablecimientos").value(false));
    }

    @Test
    @DisplayName("El correo se normaliza y no crea dos personas")
    void elCorreoNoDistingueMayusculas() {
        /*
         * El índice único de `usuario` es lower(email), global desde la V24. Si
         * el alta no normalizara, el segundo intento chocaría contra el índice
         * con un error de integridad en vez de reutilizar a la persona — y con
         * dos filas para el mismo correo, cuál gana al resolver el contexto sería
         * cuestión de suerte.
         */
        comoDemo();
        var primera = usuarios.invitar("Repetido@Ejemplo.com", "Con Mayúsculas", "Apellido",
                rol("ALMACENERO"), null);

        assertThat(primera.email()).isEqualTo("repetido@ejemplo.com");

        // El mismo correo en otra empresa de la cuenta reutiliza a la persona.
        ContextoDePrueba.comoUsuarioDe(
                UUID.fromString(USUARIO_DEMO), CUENTA, UUID.fromString(EMPRESA_COMO_VENDEDOR));
        var segunda = usuarios.invitar("REPETIDO@ejemplo.com", "Da igual el nombre", "Apellido",
                rol("VENDEDOR"), null);

        assertThat(segunda.usuarioId())
                .as("es la misma persona, no una copia")
                .isEqualTo(primera.usuarioId());
    }

    @Test
    @DisplayName("Un correo pertenece a una sola suscripción")
    void elCorreoNoSeRepiteEntreSuscripciones() {
        // Decisión del propietario del 2026-09-08: una persona vive en una sola
        // suscripción. Dentro de ella puede tener tantas empresas como quiera
        // —eso lo cubre la prueba de arriba—; lo que no puede es trabajar para
        // dos clientes con el mismo correo.
        UUID otraCuenta = UUID.randomUUID();
        // Correo propio de esta prueba. Las de integración comparten base, y un
        // correo ahora es único en TODO el sistema: reutilizar uno de otra
        // prueba la rompería a distancia.
        String correoAjeno = "de-otro-cliente-" + UUID.randomUUID() + "@ejemplo.com";
        jdbc.sql("""
                        insert into cuenta (id, nombre, plan, estado_suscripcion, permisos_version)
                        values (?, 'Otro cliente', 'ESENCIAL', 'EN_PRUEBA', 1)
                        """)
                .param(otraCuenta).update();
        jdbc.sql("""
                        insert into usuario (id, cuenta_id, email, nombre, activo)
                        values (?, ?, ?, 'Persona de otro cliente', true)
                        """)
                .param(UUID.randomUUID()).param(otraCuenta).param(correoAjeno).update();

        comoDemo();

        // En mayúsculas a propósito: la unicidad no distingue caja.
        assertThatThrownBy(() -> usuarios.invitar(correoAjeno.toUpperCase(), "Da igual", "Apellido",
                        rol("VENDEDOR"), null))
                .isInstanceOf(com.ondexia.domain.comun.error.Conflicto.class)
                .satisfies(fallo -> assertThat(
                        ((com.ondexia.domain.comun.error.Conflicto) fallo).getCodigo())
                        .isEqualTo("correo_en_otra_suscripcion"))
                // No se dice de qué suscripción se trata: el formulario de
                // invitación no es un buscador de clientes nuestros.
                .hasMessageNotContaining("Otro cliente");
    }

    @Test
    @DisplayName("Agregar dos veces a la misma persona en la misma empresa es conflicto")
    void asignacionRepetida() throws Exception {
        String cuerpo = """
                {"email":"duplicada@ejemplo.com","nombre":"Duplicada","apellido":"Otra Vez",
                 "rolId":"%s"}"""
                .formatted(rol("VENDEDOR"));

        mockMvc.perform(post(USUARIOS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated());

        mockMvc.perform(post(USUARIOS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("usuario_ya_asignado"));
    }

    @Test
    @DisplayName("Sin establecimiento, la persona alcanza todos")
    void sinSucursalAlcanzaTodo() {
        comoDemo();
        var miembro = usuarios.invitar("global@ejemplo.com", "Alcance Total", "Apellido",
                rol("ALMACENERO"), null);

        assertThat(miembro.alcanzaTodosLosEstablecimientos()).isTrue();
        assertThat(miembro.sucursalId()).isNull();
    }

    // ── Las tres puertas ────────────────────────────────────────────────────

    @Test
    @DisplayName("Nadie se desactiva a sí mismo")
    void noPuedesDesactivarte() {
        comoDemo();
        var propia = usuarios.listar().stream()
                .filter(m -> m.usuarioId().equals(UUID.fromString(USUARIO_DEMO)))
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() -> usuarios.cambiarEstado(propia.asignacionId(), false))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("a ti mismo");
    }

    /**
     * La tercera puerta, que faltaba (hallazgo M1).
     *
     * <p>Las otras dos —desactivarse y retirarse— estaban cerradas desde el
     * principio. Faltaba justo la que sube de nivel: quien tuviera
     * {@code configuracion.usuario:editar} podia llamar a `reasignar` sobre su
     * propia asignacion y ponerse ADMINISTRADOR.
     *
     * <p>El orden en que se cerraron dice algo: se protegio primero lo que
     * molesta a quien se equivoca —quedarse fuera— y despues lo que aprovecha
     * quien no se equivoca.
     */
    @Test
    @DisplayName("Nadie se cambia el rol a sí mismo")
    void noPuedesCambiarteElRol() {
        comoDemo();
        var propia = usuarios.listar().stream()
                .filter(m -> m.usuarioId().equals(UUID.fromString(USUARIO_DEMO)))
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() ->
                usuarios.reasignar(propia.asignacionId(), rol("ADMINISTRADOR"), null))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("tu propio rol");
    }

    @Test
    @DisplayName("Nadie se retira el acceso a sí mismo")
    void noPuedesRetirarte() {
        comoDemo();
        var propia = usuarios.listar().stream()
                .filter(m -> m.usuarioId().equals(UUID.fromString(USUARIO_DEMO)))
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() -> usuarios.retirar(propia.asignacionId()))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("a ti mismo");
    }

    @Test
    @DisplayName("El último administrador de la cuenta no se puede desactivar")
    void elUltimoAdministradorNoSeDesactiva() {
        /*
         * Esta no la puede defender la base. El disparador de la V1 protege la
         * tabla `cuenta_administrador`, y desactivar al usuario no borra su fila
         * de administrador: la deja intacta y sin poder entrar, que es
         * exactamente el estado que el disparador existía para impedir.
         *
         * Se ejecuta como OTRA persona para que no salte antes la regla de «no
         * puedes desactivarte a ti mismo», que es la que se dispararía si el
         * administrador lo intentara sobre sí.
         */
        comoDemo();
        var otra = usuarios.invitar("otro.admin@ejemplo.com", "Otro Cualquiera", "Apellido",
                rol("ADMINISTRADOR"), null);

        // Ahora opera esa otra persona, que no es administradora de la cuenta.
        ContextoDePrueba.establecer(new ContextoOperacion(
                otra.usuarioId(), "sub-otra", CUENTA, 1L, UUID.fromString(EMPRESA_ADMINISTRADA),
                null, otra.rolId(), false, false, "127.0.0.1"));

        var administrador = usuarios.listar().stream()
                .filter(m -> m.usuarioId().equals(UUID.fromString(USUARIO_DEMO)))
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() -> usuarios.cambiarEstado(administrador.asignacionId(), false))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("único administrador");
    }

    // ── Reasignación y retirada ─────────────────────────────────────────────

    @Test
    @DisplayName("Cambiar el rol y el alcance de alguien")
    void reasignar() throws Exception {
        comoDemo();
        var miembro = usuarios.invitar("reasignable@ejemplo.com", "Se Mueve", "Apellido",
                rol("VENDEDOR"), SUCURSAL_MATRIZ);
        ContextoDePrueba.limpiar();

        mockMvc.perform(put(USUARIOS + "/" + miembro.asignacionId())
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rolId\":\"%s\"}".formatted(rol("ALMACENERO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolNombre").value("Almacenero"))
                // Sin sucursalId en el cuerpo pasa a alcanzar todos: el campo
                // ausente es una decisión, no un descuido.
                .andExpect(jsonPath("$.todosLosEstablecimientos").value(true));
    }

    @Test
    @DisplayName("Retirar el acceso deja a la persona en la cuenta")
    void retirarNoBorraALaPersona() throws Exception {
        comoDemo();
        var miembro = usuarios.invitar("de.paso@ejemplo.com", "De Paso", "Apellido",
                rol("VENDEDOR"), null);
        ContextoDePrueba.limpiar();

        mockMvc.perform(delete(USUARIOS + "/" + miembro.asignacionId())
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(USUARIOS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'de.paso@ejemplo.com')]").isEmpty());

        // Pero sigue existiendo: se la puede volver a agregar sin recrearla.
        comoDemo();
        var devuelta = usuarios.invitar("de.paso@ejemplo.com", "Da igual", "Apellido",
                rol("VENDEDOR"), null);
        assertThat(devuelta.usuarioId()).isEqualTo(miembro.usuarioId());
    }

    // ── Fronteras ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("No se puede asignar un establecimiento de otra empresa")
    void establecimientoAjeno() {
        comoDemo();
        // La ...022 es la casa matriz de la SEGUNDA empresa.
        UUID ajena = UUID.fromString("00000000-0000-4000-8000-000000000022");

        assertThatThrownBy(() ->
                usuarios.invitar("con.sucursal.ajena@ejemplo.com", "Ajena", "Apellido",
                        rol("VENDEDOR"), ajena))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("no existe en esta empresa");
    }

    @Test
    @DisplayName("No se puede asignar un rol inexistente")
    void rolInexistente() {
        comoDemo();
        assertThatThrownBy(() ->
                usuarios.invitar("con.rol.raro@ejemplo.com", "Rara", "Apellido",
                        UUID.randomUUID(), null))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("no está disponible");
    }

    @Test
    @DisplayName("Una asignación de otra empresa responde «no encontrada»")
    void asignacionDeOtraEmpresa() {
        // `usuario_empresa` queda fuera de RLS, así que el filtro es del código.
        // Se responde «no encontrada» y no «prohibido»: decir prohibido
        // confirmaría que ese identificador existe en otro sitio.
        comoDemo();
        var enLaOtra = UUID.fromString("00000000-0000-4000-8000-000000000031");

        assertThatThrownBy(() -> usuarios.retirar(enLaOtra))
                .hasMessageContaining("no está asignado a esta empresa");
    }

    // ── No elevacion (doc 12 §6.3) ─────────────────────────────────────────

    /**
     * Un rol solo concede permisos que su portador tiene.
     *
     * <p>Es la tercera via de M1, la que quedo abierta: quien tiene
     * {@code usuario:registrar} invitaba a una segunda identidad suya como
     * Administrador. Aqui una supervisora con permisos de usuarios —los del
     * Vendedor mas gestionar usuarios— intenta invitar a alguien como
     * Administrador y no puede; con su propio rol, si.
     */
    @Test
    @DisplayName("Nadie concede un rol con permisos que él mismo no tiene")
    void nadieConcedeLoQueNoTiene() {
        comoDemo();
        var supervisora = rolesUso.duplicar(rol("VENDEDOR"), "Supervisora de tienda");
        var conUsuarios = new java.util.HashSet<>(rolesUso.permisosDe(supervisora.id()));
        conUsuarios.add(permiso("configuracion", "acceder"));
        conUsuarios.add(permiso("configuracion.usuario", "acceder"));
        conUsuarios.add(permiso("configuracion.usuario", "consultar"));
        conUsuarios.add(permiso("configuracion.usuario", "registrar"));
        conUsuarios.add(permiso("configuracion.usuario", "editar"));
        rolesUso.cambiarPermisos(supervisora.id(), conUsuarios);
        var miembro = usuarios.invitar("supervisora@ejemplo.com", "Supervisora", "De Tienda",
                supervisora.id(), null);

        // Como ella: con su rol, sin ser administradora de la cuenta.
        ContextoDePrueba.limpiar();
        ContextoDePrueba.establecer(new ContextoOperacion(miembro.usuarioId(), "sub-supervisora",
                CUENTA, 1L, UUID.fromString(EMPRESA_ADMINISTRADA), null, supervisora.id(),
                false, false, "127.0.0.1"));

        assertThatThrownBy(() -> usuarios.invitar("colado@ejemplo.com", "Colado", "Apellido",
                rol("ADMINISTRADOR"), null))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("permisos que tú no tienes");

        // Un rol que es subconjunto del suyo si se puede conceder.
        var vendedor = usuarios.invitar("nuevo.vendedor@ejemplo.com", "Nuevo", "Vendedor",
                rol("VENDEDOR"), null);
        assertThat(vendedor.rolCodigo()).isEqualTo("VENDEDOR");

        // Y tampoco se puede subir a alguien por reasignacion.
        assertThatThrownBy(() -> usuarios.reasignar(vendedor.asignacionId(),
                rol("ADMINISTRADOR"), null))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("permisos que tú no tienes");
    }

    @Test
    @DisplayName("El Propietario concede cualquier rol, y el listado lo señala")
    void elPropietarioConcedeCualquierRol() throws Exception {
        comoDemo();
        var miembro = usuarios.invitar("admin.nuevo@ejemplo.com", "Admin", "Nuevo",
                rol("ADMINISTRADOR"), null);
        assertThat(miembro.rolCodigo()).isEqualTo("ADMINISTRADOR");
        ContextoDePrueba.limpiar();

        mockMvc.perform(get(USUARIOS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.usuarioId == '" + USUARIO_DEMO + "')].propietario")
                        .value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$[?(@.email == 'admin.nuevo@ejemplo.com')].propietario")
                        .value(org.hamcrest.Matchers.contains(false)));
    }
}
