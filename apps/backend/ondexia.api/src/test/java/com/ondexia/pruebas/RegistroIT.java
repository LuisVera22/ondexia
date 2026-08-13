package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.infrastructure.seguridad.ContextoDePrueba;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Alta de un cliente nuevo, de extremo a extremo.
 *
 * <p>Estas pruebas nacen de un fallo real: el primer registro creaba la cuenta,
 * el usuario, la empresa y la casa matriz, y el usuario entraba al panel sin
 * poder abrir <em>nada</em> — todas las pantallas respondían
 * {@code sin_empresa_activa}. Faltaba la fila de {@code usuario_empresa}, que
 * es la que da acceso a la empresa recién creada.
 *
 * <p>Por eso la prueba principal no se conforma con el 201: comprueba que
 * <strong>después del alta el usuario puede trabajar</strong>, que es lo que de
 * verdad se quería.
 */
class RegistroIT extends PruebaIntegracion {

    private static final String REGISTRO = "/api/v1/registro";

    @AfterEach
    void limpiar() {
        ContextoDePrueba.limpiar();
    }

    private static String cuerpo(String ruc, String razonSocial) {
        return """
                {
                  "ruc": "%s",
                  "razonSocial": "%s",
                  "domicilioFiscal": "Av. Nueva 100, Lima",
                  "ubigeo": "150101",
                  "nombreTitular": "Titular de Prueba"
                }""".formatted(ruc, razonSocial);
    }

    @Test
    @DisplayName("Tras registrarse, el usuario puede operar de inmediato")
    void trasRegistrarseSePuedeOperar() throws Exception {
        String token = "Bearer " + tokenPara("sub-recien-llegado");

        // 20100000033 tiene dígito verificador válido; el value object Ruc
        // rechazaría uno inventado y la prueba fallaría por el motivo equivocado.
        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("20100000033", "EMPRESA RECIEN CREADA S.A.C.")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cuentaId").isNotEmpty());

        /*
         * La comprobación que importa. Con una sola empresa, la API la elige por
         * él: no hace falta cabecera. Si `usuario_empresa` no existiera, esto
         * devolvería la lista vacía y los permisos vacíos — que es exactamente
         * el fallo que se vio en dev.
         */
        mockMvc.perform(get("/api/v1/contexto").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empresas.length()").value(1))
                .andExpect(jsonPath("$.empresaActiva.ruc").value("20100000033"))
                .andExpect(jsonPath("$.cuenta.esAdministrador").value(true))
                // El rol Administrador trae todo el catálogo: si la asignación
                // se creara sin rol, esto vendría vacío.
                .andExpect(jsonPath("$.permisos.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        // Y con eso puede abrir una pantalla de configuración, que era lo que
        // fallaba: la casa matriz creada por el alta debe estar ahí.
        mockMvc.perform(get("/api/v1/configuracion/establecimientos")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("0000"));
    }

    @Test
    @DisplayName("Un RUC ya registrado se rechaza con una salida para el usuario")
    void rucRepetido() throws Exception {
        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPara("sub-primero"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("20100000041", "PRIMERA S.A.C.")))
                .andExpect(status().isCreated());

        // Otra persona, mismo RUC. No puede pasar: dos clientes emitiendo con el
        // mismo RUC producirían numeración duplicada del mismo contribuyente.
        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPara("sub-segundo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("20100000041", "SEGUNDA S.A.C.")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ruc_ya_registrado"))
                // El mensaje dice qué hacer, no solo que no se puede.
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("administrador")));
    }

    @Test
    @DisplayName("Registrarse dos veces con la misma cuenta de acceso no duplica nada")
    void registrarseDosVeces() throws Exception {
        String token = "Bearer " + tokenPara("sub-insistente");

        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("20100000068", "UNICA S.A.C.")))
                .andExpect(status().isCreated());

        // Recargar la pantalla o pulsar dos veces no debe crear una segunda
        // cuenta con la misma persona dentro.
        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("20100000076", "OTRA MAS S.A.C.")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ya_registrado"));
    }

    @Test
    @DisplayName("Un RUC con dígito verificador incorrecto no llega a la base")
    void rucConVerificadorInvalido() throws Exception {
        // 20123456789 tiene once dígitos y pasa la validación de formato del
        // controlador; lo que lo detiene es el value object Ruc, que es donde
        // vive la aritmética y donde no se puede olvidar.
        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPara("sub-con-errata"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("20123456789", "CON ERRATA S.A.C.")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ruc_invalido"));
    }

    @Test
    @DisplayName("Con un token sin correo, el alta usa el del cuerpo")
    void elCorreoLlegaDelCuerpoCuandoElTokenNoLoTrae() throws Exception {
        /*
         * El caso que rompió en produccion y no se veia desde la aplicacion.
         *
         * Los tokens de ACCESO de Cognito no llevan `email`. La primera version
         * caia a la reclamacion `username`, que en un pool con acceso por correo
         * es el UUID — asi que los usuarios registrados quedaban con su `sub`
         * guardado como correo. No fallaba nada; solo escribia basura, y se
         * descubrio inspeccionando la tabla.
         */
        String sub = "sub-sin-correo-en-el-token";

        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenSinCorreo(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ruc": "20100000092",
                                  "razonSocial": "CON CORREO EN EL CUERPO S.A.C.",
                                  "domicilioFiscal": "Av. Nueva 100, Lima",
                                  "nombreTitular": "Titular de Prueba",
                                  "correo": "titular@ejemplo.com"
                                }"""))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/contexto")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenSinCorreo(sub)))
                .andExpect(status().isOk())
                // Lo que importa: el correo, no el UUID.
                .andExpect(jsonPath("$.usuario.email").value("titular@ejemplo.com"));
    }

    @Test
    @DisplayName("Sin token no hay registro")
    void sinTokenNoHayRegistro() throws Exception {
        mockMvc.perform(post(REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("20100000084", "ANONIMA S.A.C.")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El acento sobrevive al viaje: los mensajes salen en UTF-8")
    void losMensajesConservanLosAcentos() throws Exception {
        /*
         * Guarda contra el mojibake. En Lambda salía «La operaciÃ³n», porque el
         * contenedor decodifica con ISO-8859-1 cuando el Content-Type no declara
         * charset — y `application/problem+json` no lo declara.
         *
         * Esta prueba NO reproduce ese fallo: corre sobre Tomcat, que sí aplica
         * la configuración de encoding. Lo que fija es que el mensaje lleva
         * acentos, de modo que si alguien los quita «para evitar problemas» se
         * entere de que el problema estaba en otro sitio — en ManejadorLambda.
         */
        String detalle = mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPara("sub-acentos"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("20123456789", "ACENTOS S.A.C.")))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(detalle)
                .as("el mensaje del RUC inválido lleva acentos")
                .contains("dígito verificador");
    }
}
