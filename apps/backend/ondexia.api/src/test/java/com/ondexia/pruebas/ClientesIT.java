package com.ondexia.pruebas;

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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * Clientes (doc 12 §4.3): con DNI, con RUC verificado por el padrón y con RUC
 * sin verificar. La clave pública es la misma de {@code RegistroDeEmpresaIT}.
 */
@SpringBootTest(properties =
        "ondexia.consultas.firma-publica="
                + "MCowBQYDK2VwAyEArH+O+lntDsX9UwpK0dFrbTzckioDM9zhI7jnoLq7URw=")
class ClientesIT extends PruebaIntegracion {

    private static final String CLIENTES = "/api/v1/ventas/clientes";

    private static final String PRIVADA =
            "MC4CAQAwBQYDK2VwBCIEIKA3itKbjL1Lu/BLbAXSt2igZr8kb8tD5uSNcRYckQ+w";

    @Autowired
    private ObjectMapper json;

    private MockHttpServletRequestBuilder comoAdministrador(MockHttpServletRequestBuilder peticion) {
        return peticion.header("Authorization", autorizacionDemo())
                .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private static String atestacion(String ruc, String razonSocial) {
        var datos = new DatosDeRuc(new Ruc(ruc), razonSocial, EstadoContribuyente.ACTIVO,
                CondicionDomicilio.HABIDO, "AV. COLONIAL 2450", new Ubigeo("150101"), "LIMA",
                "LIMA", "LIMA", false, false, "SOCIEDAD ANONIMA CERRADA", Instant.now());
        return Atestacion.emitir(datos, Instant.now().plus(Duration.ofMinutes(10)),
                Atestacion.clavePrivada(PRIVADA), SUB_DEMO);
    }

    @Test
    @DisplayName("Un cliente con DNI: nombre tecleado, sin verificación")
    void clienteConDni() throws Exception {
        String creado = mockMvc.perform(comoAdministrador(post(CLIENTES)).content("""
                        {"tipoDocumento": "DNI", "numeroDocumento": "45678912",
                         "nombre": "Rosa Quispe Mamani", "correo": "Rosa@Correo.pe"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoDocumento").value("DNI"))
                .andExpect(jsonPath("$.numeroDocumento").value("45678912"))
                .andExpect(jsonPath("$.correo").value("rosa@correo.pe"))
                .andExpect(jsonPath("$.verificadoEn").doesNotExist())
                .andExpect(jsonPath("$.admiteFactura").value(false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = json.readTree(creado).path("id").asString();

        mockMvc.perform(comoAdministrador(put(CLIENTES + "/" + id)).content("""
                        {"nombre": "Rosa María Quispe Mamani", "telefono": "999 111 222"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Rosa María Quispe Mamani"))
                .andExpect(jsonPath("$.numeroDocumento").value("45678912"))
                .andExpect(jsonPath("$.correo").doesNotExist());

        mockMvc.perform(comoAdministrador(put(CLIENTES + "/" + id + "/estado")).content("{\"activo\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        // El documento repetido choca, y el error señala al campo.
        mockMvc.perform(comoAdministrador(post(CLIENTES)).content("""
                        {"tipoDocumento": "DNI", "numeroDocumento": "45678912", "nombre": "Otra"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("documento_duplicado"))
                .andExpect(jsonPath("$.campos.numeroDocumento").exists());
    }

    @Test
    @DisplayName("Con la atestación del padrón, la razón social sale de la firma y queda verificado")
    void clienteConRucVerificado() throws Exception {
        mockMvc.perform(comoAdministrador(post(CLIENTES)).content("""
                        {"tipoDocumento": "RUC", "numeroDocumento": "20512345671",
                         "nombre": "lo que tecleó el usuario", "atestacion": "%s"}
                        """.formatted(atestacion("20512345671", "DISTRIBUIDORA ANDINA S.A.C."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("DISTRIBUIDORA ANDINA S.A.C."))
                .andExpect(jsonPath("$.direccion").value("AV. COLONIAL 2450"))
                .andExpect(jsonPath("$.verificadoEn").isNotEmpty())
                .andExpect(jsonPath("$.admiteFactura").value(true));
    }

    @Test
    @DisplayName("La atestación de otro RUC no vale para este cliente")
    void atestacionDeOtroRuc() throws Exception {
        mockMvc.perform(comoAdministrador(post(CLIENTES)).content("""
                        {"tipoDocumento": "RUC", "numeroDocumento": "20601030013",
                         "nombre": "X", "atestacion": "%s"}
                        """.formatted(atestacion("20512345671", "OTRA S.A.C."))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("atestacion_de_otro_ruc"));
    }

    @Test
    @DisplayName("Sin atestación el RUC se admite, sin verificar, y se verifica después")
    void rucSinVerificarYLuegoVerificado() throws Exception {
        String creado = mockMvc.perform(comoAdministrador(post(CLIENTES)).content("""
                        {"tipoDocumento": "RUC", "numeroDocumento": "20100000009",
                         "nombre": "Comercial Demo"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificadoEn").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = json.readTree(creado).path("id").asString();

        mockMvc.perform(comoAdministrador(post(CLIENTES + "/" + id + "/verificacion")).content("""
                        {"atestacion": "%s"}
                        """.formatted(atestacion("20100000009", "COMERCIAL DEMO S.A.C."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("COMERCIAL DEMO S.A.C."))
                .andExpect(jsonPath("$.verificadoEn").isNotEmpty());
    }

    @Test
    @DisplayName("Un RUC con dígito verificador incorrecto no entra, y el error señala al campo")
    void rucInvalido() throws Exception {
        mockMvc.perform(comoAdministrador(post(CLIENTES)).content("""
                        {"tipoDocumento": "RUC", "numeroDocumento": "20601030014", "nombre": "X"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ruc_invalido"));

        mockMvc.perform(comoAdministrador(post(CLIENTES)).content("""
                        {"tipoDocumento": "DNI", "numeroDocumento": "123", "nombre": "X"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.numeroDocumento").exists());
    }

    @Test
    @DisplayName("Se busca por nombre o por documento; el catálogo 06 se publica")
    void busquedaYCatalogo() throws Exception {
        mockMvc.perform(comoAdministrador(post(CLIENTES)).content("""
                        {"tipoDocumento": "DNI", "numeroDocumento": "09876543", "nombre": "Carlos Mendoza Ríos"}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(comoAdministrador(get(CLIENTES).param("q", "mendoza")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numeroDocumento == '09876543')]").exists());
        mockMvc.perform(comoAdministrador(get(CLIENTES).param("q", "0987")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numeroDocumento").value("09876543"));

        mockMvc.perform(comoAdministrador(get(CLIENTES + "/tipos-documento")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'RUC')].codigoSunat").value("6"))
                .andExpect(jsonPath("$[?(@.codigo == 'DNI')].codigoSunat").value("1"));
    }
}
