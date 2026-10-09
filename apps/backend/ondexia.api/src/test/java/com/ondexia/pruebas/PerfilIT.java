package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.application.configuracion.Usuarios;
import com.ondexia.domain.identidad.RolRepositorio;
import com.ondexia.domain.identidad.UsuarioRepositorio;
import com.ondexia.infrastructure.seguridad.ContextoDePrueba;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Mi perfil: lo que cada persona edita de si misma.
 *
 * <p>Lo que importa probar no es que guarde un nombre, sino los limites. El
 * usuario sale del contexto y nunca de un parametro, asi que no existe forma de
 * nombrar a otro; la prueba de aislamiento comprueba que eso sigue siendo cierto
 * el dia que alguien anada un id "por comodidad".
 *
 * <p>Y el correo: es la credencial de Cognito. Si un dia se colara un campo que
 * lo cambie, la fila apuntaria a un buzon con el que ya no se puede entrar — el
 * cambio se veria guardado y el acceso roto, en ese orden.
 */
class PerfilIT extends PruebaIntegracion {

    private static final String PERFIL = "/api/v1/perfil";
    private static final UUID CUENTA = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID SUCURSAL_MATRIZ =
            UUID.fromString("00000000-0000-4000-8000-000000000020");

    @Autowired
    private Usuarios usuarios;

    @Autowired
    private UsuarioRepositorio repositorio;

    @Autowired
    private RolRepositorio roles;

    @AfterEach
    void limpiar() {
        ContextoDePrueba.limpiar();
    }

    /** Deja a la persona dentro de la cuenta demo con identidad propia. */
    private void crearUsuarioCon(String sub, String email, String nombre, String apellido) {
        ContextoDePrueba.comoUsuarioDe(
                UUID.fromString(USUARIO_DEMO), CUENTA, UUID.fromString(EMPRESA_ADMINISTRADA));
        usuarios.invitar(email, nombre, apellido,
                rolDeLaCuenta("VENDEDOR").id(), SUCURSAL_MATRIZ);
        ContextoDePrueba.limpiar();

        var invitado = repositorio.buscarInvitacionesPendientes(email).getFirst();
        invitado.vincularIdentidad(sub);
        repositorio.guardar(invitado);
    }

    @Test
    @DisplayName("Devuelve mis datos, no los de la cuenta")
    void devuelveMisDatos() throws Exception {
        mockMvc.perform(get(PERFIL)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("demo@ondexia.com"))
                .andExpect(jsonPath("$.nombre").value("Usuario de demostracion"));
    }

