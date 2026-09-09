package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.Ubigeo;
import com.ondexia.domain.consultas.Atestacion;
import com.ondexia.domain.consultas.CondicionDomicilio;
import com.ondexia.domain.consultas.DatosDeRuc;
import com.ondexia.domain.consultas.EstadoContribuyente;
import com.ondexia.infrastructure.seguridad.ContextoDePrueba;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
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
 *
 * <h2>Qué cambió al validar el RUC en el alta</h2>
 *
 * <p>El cuerpo ya no lleva RUC, razón social, domicilio ni ubigeo: los cuatro
 * salen de una atestación firmada. Antes bastaba un RUC bien formado —el dígito
 * verificador cuadraba y nada más se comprobaba— así que se podía crear una
 * cuenta con un contribuyente inexistente y la razón social que a uno le
 * pareciera.
 *
 * <p>Dos pruebas desaparecieron de aquí por eso, y no porque dejaran de
 * importar: las del dígito verificador y de los acentos en su mensaje. Ese
 * mensaje ya no lo produce este endpoint —el RUC no llega como cadena— sino
 * {@code ondexia.consultas} al consultarlo. Su sitio es ahí.
 */
@SpringBootTest(properties =
        "ondexia.consultas.firma-publica="
                + "MCowBQYDK2VwAyEArH+O+lntDsX9UwpK0dFrbTzckioDM9zhI7jnoLq7URw=")
class RegistroIT extends PruebaIntegracion {

    private static final String REGISTRO = "/api/v1/registro";

    /** Par de pruebas, generado con openssl. No se usa en ningún entorno. */
    private static final String PRIVADA =
            "MC4CAQAwBQYDK2VwBCIEIKA3itKbjL1Lu/BLbAXSt2igZr8kb8tD5uSNcRYckQ+w";

    private static final String PRIVADA_AJENA =
            "MC4CAQAwBQYDK2VwBCIEIL4UnsUNd5e0VdMWBD/XlMEGqrMnAvW0/UxC2+4ebfU1";

    @AfterEach
    void limpiar() {
        ContextoDePrueba.limpiar();
    }

    private static DatosDeRuc padron(String ruc, String razonSocial,
            EstadoContribuyente estado, CondicionDomicilio condicion) {
        return new DatosDeRuc(new Ruc(ruc), razonSocial, estado, condicion,
                "Av. Nueva 100, Lima", new Ubigeo("150101"), "LIMA", "LIMA", "LIMA",
                false, false, "SOCIEDAD ANONIMA CERRADA", Instant.now());
    }

    /** Firmada para {@code sub}: desde M17 la atestación solo vale para él. */
    private static String firmar(DatosDeRuc datos, String privada, String sub) {
        return Atestacion.emitir(datos, Instant.now().plus(Duration.ofMinutes(10)),
                Atestacion.clavePrivada(privada), sub);
    }

    /** El cuerpo del alta: la verificación del RUC y quién eres. Nada más. */
    private static String cuerpo(String atestacion) {
        return """
                {
                  "atestacion": "%s",
                  "nombreTitular": "Titular",
                  "apellidoTitular": "De Prueba"
                }""".formatted(atestacion);
    }

    private static String cuerpoPara(String ruc, String razonSocial, String sub) {
        return cuerpo(firmar(
                padron(ruc, razonSocial, EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO),
                PRIVADA, sub));
    }

