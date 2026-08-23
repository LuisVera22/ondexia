package com.ondexia.pruebas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * El alta de una empresa desde dentro del ERP, de extremo a extremo.
 *
 * <h2>Por qué hace falta configurar una clave para probar</h2>
 *
 * <p>Porque sin clave pública la API rechaza toda atestación —lo hace
 * {@code VerificacionPorAtestacion.SinClave}— y todas las pruebas de aquí darían
 * 400 por el mismo motivo, incluidas las que deberían pasar. Se inyecta un par
 * generado con {@code openssl} para esta prueba: no se usa en ningún entorno, y
 * está literal en el archivo para que la prueba no dependa de tener
 * {@code openssl} instalado.
 *
 * <h2>Lo que se está probando de verdad</h2>
 *
 * <p>Que no haya forma de dar de alta una empresa cuyos datos no vengan
 * firmados. El resto —el cupo, el RUC repetido— importa, pero su fallo se ve;
 * este no: una verificación permisiva dejaría pasar todos los registros sin que
 * nada pareciera roto.
 */
@SpringBootTest(properties =
        "ondexia.consultas.firma-publica="
                + "MCowBQYDK2VwAyEArH+O+lntDsX9UwpK0dFrbTzckioDM9zhI7jnoLq7URw=")
class RegistroDeEmpresaIT extends PruebaIntegracion {

    /** Par de pruebas, generado con openssl. No se usa en ningún entorno. */
    static final String PUBLICA =
            "MCowBQYDK2VwAyEArH+O+lntDsX9UwpK0dFrbTzckioDM9zhI7jnoLq7URw=";

    private static final String PRIVADA =
            "MC4CAQAwBQYDK2VwBCIEIKA3itKbjL1Lu/BLbAXSt2igZr8kb8tD5uSNcRYckQ+w";

    /** Otro par, para comprobar que una firma ajena no cuela. */
    private static final String PRIVADA_AJENA =
            "MC4CAQAwBQYDK2VwBCIEIL4UnsUNd5e0VdMWBD/XlMEGqrMnAvW0/UxC2+4ebfU1";

    private static final String EMPRESAS = "/api/v1/configuracion/empresas";

    /** RUC libre, con dígito verificador correcto. */
    private static final String RUC_NUEVO = "20601030013";

    /** El de la primera empresa de los datos de ejemplo. */
    private static final String RUC_YA_REGISTRADO = "20100000009";

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void limpiar() {
        ContextoDePrueba.limpiar();
        // El cupo se toca en algunas pruebas; se devuelve a «lo que diga el plan».
        jdbc.sql("update cuenta set limite_empresas = null where id = ?::uuid")
                .param(CUENTA_DEMO)
                .update();
        jdbc.sql("delete from usuario_empresa where empresa_id in "
                        + "(select id from empresa where ruc = ?)")
                .param(RUC_NUEVO)
                .update();
        jdbc.sql("delete from sucursal where empresa_id in "
                        + "(select id from empresa where ruc = ?)")
                .param(RUC_NUEVO)
                .update();
        jdbc.sql("delete from empresa where ruc = ?").param(RUC_NUEVO).update();
    }

    private static final String CUENTA_DEMO = "00000000-0000-4000-8000-000000000001";

    /** Sube el cupo para poder probar el camino que sí funciona. */
    private void ampliarCupo() {
        jdbc.sql("update cuenta set limite_empresas = 5 where id = ?::uuid")
                .param(CUENTA_DEMO)
                .update();
    }

    private static DatosDeRuc datos(String ruc, EstadoContribuyente estado,
            CondicionDomicilio condicion) {
        return new DatosDeRuc(new Ruc(ruc), "NUEVA EMPRESA S.A.C.", estado, condicion,
                "AV. AREQUIPA 100", new Ubigeo("150101"), "LIMA", "LIMA", "LIMA",
                false, false, "SOCIEDAD ANONIMA CERRADA", Instant.now());
    }

    private static String atestacion(DatosDeRuc datos, String privada) {
        return Atestacion.emitir(datos, Instant.now().plus(Duration.ofMinutes(10)),
                Atestacion.clavePrivada(privada));
    }

    private static String cuerpo(String atestacion) {
        return """
                {"atestacion": "%s", "nombreComercial": "La Nueva"}
                """.formatted(atestacion);
    }