    @Test
    @DisplayName("Una fila anterior a la V11 conserva su nombre entero y sin apellido")
    void laFilaViejaLlegaSinApellido() throws Exception {
        /*
         * El demo es de antes de que el campo existiera. La migracion NO parte
         * su nombre por el espacio: daria "Usuario" y "de demostracion", y con
         * "Maria del Carmen Rojas" seria peor. Un dato inventado que parece
         * correcto cuesta mas de detectar que uno ausente.
         *
         * El frontend usa este nulo para avisar de que hay que repartirlo a
         * mano, asi que tiene que seguir llegando nulo y no cadena vacia.
         */
        mockMvc.perform(get(PERFIL).header("Authorization", autorizacionDemo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Usuario de demostracion"))
                .andExpect(jsonPath("$.apellido").doesNotExist());
    }

    @Test
    @DisplayName("El apellido es obligatorio aunque la columna admita nulo")
    void elApellidoEsObligatorio() throws Exception {
        /*
         * La columna acepta nulo por las filas viejas; el formulario no, porque
         * esta pantalla es justo donde se arreglan. Aceptarlo en blanco las
         * dejaria a medias para siempre: nada mas las vuelve a tocar.
         */
        mockMvc.perform(put(PERFIL)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Usuario","apellido":"  ","telefono":null}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("El nombre completo del contexto une las dos columnas")
    void elContextoUneNombreYApellido() throws Exception {
        // La barra superior y la bitacora muestran la persona, no editan sus
        // campos: el contexto entrega el nombre ya unido para que ninguna
        // pantalla tenga que decidir como se concatena.
        crearUsuarioCon("sub-perfil-contexto", "perfil.contexto@ejemplo.com", "Ana", "Quispe");

        mockMvc.perform(get("/api/v1/contexto")
                        .header("Authorization", "Bearer " + tokenPara("sub-perfil-contexto"))
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.nombre").value("Ana Quispe"));
    }

    @Test
    @DisplayName("Guarda el nombre y el telefono, y deja el correo intacto")
    void guardaNombreYTelefono() throws Exception {
        crearUsuarioCon("sub-perfil-edita", "perfil.edita@ejemplo.com", "Nombre", "Viejo");

        mockMvc.perform(put(PERFIL)
                        .header("Authorization", "Bearer " + tokenPara("sub-perfil-edita"))
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"  Nombre Nuevo  ","apellido":" Apellido Nuevo ","telefono":"987 654 321"}"""))
                .andExpect(status().isOk())
                // Los espacios se recortan en el servidor: si se devolviera lo
                // enviado, un refresco "cambiaria" el dato sin que nadie lo tocara.
                .andExpect(jsonPath("$.nombre").value("Nombre Nuevo"))
                .andExpect(jsonPath("$.apellido").value("Apellido Nuevo"))
                .andExpect(jsonPath("$.telefono").value("987 654 321"))
                .andExpect(jsonPath("$.email").value("perfil.edita@ejemplo.com"));
    }

    @Test
    @DisplayName("El correo del cuerpo se ignora: es la credencial de Cognito")
    void elCorreoDelCuerpoSeIgnora() throws Exception {
        /*
         * El DTO no tiene campo `email`, asi que este cuerpo trae uno de mas.
         * Se comprueba que no cuele igualmente: si alguien lo anadiera sin
         * pensarlo, cambiar el correo dejaria a la persona sin poder entrar, y
         * este endpoint no exige contrasena ni verificacion de nada.
         */
        crearUsuarioCon("sub-perfil-correo", "perfil.correo@ejemplo.com", "Quien", "Sea");

        mockMvc.perform(put(PERFIL)
                        .header("Authorization", "Bearer " + tokenPara("sub-perfil-correo"))
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Quien","apellido":"Sea","email":"secuestrado@ejemplo.com",
                                 "telefono":null}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("perfil.correo@ejemplo.com"));
    }

    @Test
    @DisplayName("Cada quien ve y edita lo suyo, aunque compartan cuenta y empresa")
    void cadaQuienEditaLoSuyo() throws Exception {
        /*
         * La prueba de aislamiento. Hoy es imposible fallar —el usuario sale del
         * contexto y el endpoint no acepta ningun id— y de eso se trata: si
         * manana alguien anade un parametro "por comodidad", esto se pone rojo.
         */
        crearUsuarioCon("sub-perfil-otra", "perfil.otra@ejemplo.com", "La Otra", "Persona");

        mockMvc.perform(put(PERFIL)
                        .header("Authorization", "Bearer " + tokenPara("sub-perfil-otra"))
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Renombrada","apellido":"Del Todo","telefono":"111"}"""))
                .andExpect(status().isOk());

        // El demo, con su propio token, sigue como estaba.
        mockMvc.perform(get(PERFIL)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(jsonPath("$.nombre").value("Usuario de demostracion"))
                .andExpect(jsonPath("$.telefono").doesNotExist());
    }

    @Test
    @DisplayName("El telefono en blanco borra el que hubiera")
    void elTelefonoEnBlancoBorra() throws Exception {
        // Una columna opcional con dos formas de decir «sin dato» —null y cadena
        // vacia— produce consultas que se olvidan de una.
        crearUsuarioCon("sub-perfil-borra", "perfil.borra@ejemplo.com", "Con", "Telefono");

        var conTelefono = """
                {"nombre":"Con","apellido":"Telefono","telefono":"999888777"}""";
        var sinTelefono = """
                {"nombre":"Con","apellido":"Telefono","telefono":"   "}""";

        mockMvc.perform(put(PERFIL)
                        .header("Authorization", "Bearer " + tokenPara("sub-perfil-borra"))
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conTelefono))
                .andExpect(jsonPath("$.telefono").value("999888777"));

        mockMvc.perform(put(PERFIL)
                        .header("Authorization", "Bearer " + tokenPara("sub-perfil-borra"))
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sinTelefono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").doesNotExist());

        assertThat(repositorio.buscarPorCognitoSub("sub-perfil-borra").orElseThrow().telefono())
                .isNull();
    }

    @Test
    @DisplayName("Un nombre en blanco no pasa")
    void elNombreEsObligatorio() throws Exception {
        mockMvc.perform(put(PERFIL)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"   ","apellido":"Apellido","telefono":null}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Se puede entrar a Mi perfil sin haber elegido empresa")
    void funcionaSinEmpresaActiva() throws Exception {
        /*
         * Quien tiene varias empresas cae en el escritorio sin ninguna activa, y
         * desde ahi puede ir a Mi perfil. Si este endpoint exigiera empresa —o
         * escribiera en `auditoria`, que tiene RLS por empresa— fallaria solo
         * para ese subconjunto de usuarios: el peor reparto posible.
         */
        mockMvc.perform(get(PERFIL).header("Authorization", autorizacionDemo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("demo@ondexia.com"));
    }
}
