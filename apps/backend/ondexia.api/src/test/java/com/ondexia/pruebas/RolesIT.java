package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.application.configuracion.Roles;
import com.ondexia.application.configuracion.Usuarios;
import com.ondexia.domain.comun.error.Conflicto;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.identidad.Permiso;
import com.ondexia.domain.identidad.PermisoRepositorio;
import com.ondexia.domain.identidad.RolRepositorio;
import com.ondexia.domain.identidad.UsuarioRepositorio;
import com.ondexia.infrastructure.seguridad.ContextoDePrueba;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Entrega 5: roles a medida.
 *
 * <p>La prueba que justifica el diseño entero es
 * {@link #revocarSurteEfectoEnLaPeticionSiguiente()}. Los permisos se cachean
 * dentro del contenedor de Lambda, y sin invalidación por versión un permiso
 * revocado se seguiría concediendo hasta que ese contenedor se reciclara — que
 * puede ser dentro de horas, y no en todos a la vez.
 */
class RolesIT extends PruebaIntegracion {

    private static final String ROLES = "/api/v1/configuracion/roles";
    private static final String ALMACENES = "/api/v1/almacen/almacenes";
    private static final UUID CUENTA = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    private Roles roles;

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private RolRepositorio repositorioRoles;

    @Autowired
    private PermisoRepositorio permisos;

    @Autowired
    private UsuarioRepositorio repositorioUsuarios;

    @Autowired
    private com.ondexia.domain.identidad.UsuarioEmpresaRepositorio asignaciones;

    @AfterEach
    void limpiar() {
        ContextoDePrueba.limpiar();
    }

    private void comoDemo() {
        ContextoDePrueba.comoUsuarioDe(
                UUID.fromString(USUARIO_DEMO), CUENTA, UUID.fromString(EMPRESA_ADMINISTRADA));
    }

    private UUID permiso(String modulo, String accion) {
        return permisos.listarCatalogo().stream()
                .filter(p -> p.modulo().equals(modulo) && p.accion().equals(accion))
                .map(Permiso::id)
                .findFirst()
                .orElseThrow();
    }

    /** El interruptor de área: {@code almacen:acceder}. */
    private UUID permisoDeModulo(String modulo) {
        return permiso(modulo, Permiso.ACCEDER);
    }

    /** El eslabón intermedio: {@code almacen.almacen:acceder}. */
    private UUID permisoDeSubmodulo(String submodulo) {
        return permiso(submodulo, Permiso.ACCEDER);
    }

    // ── La prueba que justifica permisos_version ────────────────────────────

    @Test
    @DisplayName("Revocar un permiso surte efecto en la petición siguiente")
    void revocarSurteEfectoEnLaPeticionSiguiente() throws Exception {
        comoDemo();

        // Un rol propio, copiado de Vendedor: no tiene `almacen.almacen`.
        var aMedida = roles.duplicar(
                repositorioRoles.buscarPredefinido("VENDEDOR").orElseThrow().id(),
                "Vendedor con almacén");

        // Alguien con ese rol. Se le vincula una identidad de Cognito a mano,
        // que es lo que ocurriría en su primer ingreso: sin `cognito_sub` el
        // token no resolvería a ningún usuario y la prueba fallaría por el
        // motivo equivocado.
        String sub = "sub-rol-a-medida";
        var miembro = usuarios.invitar("a.medida@ejemplo.com", "Con Rol Propio", "Apellido",
                aMedida.id(), null);
        var usuario = repositorioUsuarios.buscarPorId(miembro.usuarioId()).orElseThrow();
        usuario.vincularIdentidad(sub);
        repositorioUsuarios.guardar(usuario);

        ContextoDePrueba.limpiar();
        String token = "Bearer " + tokenPara(sub);

        // 1. Copiado de Vendedor, no puede ver almacenes.
        mockMvc.perform(get(ALMACENES)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isForbidden());

        // 2. Se le concede el permiso. La cadena entera: sin el módulo y el
        // submódulo, la función suelta se podaría al guardar.
        comoDemo();
        var conAlmacenes = new HashSet<>(roles.permisosDe(aMedida.id()));
        conAlmacenes.add(permisoDeModulo("almacen"));
        conAlmacenes.add(permisoDeSubmodulo("almacen.almacen"));
        conAlmacenes.add(permiso("almacen.almacen", "consultar"));
        roles.cambiarPermisos(aMedida.id(), conAlmacenes);
        ContextoDePrueba.limpiar();

        // La petición SIGUIENTE ya lo ve. Sin invalidar la caché por versión,
        // esto seguiría dando 403 con el permiso ya concedido en la base.
        mockMvc.perform(get(ALMACENES)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk());

        // 3. Y al revocarlo, otra vez fuera. Este es el sentido que importa:
        // conceder tarde es una molestia, revocar tarde es un agujero.
        comoDemo();
        var sinAlmacenes = new HashSet<>(roles.permisosDe(aMedida.id()));
        sinAlmacenes.remove(permiso("almacen.almacen", "consultar"));
        roles.cambiarPermisos(aMedida.id(), sinAlmacenes);
        ContextoDePrueba.limpiar();

        mockMvc.perform(get(ALMACENES)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isForbidden());
    }

    // ── Duplicar ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("La copia arrastra los permisos del original y ya es editable")
    void duplicarCopiaLosPermisos() throws Exception {
        comoDemo();
        var origen = repositorioRoles.buscarPredefinido("ALMACENERO").orElseThrow();
        int permisosDelOrigen = roles.permisosDe(origen.id()).size();
        ContextoDePrueba.limpiar();

        mockMvc.perform(post(ROLES + "/" + origen.id() + "/duplicado")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Almacenero de turno noche\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.delSistema").value(false))
                // Empezar en blanco sería más simple de programar y peor de
                // usar: quien duplica quiere «lo de Almacenero y además esto».
                .andExpect(jsonPath("$.cantidadPermisos").value(permisosDelOrigen))
                .andExpect(jsonPath("$.enUso").value(false))
                // El código se deriva del nombre: es interno y tiene índice
                // único por cuenta, así que pedírselo al usuario solo serviría
                // para que choque.
                .andExpect(jsonPath("$.codigo").value("ALMACENERO_DE_TURNO_NOCHE"));
    }

    @Test
    @DisplayName("Duplicar dos veces con el mismo nombre no choca")
    void codigosQueNoChocan() {
        comoDemo();
        var origen = repositorioRoles.buscarPredefinido("CONTADOR").orElseThrow();

        var primero = roles.duplicar(origen.id(), "Contador junior");
        var segundo = roles.duplicar(origen.id(), "Contador junior");

        assertThat(primero.codigo()).isEqualTo("CONTADOR_JUNIOR");
        assertThat(segundo.codigo()).isEqualTo("CONTADOR_JUNIOR_2");
    }

    // ── Los predefinidos son inmutables ─────────────────────────────────────

    @Test
    @DisplayName("Un rol del sistema no se renombra ni se borra")
    void losPredefinidosNoSeTocan() throws Exception {
        var vendedor = repositorioRoles.buscarPredefinido("VENDEDOR").orElseThrow();

        // 409 y no 403: no es un problema de quién eres, es que ese rol no lo
        // modifica nadie. Un 403 sugeriría que otro usuario sí podría.
        mockMvc.perform(put(ROLES + "/" + vendedor.id())
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Vendedor mío\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("rol_del_sistema"));

        mockMvc.perform(delete(ROLES + "/" + vendedor.id())
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isConflict());
    }

    // ── Eliminar ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Un rol con usuarios asignados no se puede eliminar")
    void noSeBorraUnRolEnUso() {
        comoDemo();
        var aMedida = roles.duplicar(
                repositorioRoles.buscarPredefinido("VENDEDOR").orElseThrow().id(),
                "Vendedor ocupado");

        usuarios.invitar("ocupa.rol@ejemplo.com", "Ocupa El Rol", "Apellido", aMedida.id(), null);

        assertThatThrownBy(() -> roles.eliminar(aMedida.id()))
                .isInstanceOf(Conflicto.class)
                .hasMessageContaining("Cámbiales el rol");

        assertThat(roles.listar())
                .filteredOn(detalle -> detalle.rol().id().equals(aMedida.id()))
                .singleElement()
                .extracting(detalle -> detalle.enUso())
                .isEqualTo(true);
    }

    @Test
    @DisplayName("Un rol libre se elimina")
    void unRolLibreSeElimina() throws Exception {
        comoDemo();
        var aMedida = roles.duplicar(
                repositorioRoles.buscarPredefinido("CONTADOR").orElseThrow().id(),
                "Contador de paso");
        ContextoDePrueba.limpiar();

        mockMvc.perform(delete(ROLES + "/" + aMedida.id())
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ROLES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Contador de paso')]").isEmpty());
    }

    // ── Catálogo y validación ───────────────────────────────────────────────

    @Test
    @DisplayName("El catálogo llega como árbol de tres niveles y con nombres")
    void catalogoJerarquico() throws Exception {
        mockMvc.perform(get(ROLES + "/permisos")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.codigo == 'almacen')].nombre").value("Almacén"))
                // Los nombres vienen de la base, no de un mapa en el frontend:
                // un módulo nuevo aparece solo, sin tocar TypeScript.
                .andExpect(jsonPath(
                        "$[?(@.codigo == 'configuracion')].submodulos[?(@.codigo == 'configuracion.serie')].nombre")
                        .value("Series y correlativos"))
                .andExpect(jsonPath(
                        "$[?(@.codigo == 'configuracion')].submodulos[?(@.codigo == 'configuracion.serie')].funciones.length()")
                        .value(3))
                .andExpect(jsonPath(
                        "$[?(@.codigo == 'configuracion')].submodulos[?(@.codigo == 'configuracion.comprobante')].funciones.length()")
                        .value(2));
    }

    @Test
    @DisplayName("El acento sobrevive a la migración: «Almacén», no «AlmacÃ©n»")
    void losNombresConservanLosAcentos() {
        /*
         * Guarda contra el mojibake, que en este proyecto ya apareció una vez —en
         * las respuestas de Lambda— y aquí tendría otra causa: la codificación con
         * la que Flyway lee el archivo .sql. Los nombres de módulo son el primer
         * texto acentuado que entra por una migración.
         */
        comoDemo();
        assertThat(permisos.listarCatalogo())
                .filteredOn(p -> "almacen".equals(p.modulo()))
                .extracting(Permiso::nombre)
                .contains("Almacén");
    }

    // ── La jerarquía: el módulo es una puerta de verdad ─────────────────────

    @Test
    @DisplayName("Apagar el módulo deja fuera todo lo que cuelga de él")
    void elModuloEsUnaPuerta() throws Exception {
        /*
         * La prueba que justifica el modelo jerárquico. El rol conserva
         * `almacen.almacen:consultar` en la matriz —el usuario no lo tocó— y aun
         * así deja de autorizar en cuanto se retira el acceso al módulo.
         */
        comoDemo();
        var aMedida = roles.duplicar(
                repositorioRoles.buscarPredefinido("ALMACENERO").orElseThrow().id(),
                "Almacenero con puerta");

        String sub = "sub-puerta-de-modulo";
        var miembro = usuarios.invitar(
                "puerta@ejemplo.com", "Con Puerta", "Apellido", aMedida.id(), null);
        var usuario = repositorioUsuarios.buscarPorId(miembro.usuarioId()).orElseThrow();
        usuario.vincularIdentidad(sub);
        repositorioUsuarios.guardar(usuario);
        ContextoDePrueba.limpiar();

        String token = "Bearer " + tokenPara(sub);

        // Copiado de Almacenero: entra sin problema.
        mockMvc.perform(get(ALMACENES)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk());

        // Se retira SOLO el módulo. Las casillas de dentro se mandan igual.
        comoDemo();
        var sinModulo = new HashSet<>(roles.permisosDe(aMedida.id()));
        sinModulo.remove(permisoDeModulo("almacen"));
        roles.cambiarPermisos(aMedida.id(), sinModulo);
        ContextoDePrueba.limpiar();

        mockMvc.perform(get(ALMACENES)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Al guardar se poda lo que se quedó sin padre")
    void sePodaLoQueNoTienePadre() {
        /*
         * Lo guardado tiene que coincidir con lo que está en vigor. Si se
         * almacenara la función sin sus padres, la matriz mostraría una casilla
         * marcada que la autorización niega — una denegación que no se explica
         * mirando donde uno mira.
         *
         * La pantalla ya desmarca en cascada, así que en el uso normal esto no
         * quita nada. Está para lo demás: una petición hecha a mano, un cliente
         * antiguo, un error.
         */
        comoDemo();
        var aMedida = roles.duplicar(
                repositorioRoles.buscarPredefinido("VENDEDOR").orElseThrow().id(),
                "Vendedor podado");

        // Solo la función, sin su submódulo ni su módulo.
        roles.cambiarPermisos(aMedida.id(), Set.of(permiso("almacen.almacen", "consultar")));

        assertThat(roles.permisosDe(aMedida.id()))
                .as("la función suelta no se guarda: le faltan los dos eslabones de arriba")
                .isEmpty();

        // Con la cadena completa sí entra.
        roles.cambiarPermisos(aMedida.id(), Set.of(
                permisoDeModulo("almacen"),
                permisoDeSubmodulo("almacen.almacen"),
                permiso("almacen.almacen", "consultar")));

        assertThat(roles.permisosDe(aMedida.id())).hasSize(3);
    }

    @Test
    @DisplayName("Un submódulo sin su módulo tampoco se guarda")
    void elSubmoduloTambienNecesitaPadre() {
        comoDemo();
        var aMedida = roles.duplicar(
                repositorioRoles.buscarPredefinido("VENDEDOR").orElseThrow().id(),
                "Vendedor sin modulo");

        roles.cambiarPermisos(aMedida.id(), Set.of(
                permisoDeSubmodulo("almacen.almacen"),
                permiso("almacen.almacen", "consultar")));

        assertThat(roles.permisosDe(aMedida.id())).isEmpty();
    }

    @Test
    @DisplayName("La migración no cambió lo que los roles predefinidos podían")
    void losPredefinidosConservanSuAlcance() {
        /*
         * V6 concede a cada rol los eslabones de módulo y submódulo que ya tenía
         * de hecho, derivándolos de sus funciones. Sin eso, la migración habría
         * dejado a TODOS los roles sin poder nada — que es la única forma
         * inaceptable de cambiar el modelo de autorización de un sistema en
         * marcha.
         */
        comoDemo();
        var vendedor = repositorioRoles.buscarPredefinido("VENDEDOR").orElseThrow();
        var suyos = permisos.permisosDelRol(vendedor.id());

        assertThat(suyos.puede("ventas.comprobante", "emitir"))
                .as("Vendedor emite comprobantes")
                .isTrue();
        assertThat(suyos.puede("ventas.comprobante", "anular"))
                .as("y sigue sin poder anularlos")
                .isFalse();
        assertThat(suyos.puede("almacen.almacen", "consultar"))
                .as("ni ve almacenes, que nunca tuvo")
                .isFalse();
        assertThat(suyos.alcanzaModulo("ventas")).isTrue();
        assertThat(suyos.alcanzaModulo("configuracion")).isFalse();
    }

    @Test
    @DisplayName("Un permiso que no está en el catálogo se rechaza")
    void permisoDesconocido() throws Exception {
        comoDemo();
        var aMedida = roles.duplicar(
                repositorioRoles.buscarPredefinido("VENDEDOR").orElseThrow().id(),
                "Vendedor con permiso raro");
        ContextoDePrueba.limpiar();

        mockMvc.perform(put(ROLES + "/" + aMedida.id() + "/permisos")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permisoIds\":[\"%s\"]}".formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("permiso_desconocido"));
    }

    @Test
    @DisplayName("Una lista vacía de permisos es válida")
    void rolSinPermisos() {
        // Un rol sin nada no puede nada, y es una configuración legítima
        // mientras se compone. Rechazarla obligaría a construirlo al revés.
        comoDemo();
        var aMedida = roles.duplicar(
                repositorioRoles.buscarPredefinido("VENDEDOR").orElseThrow().id(),
                "Vendedor en blanco");

        roles.cambiarPermisos(aMedida.id(), Set.of());

        assertThat(roles.permisosDe(aMedida.id())).isEmpty();
    }

    // ── Auto-elevacion (hallazgo M1) ────────────────────────────────────────

    /**
     * Nadie se ensancha el rol que él mismo tiene.
     *
     * <p>Es la variante de M1 que menos se ve. Cambiarse de rol lo cierra
     * {@code Usuarios.reasignar}; esto es lo otro: dejar el rol donde está y
     * marcarle las casillas que faltan. El efecto es el mismo —más permisos para
     * quien lo hace— y además no deja rastro de asignación en la bitácora.
     *
     * <p>La asignación se cambia aquí por el repositorio y no por el caso de uso,
     * precisamente porque el caso de uso ya no lo permite.
     */
    @Test
    @DisplayName("No se pueden editar los permisos del rol que uno mismo tiene")
    void noSePuedeEnsancharElPropioRol() {
        comoDemo();

        var aMedida = roles.duplicar(
                repositorioRoles.buscarPredefinido("VENDEDOR").orElseThrow().id(),
                "Rol del supervisor");

        // Alguien CON ese rol, y con permiso para editar roles: es el caso real
        // —un supervisor al que se le deja gestionar su equipo—, no un
        // administrador disfrazado.
        var supervisor = usuarios.invitar("supervisor@ejemplo.com", "Supervisora", "Apellido",
                aMedida.id(), null);

        var conMas = new HashSet<>(roles.permisosDe(aMedida.id()));
        conMas.add(permisoDeModulo("almacen"));

        // Se actua COMO ella. El demo no se toca: reasignarlo dejaria a las
        // demas pruebas de esta clase sin permisos, y el fallo apareceria en
        // otra prueba cualquiera.
        ContextoDePrueba.limpiar();
        ContextoDePrueba.comoUsuarioDe(supervisor.usuarioId(), CUENTA,
                UUID.fromString(EMPRESA_ADMINISTRADA));

        assertThatThrownBy(() -> roles.cambiarPermisos(aMedida.id(), conMas))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("rol que tú mismo");
    }

    /**
     * Y el rol de OTRO se sigue pudiendo editar, que es para lo que existe la
     * pantalla. Sin esta mitad, la prueba de arriba pasaría igual con un
     * `cambiarPermisos` que lanzara siempre.
     */
    @Test
    @DisplayName("El rol de otra persona sí se puede editar")
    void elRolAjenoSiSeEdita() {
        comoDemo();

        var ajeno = roles.duplicar(
                repositorioRoles.buscarPredefinido("VENDEDOR").orElseThrow().id(),
                "Rol de otra persona");

        var conMas = new HashSet<>(roles.permisosDe(ajeno.id()));
        conMas.add(permisoDeModulo("almacen"));

        roles.cambiarPermisos(ajeno.id(), conMas);

        assertThat(roles.permisosDe(ajeno.id())).contains(permisoDeModulo("almacen"));
    }
}
