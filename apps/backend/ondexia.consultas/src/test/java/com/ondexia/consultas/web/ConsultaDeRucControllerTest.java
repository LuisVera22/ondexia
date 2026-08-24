package com.ondexia.consultas.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.Ubigeo;
import com.ondexia.domain.consultas.Atestacion;
import com.ondexia.domain.consultas.CondicionDomicilio;
import com.ondexia.domain.consultas.ConsultaDeRuc;
import com.ondexia.domain.consultas.ConsultaNoDisponible;
import com.ondexia.domain.consultas.DatosDeRuc;
import com.ondexia.domain.consultas.EstadoContribuyente;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La ruta de consulta, con el contexto de Spring de verdad.
 *
 * <h2>Por qué esta prueba existe</h2>
 *
 * <p>Porque al pasar el módulo a Spring, lo que puede romperse ya no es la
 * lógica —esa la cubren {@code CascadaDeProveedoresTest} y
 * {@code VocabularioDeSunatTest}— sino el <strong>montaje</strong>: un
 * {@code @ConfigurationProperties} que no se enlaza, un bean que falta, un
 * {@code @RestControllerAdvice} que no se registra, un campo que Jackson
 * serializa con otro nombre.
 *
 * <p>Nada de eso lo detecta un compilador, y todo se manifiesta igual: la
 * aplicación arranca y el endpoint responde mal. Antes esto se comprobaba
 * arrancando el servidor a mano; con Spring, arrancar el contexto es una prueba.
 *
 * <h2>El proveedor va sustituido</h2>
 *
 * <p>La cascada real construye clientes HTTP contra Decolecta. Dejarla en pie
 * mediría además su disponibilidad, fallaría los días que tengan un mal rato y
 * gastaría cupo de un plan de pago en cada ejecución.
 */
@SpringBootTest(properties = {
    // Un par de openssl, solo para esta prueba. Sin clave el contexto no
    // arranca, que es justo lo que se quiere en produccion.
    "ondexia.consultas.firma-privada="
            + "MC4CAQAwBQYDK2VwBCIEIKA3itKbjL1Lu/BLbAXSt2igZr8kb8tD5uSNcRYckQ+w",
    // Hace falta para que la cascada real se construya. No se llama: el bean
    // sustituto es el primario.
    "ondexia.consultas.decolecta-token=no-se-usa",
})
@AutoConfigureMockMvc
class ConsultaDeRucControllerTest {

    private static final String PUBLICA =
            "MCowBQYDK2VwAyEArH+O+lntDsX9UwpK0dFrbTzckioDM9zhI7jnoLq7URw=";

    private static final String RUC = "20601030013";

    /**
     * Lo que responde el padrón en cada prueba.
     *
     * <p>Estático porque el bean se crea una vez con el contexto, y cada prueba
     * reasigna la función antes de llamar.
     */
    private static Function<Ruc, Optional<DatosDeRuc>> respuesta;

    @TestConfiguration
    static class PadronDeMentira {