    private String alta(String cuerpo) throws Exception {
        return mockMvc.perform(post(EMPRESAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    // ── Lo que no debe pasar ───────────────────────────────────────────────

    /**
     * La prueba que justifica todo el mecanismo de la firma.
     *
     * <p>Se construye una atestación bien formada con OTRA clave privada. Si la
     * verificación no comprobara la firma, esto pasaría — y con ello cualquiera
     * podría registrar una empresa declarando el estado que quisiera.
     */
    @Test
    @DisplayName("Una atestación firmada con otra clave no registra nada")
    void firmaAjenaNoVale() throws Exception {
        ampliarCupo();
        String falsa = atestacion(
                datos(RUC_NUEVO, EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO),
                PRIVADA_AJENA);

        mockMvc.perform(post(EMPRESAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(falsa)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("atestacion_invalida"));
    }

    @Test
    @DisplayName("Una atestación caducada no registra nada")
    void atestacionCaducadaNoVale() throws Exception {
        ampliarCupo();
        String vieja = Atestacion.emitir(
                datos(RUC_NUEVO, EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO),
                Instant.now().minus(Duration.ofMinutes(1)),
                Atestacion.clavePrivada(PRIVADA));

        mockMvc.perform(post(EMPRESAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(vieja)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("atestacion_invalida"));
    }

    @Test
    @DisplayName("Sin verificación no se intenta nada")
    void sinAtestacionNoVale() throws Exception {
        mockMvc.perform(post(EMPRESAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"atestacion\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Un RUC no habido no entra, aunque la firma sea nuestra.
     *
     * <p>Es la puerta del registro, y su fallo es del tipo que no se ve: la
     * empresa quedaría dada de alta y el problema aparecería en los comprobantes
     * de sus clientes, no aquí.
     */
    @Test
    @DisplayName("Un RUC no habido no se registra")
    void noHabidoNoEntra() throws Exception {
        ampliarCupo();
        String firmada = atestacion(
                datos(RUC_NUEVO, EstadoContribuyente.ACTIVO, CondicionDomicilio.NO_HABIDO),
                PRIVADA);

        mockMvc.perform(post(EMPRESAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(firmada)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ruc_no_apto"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("NO HABIDO")));
    }

    @Test
    @DisplayName("Un RUC de baja tampoco")
    void bajaNoEntra() throws Exception {
        ampliarCupo();
        String firmada = atestacion(
                datos(RUC_NUEVO, EstadoContribuyente.BAJA_DEFINITIVA, CondicionDomicilio.HABIDO),
                PRIVADA);

        mockMvc.perform(post(EMPRESAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(firmada)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ruc_no_apto"));
    }

    /**
     * El cupo del plan.
     *
     * <p>No hace falta preparar nada: la cuenta de ejemplo es PROFESIONAL —dos
     * empresas— y ya tiene dos. Que el caso difícil sea el estado por omisión de
     * los datos de desarrollo es deliberado (ver V900).
     */
    @Test
    @DisplayName("Con el cupo del plan lleno, no se registra")
    void cupoLlenoNoEntra() throws Exception {
        String firmada = atestacion(
                datos(RUC_NUEVO, EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO),
                PRIVADA);

        mockMvc.perform(post(EMPRESAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(firmada)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("limite_de_empresas"))
                // Con las cifras dentro: «has alcanzado tu limite» obliga a
                // buscar cual es.
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("2")));
    }

    @Test
    @DisplayName("Un RUC ya registrado en Ondexia da conflicto")
    void rucRepetidoDaConflicto() throws Exception {
        ampliarCupo();
        String firmada = atestacion(
                datos(RUC_YA_REGISTRADO, EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO),
                PRIVADA);

        mockMvc.perform(post(EMPRESAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(firmada)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ruc_ya_registrado"))
                // El campo, para que el mensaje salga junto al input del RUC y no
                // en un aviso de la esquina.
                // En `campos` y no en un texto suelto: es lo que permite pintar
                // el mensaje debajo del cuadro del RUC.
                .andExpect(jsonPath("$.campos.ruc").exists());
    }

    // ── Lo que sí debe pasar ──────────────────────────────────────────────

    /**
     * El camino completo, y lo que comprueba de verdad es de dónde salen los
     * datos: el cuerpo de la petición solo llevaba la atestación y un nombre
     * comercial, así que una razón social correcta en la respuesta solo puede
     * venir de la firma.
     */
    @Test
    @DisplayName("Se registra la empresa y sus datos salen de la atestación")
    void seRegistra() throws Exception {
        ampliarCupo();
        String firmada = atestacion(
                datos(RUC_NUEVO, EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO),
                PRIVADA);

        mockMvc.perform(post(EMPRESAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(firmada)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruc").value(RUC_NUEVO))
                .andExpect(jsonPath("$.razonSocial").value("NUEVA EMPRESA S.A.C."))
                .andExpect(jsonPath("$.nombreComercial").value("La Nueva"))
                .andExpect(jsonPath("$.domicilioFiscal").value("AV. AREQUIPA 100"))
                .andExpect(jsonPath("$.ubigeo").value("150101"))
                // Nace en BETA: emitir en produccion exige certificado.
                .andExpect(jsonPath("$.modoSunat").value("BETA"));
    }

    /**
     * Que se guarde lo que SUNAT dijo, con su fecha.
     *
     * <p>Sin la fecha, la base afirmaría «ACTIVO» sin decir de cuándo — y esa
     * afirmación se usa para decidir si la empresa puede emitir.
     */
    @Test
    @DisplayName("Queda guardado el estado, la condición y cuándo se comprobó")
    void seGuardaLaVerificacion() throws Exception {
        ampliarCupo();
        alta(cuerpo(atestacion(
                datos(RUC_NUEVO, EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO),
                PRIVADA)));

        var fila = jdbc.sql("""
                        select estado_contribuyente, condicion_domicilio, verificado_en,
                               distrito, tipo_societario
                          from empresa where ruc = ?
                        """)
                .param(RUC_NUEVO)
                .query()
                .singleRow();

        org.assertj.core.api.Assertions.assertThat(fila.get("estado_contribuyente"))
                .isEqualTo("ACTIVO");
        org.assertj.core.api.Assertions.assertThat(fila.get("condicion_domicilio"))
                .isEqualTo("HABIDO");
        org.assertj.core.api.Assertions.assertThat(fila.get("verificado_en"))
                .withFailMessage("un estado sin fecha es la mentira que verificado_en evita")
                .isNotNull();
        org.assertj.core.api.Assertions.assertThat(fila.get("distrito")).isEqualTo("LIMA");
        org.assertj.core.api.Assertions.assertThat(fila.get("tipo_societario"))
                .isEqualTo("SOCIEDAD ANONIMA CERRADA");
    }

    /**
     * La casa matriz.
     *
     * <p>Sin ella la empresa existe y no puede emitir: el primer intento falla
     * por un establecimiento que nadie recuerda haber tenido que crear.
     */
    @Test
    @DisplayName("Nace con su casa matriz y con quien la registró dentro")
    void nacePreparadaParaOperar() throws Exception {
        ampliarCupo();
        alta(cuerpo(atestacion(
                datos(RUC_NUEVO, EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO),
                PRIVADA)));

        Long matriz = jdbc.sql("""
                        select count(*) from sucursal s join empresa e on e.id = s.empresa_id
                         where e.ruc = ? and s.codigo = '0000'
                        """)
                .param(RUC_NUEVO)
                .query(Long.class)
                .single();
        org.assertj.core.api.Assertions.assertThat(matriz).isEqualTo(1L);

        Long asignacion = jdbc.sql("""
                        select count(*) from usuario_empresa ue
                          join empresa e on e.id = ue.empresa_id
                         where e.ruc = ? and ue.usuario_id = ?::uuid
                        """)
                .params(RUC_NUEVO, USUARIO_DEMO)
                .query(Long.class)
                .single();
        org.assertj.core.api.Assertions.assertThat(asignacion)
                .withFailMessage("sin asignación, quien la crea no la ve en el selector")
                .isEqualTo(1L);
    }

    // ── El cupo, para la pantalla ─────────────────────────────────────────

    @Test
    @DisplayName("El cupo se puede consultar antes de abrir el formulario")
    void seConsultaElCupo() throws Exception {
        mockMvc.perform(get(EMPRESAS + "/cupo")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxEmpresas").value(2))
                .andExpect(jsonPath("$.empresasUsadas").value(2))
                .andExpect(jsonPath("$.cabeOtra").value(false))
                .andExpect(jsonPath("$.motivo").value(
                        org.hamcrest.Matchers.containsString("ampliar el plan")));
    }

    /**
     * Lo pactado con la cuenta manda sobre el plan.
     *
     * <p>Es el {@code coalesce} de la V9. Olvidarlo dejaría bloqueado justo al
     * cliente que pagó una empresa adicional.
     */
    @Test
    @DisplayName("El límite negociado con la cuenta gana al del plan")
    void loPactadoGanaAlPlan() throws Exception {
        ampliarCupo();

        mockMvc.perform(get(EMPRESAS + "/cupo")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxEmpresas").value(5))
                .andExpect(jsonPath("$.cabeOtra").value(true))
                .andExpect(jsonPath("$.motivo").isEmpty());
    }
}