    @Test
    @DisplayName("Tras registrarse, el usuario puede operar de inmediato")
    void trasRegistrarseSePuedeOperar() throws Exception {
        String token = "Bearer " + tokenPara("sub-recien-llegado");

        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPara("20100000033", "EMPRESA RECIEN CREADA S.A.C.", "sub-recien-llegado")))
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
                .andExpect(jsonPath("$.permisos.length()").value(
                        org.hamcrest.Matchers.greaterThan(0)));

        // Y con eso puede abrir una pantalla de configuración, que era lo que
        // fallaba: la casa matriz creada por el alta debe estar ahí.
        mockMvc.perform(get("/api/v1/configuracion/establecimientos")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("0000"));

        // Y puede vender desde el primer día: la matriz nace con su almacén y su
        // primera caja (iteración 2). Estas dos tablas están bajo RLS, así que
        // verlas desde la API prueba además que el alta las escribió bajo la
        // empresa correcta.
        mockMvc.perform(get("/api/v1/almacen/almacenes")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigo").value("PRINCIPAL"))
                .andExpect(jsonPath("$[0].sucursalId").isNotEmpty());

        mockMvc.perform(get("/api/v1/ventas/cajas")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigo").value("CAJA1"))
                .andExpect(jsonPath("$[0].activa").value(true))
                .andExpect(jsonPath("$[0].sesionAbierta").doesNotExist());
    }

    /**
     * La razón social y el domicilio salen del padrón, no del formulario.
     *
     * <p>Es lo que la atestación compra: el cuerpo de la petición no tiene esos
     * campos, así que una razón social correcta en la empresa creada solo puede
     * venir de la firma.
     */
    @Test
    @DisplayName("Los datos de la empresa creada salen de la atestación")
    void losDatosSalenDelPadron() throws Exception {
        String token = "Bearer " + tokenPara("sub-datos-del-padron");

        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPara("20100001005", "LO QUE DICE SUNAT S.A.C.", "sub-datos-del-padron")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/configuracion/empresa")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razonSocial").value("LO QUE DICE SUNAT S.A.C."))
                .andExpect(jsonPath("$.domicilioFiscal").value("Av. Nueva 100, Lima"))
                .andExpect(jsonPath("$.ubigeo").value("150101"))
                // Nace verificada: la fecha existe desde el primer dia.
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.condicion").value("HABIDO"))
                .andExpect(jsonPath("$.verificadoEn").exists())
                .andExpect(jsonPath("$.tipoSocietario").value("SOCIEDAD ANONIMA CERRADA"));
    }

    /**
     * La puerta del onboarding.
     *
     * <p>Sin esto, cualquiera se registra con un RUC de baja o no habido, y el
     * problema aparece cuando sus clientes pierden el crédito fiscal — no aquí.
     */
    @Test
    @DisplayName("Un RUC no habido no puede crear una cuenta")
    void unRucNoHabidoNoCreaCuenta() throws Exception {
        String firmada = firmar(padron("20100001013", "NO HABIDA S.A.C.",
                EstadoContribuyente.ACTIVO, CondicionDomicilio.NO_HABIDO), PRIVADA, "sub-no-habido");

        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPara("sub-no-habido"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(firmada)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ruc_no_apto"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("NO HABIDO")));
    }

    @Test
    @DisplayName("Un RUC de baja tampoco")
    void unRucDeBajaNoCreaCuenta() throws Exception {
        String firmada = firmar(padron("20100001021", "DE BAJA S.A.C.",
                EstadoContribuyente.BAJA_DEFINITIVA, CondicionDomicilio.HABIDO), PRIVADA, "sub-de-baja");

        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPara("sub-de-baja"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(firmada)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ruc_no_apto"));
    }

    /**
     * Y la prueba que sostiene todo lo anterior: una atestación firmada con otra
     * clave no vale. Si la verificación fuera permisiva, las dos pruebas de
     * arriba pasarían igual construyendo la firma con cualquier clave.
     */
    @Test
    @DisplayName("Una atestación firmada con otra clave no crea nada")
    void firmaAjenaNoCreaCuenta() throws Exception {
        String falsa = firmar(padron("20100001030", "FALSIFICADA S.A.C.",
                EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO), PRIVADA_AJENA, "sub-falsario");

        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPara("sub-falsario"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(falsa)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("atestacion_invalida"));
    }

    @Test
    @DisplayName("Una atestación caducada tampoco")
    void atestacionCaducadaNoCreaCuenta() throws Exception {
        String vieja = Atestacion.emitir(
                padron("20100001048", "TARDONA S.A.C.",
                        EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO),
                Instant.now().minus(Duration.ofMinutes(1)),
                Atestacion.clavePrivada(PRIVADA), "sub-tardon");

        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPara("sub-tardon"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(vieja)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("atestacion_invalida"));
    }

    /**
     * El rechazo NO puede confirmar que ese RUC este registrado.
     *
     * Hallazgos C2 y M16 de la auditoria 2026-09-01: el mensaje decia «El RUC X
     * ya está registrado en Ondexia», con lo que cualquiera con una cuenta podia
     * recorrer el padron y quedarse con la lista de contribuyentes que son
     * clientes nuestros.
     *
     * La prueba fija las dos mitades: que siga habiendo una salida para quien
     * tiene un motivo legitimo, y que ni el codigo ni el texto digan que el RUC
     * existe.
     */
    @Test
    @DisplayName("Un RUC ya registrado se rechaza sin confirmar que lo esta")
    void rucRepetido() throws Exception {
        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPara("sub-primero"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPara("20100000041", "PRIMERA S.A.C.", "sub-primero")))
                .andExpect(status().isCreated());

        // Otra persona, mismo RUC. No puede pasar: dos clientes emitiendo con el
        // mismo RUC producirían numeración duplicada del mismo contribuyente.
        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPara("sub-segundo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPara("20100000041", "SEGUNDA S.A.C.", "sub-segundo")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("registro_no_disponible"))
                // Sigue diciendo qué hacer, que es lo que necesita quien de
                // verdad es de esa empresa.
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("administrador")))
                // Y no confirma nada. Ni el RUC ni la palabra «registrado»:
                // sin esto, el mensaje seguiría siendo un oráculo aunque el
                // código hubiera cambiado.
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("20100000041"))))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("registrado"))));
    }

    @Test
    @DisplayName("Registrarse dos veces con la misma cuenta de acceso no duplica nada")
    void registrarseDosVeces() throws Exception {
        String token = "Bearer " + tokenPara("sub-insistente");

        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPara("20100000068", "UNICA S.A.C.", "sub-insistente")))
                .andExpect(status().isCreated());

        // Recargar la pantalla o pulsar dos veces no debe crear una segunda
        // cuenta con la misma persona dentro.
        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPara("20100000076", "OTRA MAS S.A.C.", "sub-insistente")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ya_registrado"));
    }

    /**
     * La inversión de «con un token sin correo, el alta usa el del cuerpo».
     *
     * <p>Aquella prueba fijaba un respaldo: si el token de acceso no traía
     * `email`, se aceptaba el del cuerpo. Tabla de bajas de la auditoría
     * 2026-09-01: eso dejaba nacer la fila `usuario` con un correo que nadie
     * había verificado. Ahora el alta exige el token de identidad —el que trae
     * el correo— y un token sin él no crea nada.
     */
    @Test
    @DisplayName("Con un token sin correo no hay alta, aunque el cuerpo lo traiga")
    void sinCorreoEnElTokenNoHayAlta() throws Exception {
        String sub = "sub-sin-correo-en-el-token";
        String atestacion = firmar(padron("20100000092", "CON CORREO EN EL CUERPO S.A.C.",
                EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO), PRIVADA, sub);

        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenSinCorreo(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "atestacion": "%s",
                                  "nombreTitular": "Titular",
                                  "apellidoTitular": "De Prueba",
                                  "correo": "titular@ejemplo.com"
                                }""".formatted(atestacion)))
                .andExpect(status().isUnauthorized());

        // Y no quedo nada a medias: el contexto sigue diciendo «sin registrar».
        mockMvc.perform(get("/api/v1/contexto")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenSinCorreo(sub)))
                .andExpect(status().isNotFound());
    }

    /**
     * El reenvío (hallazgo M17): una atestación válida presentada por otra
     * persona.
     *
     * <p>Es el escenario que la caducidad no cubría. Quien consulta un RUC recibe
     * una firma que vale diez minutos; si otra cuenta la obtiene en ese rato
     * —una pestaña compartida, un proxy, un registro—, antes podía registrarse
     * con ella. Ahora la firma dice para quién es.
     */
    @Test
    @DisplayName("Una atestación pedida por otra persona no sirve para registrarse")
    void laAtestacionDeOtroNoVale() throws Exception {
        String deOtro = firmar(padron("20100000106", "PRESTADA S.A.C.",
                EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO), PRIVADA, "sub-el-que-consulto");

        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPara("sub-el-que-la-reenvia"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(deOtro)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("atestacion_invalida"));
    }

    @Test
    @DisplayName("Sin token no hay registro")
    void sinTokenNoHayRegistro() throws Exception {
        mockMvc.perform(post(REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPara("20100000084", "ANONIMA S.A.C.", "sub-anonimo")))
                .andExpect(status().isUnauthorized());
    }

    /**
     * El acento sobrevive al viaje.
     *
     * <p>Guarda contra el mojibake. En Lambda salía «La operaciÃ³n», porque el
     * contenedor decodifica con ISO-8859-1 cuando el {@code Content-Type} no
     * declara charset — y {@code application/problem+json} no lo declara.
     *
     * <p>Esta prueba NO reproduce ese fallo: corre sobre Tomcat, que sí aplica la
     * configuración de encoding. Lo que fija es que un mensaje de error lleva
     * acentos, de modo que si alguien los quita «para evitar problemas» se entere
     * de que el problema estaba en otro sitio — en {@code ManejadorLambda}.
     *
     * <p>Antes usaba el mensaje del dígito verificador, que ya no se produce
     * aquí. Ahora usa el de la atestación caducada, que sí.
     */
    @Test
    @DisplayName("El acento sobrevive al viaje: los mensajes salen en UTF-8")
    void losMensajesConservanLosAcentos() throws Exception {
        String vieja = Atestacion.emitir(
                padron("20100001056", "ACENTOS S.A.C.",
                        EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO),
                Instant.now().minus(Duration.ofMinutes(1)),
                Atestacion.clavePrivada(PRIVADA), "sub-acentos");

        String detalle = mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPara("sub-acentos"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(vieja)))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(detalle)
                .as("el mensaje de la atestación caducada lleva acentos")
                .contains("caducó");
    }

    // ── Quien se registra y que emite (doc 12 §3.1) ────────────────────────

    @Test
    @DisplayName("Un RUC 15 no se registra: Ondexia admite personas naturales (10) y jurídicas (20)")
    void unRuc15NoSeRegistra() throws Exception {
        String token = "Bearer " + tokenPara("sub-sucesion");

        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPara("15123456782", "SUCESION INDIVISA DE PRUEBA",
                                "sub-sucesion")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("tipo_de_contribuyente_no_admitido"));
    }

    @Test
    @DisplayName("Una persona natural en el Nuevo RUS queda sin factura desde el alta")
    void nuevoRusDeshabilitaLaFactura() throws Exception {
        String token = "Bearer " + tokenPara("sub-nuevo-rus");
        String atestacion = firmar(
                padron("10123456781", "BODEGA DE PRUEBA", EstadoContribuyente.ACTIVO,
                        CondicionDomicilio.HABIDO),
                PRIVADA, "sub-nuevo-rus");

        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "atestacion": "%s",
                                  "nombreTitular": "Bodeguera",
                                  "apellidoTitular": "De Prueba",
                                  "nuevoRus": true
                                }""".formatted(atestacion)))
                .andExpect(status().isCreated());

        // La factura no se emite y la boleta si; la casilla no puede encenderla.
        mockMvc.perform(get("/api/v1/configuracion/comprobantes")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == '01')].emite")
                        .value(org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$[?(@.codigo == '03')].emite")
                        .value(org.hamcrest.Matchers.contains(true)));

        mockMvc.perform(put("/api/v1/configuracion/comprobantes/01")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emite\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("nuevo_rus_no_emite_facturas"));

        mockMvc.perform(get("/api/v1/configuracion/empresa")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regimenTributario").value("NUEVO_RUS"))
                .andExpect(jsonPath("$.emiteFacturas").value(false));
    }

    @Test
    @DisplayName("Una persona jurídica no puede declararse en el Nuevo RUS")
    void unaJuridicaNoEstaEnElNuevoRus() throws Exception {
        String token = "Bearer " + tokenPara("sub-juridica-rus");
        String atestacion = firmar(
                padron("20123456786", "JURIDICA EN RUS S.A.C.", EstadoContribuyente.ACTIVO,
                        CondicionDomicilio.HABIDO),
                PRIVADA, "sub-juridica-rus");

        mockMvc.perform(post(REGISTRO)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "atestacion": "%s",
                                  "nombreTitular": "Gerente",
                                  "apellidoTitular": "De Prueba",
                                  "nuevoRus": true
                                }""".formatted(atestacion)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("nuevo_rus_solo_persona_natural"));
    }
}