        @Bean
        @Primary
        ConsultaDeRuc padronSustituido() {
            return ruc -> respuesta.apply(ruc);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private static DatosDeRuc datos(EstadoContribuyente estado, CondicionDomicilio condicion) {
        return new DatosDeRuc(new Ruc(RUC), "ONDEXIA S.A.C.", estado, condicion,
                "AV. AREQUIPA 100", new Ubigeo("150101"), "LIMA", "LIMA", "LIMA",
                true, false, "SOCIEDAD ANONIMA CERRADA", Instant.parse("2026-08-23T10:00:00Z"));
    }

    /**
     * El camino completo, y lo que de verdad comprueba es que la atestación que
     * sale la puede verificar la clave pública del par. Si el contexto montara
     * otra clave —o ninguna— la firma no cuadraría.
     */
    @Test
    @DisplayName("Devuelve los datos y una atestación que la pública verifica")
    void consultaYFirma() throws Exception {
        respuesta = ruc -> Optional.of(datos(
                EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO));

        String cuerpo = mockMvc.perform(get("/consultas/ruc/" + RUC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.ruc").value(RUC))
                .andExpect(jsonPath("$.datos.razonSocial").value("ONDEXIA S.A.C."))
                .andExpect(jsonPath("$.datos.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.datos.condicion").value("HABIDO"))
                .andExpect(jsonPath("$.datos.ubigeo").value("150101"))
                .andExpect(jsonPath("$.datos.distrito").value("LIMA"))
                .andExpect(jsonPath("$.datos.esAgenteRetencion").value(true))
                .andExpect(jsonPath("$.datos.tipoSocietario").value("SOCIEDAD ANONIMA CERRADA"))
                // Calculado en el servidor para que el formulario no repita la
                // regla: dos implementaciones de la puerta del registro
                // acabarian discrepando.
                .andExpect(jsonPath("$.datos.aptaParaRegistro").value(true))
                .andExpect(jsonPath("$.atestacion").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String atestacion = cuerpo.replaceAll(".*\"atestacion\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        PublicKey publica = Atestacion.clavePublica(PUBLICA);

        // Verifica de verdad: si el contexto hubiera montado otra clave, esto
        // lanzaria AtestacionInvalida.
        Atestacion.verificar(atestacion, publica, Instant.now());
    }

    /**
     * Un RUC no apto se devuelve con su motivo y con {@code aptaParaRegistro} en
     * falso — no se rechaza con un error.
     *
     * <p>Es deliberado: quien consulta tiene que poder leer <em>por qué</em> no
     * puede registrar esa empresa. Devolver 400 dejaría el motivo en un mensaje
     * genérico.
     */
    @Test
    @DisplayName("Un RUC no habido se devuelve con su motivo, no como error")
    void noHabidoSeDevuelveConMotivo() throws Exception {
        respuesta = ruc -> Optional.of(datos(
                EstadoContribuyente.ACTIVO, CondicionDomicilio.NO_HABIDO));

        mockMvc.perform(get("/consultas/ruc/" + RUC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.aptaParaRegistro").value(false))
                .andExpect(jsonPath("$.datos.motivoDeRechazo").value(
                        org.hamcrest.Matchers.containsString("NO HABIDO")));
    }

    @Test
    @DisplayName("Un RUC que el padrón no conoce da 404, no un fallo del servicio")
    void noEncontrado() throws Exception {
        respuesta = ruc -> Optional.empty();

        mockMvc.perform(get("/consultas/ruc/" + RUC))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ruc_no_encontrado"))
                .andExpect(jsonPath("$.reintentable").value(false));
    }

    /**
     * Que `reintentable` llegue al cuerpo.
     *
     * <p>Sin él el cliente tiene que adivinar por el código si merece la pena
     * ofrecer «volver a intentarlo», y las dos formas de adivinar mal cuestan.
     */
    @Test
    @DisplayName("Un fallo del proveedor da 503 y dice si conviene reintentar")
    void noDisponibleDiceSiReintentar() throws Exception {
        respuesta = ruc -> {
            throw new ConsultaNoDisponible("consulta_no_disponible", "caído", true);
        };

        mockMvc.perform(get("/consultas/ruc/" + RUC))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("consulta_no_disponible"))
                .andExpect(jsonPath("$.reintentable").value(true));
    }

    @Test
    @DisplayName("Y si el fallo no es pasajero, no invita a esperar")
    void noDisponibleSinReintento() throws Exception {
        respuesta = ruc -> {
            throw new ConsultaNoDisponible("consulta_no_disponible", "credenciales", false);
        };

        mockMvc.perform(get("/consultas/ruc/" + RUC))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reintentable").value(false));
    }

    /**
     * El dígito verificador se rechaza <strong>sin salir a la red</strong>: es el
     * error más frecuente y no hay razón para gastar una consulta de un plan de
     * pago en una errata de tecleo.
     */
    @Test
    @DisplayName("Un RUC con el verificador mal no llega al proveedor")
    void verificadorInvalido() throws Exception {
        respuesta = ruc -> {
            throw new AssertionError("no debería haberse consultado");
        };

        mockMvc.perform(get("/consultas/ruc/20123456789"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ruc_invalido"));
    }

    @Test
    @DisplayName("Y uno que no tiene once dígitos tampoco")
    void formatoInvalido() throws Exception {
        respuesta = ruc -> {
            throw new AssertionError("no debería haberse consultado");
        };

        mockMvc.perform(get("/consultas/ruc/123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ruc_invalido"));
    }
}
