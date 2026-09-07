package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * Aceptar una invitacion: de la fila sin identidad a la persona que entra.
 *
 * <p>Lo que se prueba aqui son dos cosas distintas. La primera es que el camino
 * funcione, porque durante un tiempo no existio: la invitacion se creaba, la
 * persona se registraba en Cognito, y como nadie relacionaba su {@code sub}
 * nuevo con ese correo terminaba en el formulario de empresa nueva creandose una
 * segunda cuenta con su propio RUC.
 *
 * <p>La segunda es la que de verdad importa. Este es el unico endpoint que
 * decide en que empresa entra alguien a partir de su correo, asi que un correo
 * que no venga firmado y verificado por Cognito lo convierte en una toma de
 * cuenta: quien envie el correo ajeno entra en la empresa ajena con el rol que
 * tuviera esa invitacion. Las pruebas del token no son ceremonia — son la unica
 * cosa que separa una de la otra, y ninguna de las dos da error visible si se
 * cae.
 */
class VinculacionIT extends PruebaIntegracion {

    private static final String VINCULO = "/api/v1/registro/vinculo";
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

    /**
     * Deja una invitacion pendiente y devuelve el id de la fila creada.
     *
     * <p>Los correos de cada prueba tienen que ser unicos <strong>en toda la
     * suite</strong>, no solo en esta clase: las pruebas comparten el mismo
     * contenedor y no se limpia entre clases, asi que reutilizar uno de
     * {@code UsuariosIT} hace que el alta choque con «ya tiene acceso a esta
     * empresa» — que es como se descubrio.
     */
    private UUID invitar(String email) {
        ContextoDePrueba.comoUsuarioDe(
                UUID.fromString(USUARIO_DEMO), CUENTA, UUID.fromString(EMPRESA_ADMINISTRADA));
        var miembro = usuarios.invitar(
                email, "Persona", "Invitada",
                rolDeLaCuenta("VENDEDOR").id(),
                SUCURSAL_MATRIZ);
        ContextoDePrueba.limpiar();
        return miembro.usuarioId();
    }

    @Test
    @DisplayName("El invitado entra en la cuenta que le invito, no en una nueva")
    void elInvitadoEntraEnSuCuenta() throws Exception {
        invitar("emilio@ejemplo.com");

        mockMvc.perform(post(VINCULO)
                        .header("Authorization",
                                "Bearer " + tokenDeIdentidad("sub-emilio", "emilio@ejemplo.com", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuentaId").value(CUENTA.toString()));

        // La comprobacion que importa: a partir de ahora el token de acceso de
        // esa persona resuelve contexto, que es lo que le abre las pantallas.
        var vinculado = repositorio.buscarPorCognitoSub("sub-emilio").orElseThrow();
        assertThat(vinculado.cuentaId()).isEqualTo(CUENTA);
    }

    @Test
    @DisplayName("Un token de ACCESO no vincula, aunque lleve el correo")
    void elTokenDeAccesoNoVincula() throws Exception {
        /*
         * `tokenPara` incluye la reclamacion `email` por comodidad de las
         * pruebas, y esa comodidad es exactamente el escenario peligroso: si el
         * endpoint se conformara con encontrar un correo, cualquier token de
         * acceso valdria. El de Cognito de verdad no lo lleva, pero el filtro no
         * puede depender de eso — tiene que ser explicito.
         */
        invitar("victima@ejemplo.com");

        mockMvc.perform(post(VINCULO)
                        .header("Authorization", "Bearer " + tokenPara("victima@ejemplo.com")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Sin el correo verificado no se reclama la invitacion de nadie")
    void sinCorreoVerificadoNoVincula() throws Exception {
        /*
         * El ataque completo: alguien se da de alta en Cognito declarando el
         * correo de otra persona. Cognito emite el token igual, pero con
         * `email_verified` en falso porque ese buzon nunca confirmo nada. Sin
         * esta comprobacion, ese token bastaria para entrar en la empresa ajena.
         */
        invitar("titular@ejemplo.com");

        mockMvc.perform(post(VINCULO)
                        .header("Authorization",
                                "Bearer " + tokenDeIdentidad("sub-impostor", "titular@ejemplo.com", false)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Sin invitacion pendiente, 404 y a registrarse")
    void sinInvitacionNoHayNadaQueVincular() throws Exception {
        // Es el caso normal de quien se registra por su cuenta. El SPA lo usa
        // para decidir entre entrar y pintar el formulario de empresa, asi que
        // el codigo tiene que ser estable.
        mockMvc.perform(post(VINCULO)
                        .header("Authorization",
                                "Bearer " + tokenDeIdentidad("sub-nadie", "nadie@ejemplo.com", true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("sin_invitacion"));
    }

    @Test
    @DisplayName("Una identidad ya vinculada no roba la invitacion de otro correo")
    void unaIdentidadYaVinculadaNoRobaOtraInvitacion() throws Exception {
        /*
         * El usuario demo ya tiene `cognito_sub`. Si llegara con un token de
         * identidad cuyo correo apunta a una invitacion ajena, el endpoint debe
         * devolverle SU cuenta y no tocar la invitacion: `vincularIdentidad`
         * protege la fila, pero conviene que el caso de uso salga antes y de
         * forma idempotente, porque el SPA llama aqui en cada recarga.
         */
        UUID invitado = invitar("ajena@ejemplo.com");

        mockMvc.perform(post(VINCULO)
                        .header("Authorization",
                                "Bearer " + tokenDeIdentidad(SUB_DEMO, "ajena@ejemplo.com", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuentaId").value(CUENTA.toString()));

        assertThat(repositorio.buscarPorId(invitado).orElseThrow().cognitoSub())
                .as("la invitacion ajena tiene que seguir libre")
                .isNull();
    }

    @Test
    @DisplayName("Vincular dos veces devuelve lo mismo, no un error")
    void vincularDosVecesEsIdempotente() throws Exception {
        invitar("dos.veces@ejemplo.com");

        for (int intento = 0; intento < 2; intento++) {
            mockMvc.perform(post(VINCULO)
                            .header("Authorization", "Bearer "
                                    + tokenDeIdentidad("sub-repetido", "dos.veces@ejemplo.com", true)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cuentaId").value(CUENTA.toString()));
        }
    }
}
