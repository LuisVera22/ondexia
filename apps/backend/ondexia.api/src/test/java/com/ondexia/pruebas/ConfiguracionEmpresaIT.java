package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.infrastructure.seguridad.ContextoDePrueba;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Entrega 1: empresa y establecimientos, de extremo a extremo.
 *
 * <p>Van por HTTP y no llamando a los servicios: es la única forma de ejercitar
 * de una pasada el token, la resolución de contexto, la comprobación de permiso,
 * la validación del cuerpo y la traducción de errores. Un servicio probado en
 * aislamiento puede estar impecable y quedar inalcanzable por un permiso mal
 * escrito.
 */
class ConfiguracionEmpresaIT extends PruebaIntegracion {

    private static final String EMPRESA = "/api/v1/configuracion/empresa";
    private static final String EMPRESAS = "/api/v1/configuracion/empresas";
    private static final String ESTABLECIMIENTOS = "/api/v1/configuracion/establecimientos";

    @Autowired
    private com.ondexia.application.configuracion.Establecimientos establecimientos;

    @Autowired
    private RegistroDeAuditoria auditoria;

    @Autowired
    private TransactionTemplate transacciones;

    @AfterEach
    void limpiar() {
        ContextoDePrueba.limpiar();
    }

    // ── Empresa ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Se leen los datos de la empresa activa, elegida por la cabecera")
    void seLeeLaEmpresaActiva() throws Exception {
        mockMvc.perform(get(EMPRESA)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruc").value("20100000009"))
                .andExpect(jsonPath("$.activa").value(true));
    }

    @Test
    @DisplayName("Sin empresa elegida se dice eso, no «sin permisos»")
    void sinEmpresaActivaSeDistingueDeSinPermisos() throws Exception {
        // El usuario demo tiene DOS empresas, así que omitir la cabecera deja la
        // petición sin empresa que resolver. Con una sola, la API la tomaría por
        // él — de ahí que este caso necesite un usuario con varias.
        //
        // Esta prueba nació fallando con 403, y ese 403 era un defecto real: los
        // permisos son por empresa, así que sin empresa el conjunto está vacío y
        // la evaluación decía que no a todo. El usuario que acaba de entrar y
        // aún no ha elegido acababa en la pantalla «sin permisos», que no se
        // parece en nada a lo que le ocurre. Ver EvaluadorPermisos.
        mockMvc.perform(get(EMPRESA).header("Authorization", autorizacionDemo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("sin_empresa_activa"));
    }

    @Test
    @DisplayName("Actualizar cambia lo editable y deja rastro en la bitácora")
    void actualizarDejaRastro() throws Exception {
        String cuerpo = """
                {
                  "nombreComercial": "Demo Renombrada",
                  "cuentaDetracciones": "00123456789"
                }""";

        mockMvc.perform(put(EMPRESA)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreComercial").value("Demo Renombrada"))
                .andExpect(jsonPath("$.cuentaDetracciones").value("00123456789"));

        // La bitácora se consulta con el contexto puesto: la tabla tiene RLS, y
        // sin empresa activa la consulta devolvería vacío sin dar ningún error.
        ContextoDePrueba.comoUsuarioDe(
                UUID.fromString(USUARIO_DEMO),
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                UUID.fromString(EMPRESA_ADMINISTRADA));

        var historial = transacciones.execute(estado -> auditoria.historialDe(
                UUID.fromString(EMPRESA_ADMINISTRADA),
                "empresa",
                UUID.fromString(EMPRESA_ADMINISTRADA)));

        assertThat(historial)
                .as("actualizar la empresa debe registrarse")
                .isNotEmpty();
    }

    /**
     * Lo que el {@code PUT} ignora, que ahora es casi todo.
     *
     * <p>Se mandan a propósito el RUC y los tres campos que vienen del padrón.
     * Al no existir esos campos en el record, Jackson los descarta y los valores
     * permanecen: la garantía es que el caso de uso no ofrece ninguna vía, no que
     * alguien recuerde validarlo.
     *
     * <p>Importa más que un detalle de API. Una razón social que no coincide con
     * el padrón hace que SUNAT rechace <strong>todos</strong> los comprobantes de
     * esa empresa, y el fallo aparecería en la primera emisión real — no aquí.
     */
    @Test
    @DisplayName("Ni el RUC ni los datos de SUNAT se pueden cambiar por el PUT")
    void losDatosDeSunatNoSePuedenCambiar() throws Exception {
        String cuerpo = """
                {
                  "ruc": "20100000017",
                  "razonSocial": "NOMBRE INVENTADO S.A.",
                  "domicilioFiscal": "Av. Falsa 123",
                  "ubigeo": "150140",
                  "nombreComercial": "Solo esto cambia"
                }""";

        mockMvc.perform(put(EMPRESA)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruc").value("20100000009"))
                .andExpect(jsonPath("$.razonSocial").value("COMERCIAL DEMO S.A.C."))
                .andExpect(jsonPath("$.domicilioFiscal").value("Av. Siempre Viva 742, Lima"))
                .andExpect(jsonPath("$.ubigeo").value("150101"))
                .andExpect(jsonPath("$.nombreComercial").value("Solo esto cambia"));
    }

    /** Y que la respuesta diga qué es editable, para que el formulario no lo adivine. */
    @Test
    @DisplayName("La respuesta declara qué campos acepta el PUT")
    void laRespuestaDiceQueEsEditable() throws Exception {
        mockMvc.perform(get(EMPRESA)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(
                        org.hamcrest.Matchers.containsInAnyOrder(
                                "nombreComercial", "cuentaDetracciones", "regimenTributario")));
    }

    /**
     * Las empresas del onboarding no traen verificación, y eso se distingue.
     *
     * <p>{@code null} significa «nunca se comprobó», que no es lo mismo que
     * «está mal»: la interfaz tiene que poder ofrecer comprobarlo en vez de
     * acusar.
     */
    @Test
    @DisplayName("Una empresa sin verificar lo dice, no finge estar comprobada")
    void sinVerificarSeNota() throws Exception {
        mockMvc.perform(get(EMPRESA)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificadoEn").doesNotExist())
                .andExpect(jsonPath("$.estado").doesNotExist());
    }

    /** Una cuenta de detracciones con letras no entra. */
    @Test
    @DisplayName("La cuenta de detracciones solo admite dígitos")
    void cuentaDeDetraccionesSoloDigitos() throws Exception {
        mockMvc.perform(put(EMPRESA)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuentaDetracciones\":\"00-123-ABC\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("El vendedor no alcanza la configuración de la empresa, ni para leerla")
    void elVendedorNoAlcanzaLaConfiguracion() throws Exception {
        // Escrita esperando que el vendedor pudiera al menos consultar. El
        // catálogo de V2 dice otra cosa: el rol Vendedor solo recibe permisos
        // de ventas y almacén, ninguno de configuracion.*. Es coherente — quien
        // atiende el mostrador no tiene por qué ver el domicilio fiscal — así
        // que la prueba se ajusta al diseño, no al revés.
        mockMvc.perform(get(EMPRESA)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_COMO_VENDEDOR))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(EMPRESA)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_COMO_VENDEDOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreComercial\":\"Otra\"}"))
                .andExpect(status().isForbidden());
    }

    // ── Listado y ficha de empresas ────────────────────────────────────────

    @Test
    @DisplayName("El listado trae las dos empresas del usuario, no solo la activa")
    void elListadoTraeLasEmpresasDelUsuario() throws Exception {
        // Al menos las dos de la V900: RegistroDeEmpresaIT deja otras en la
        // misma cuenta, y una empresa registrada no se borra (su bitácora es de
        // solo inserción), así que no se afirma el total.
        mockMvc.perform(get(EMPRESAS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$[?(@.ruc == '20100000009')]").exists())
                .andExpect(jsonPath("$[?(@.ruc == '20100000017')]").exists());
    }

    @Test
    @DisplayName("La ficha de otra empresa del usuario se lee sin cambiar la activa")
    void laFichaDeOtraEmpresaDelUsuarioSeLee() throws Exception {
        // Es el motivo de que exista el endpoint. La empresa activa sigue siendo
        // la administrada —la cabecera no cambia— y aun así se leen los datos de
        // la segunda. Sin esto, el listado no podría abrir ninguna ficha salvo
        // la de la empresa en la que ya se está trabajando.
        mockMvc.perform(get(EMPRESAS + "/" + EMPRESA_COMO_VENDEDOR)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruc").value("20100000017"))
                .andExpect(jsonPath("$.razonSocial").value("DISTRIBUIDORA DEMO E.I.R.L."));
    }

    @Test
    @DisplayName("Una empresa que no es del usuario responde 403, no sus datos")
    void unaEmpresaAjenaNoSeLee() throws Exception {
        // El id no está entre las asignaciones del usuario, así que la
        // comprobación corta antes de tocar el repositorio. Importa que sea 403
        // y no 404: el 404 confirmaría, por descarte, qué UUID sí existen.
        mockMvc.perform(get(EMPRESAS + "/" + UUID.randomUUID())
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("acceso_denegado"));
    }

    // ── Establecimientos ───────────────────────────────────────────────────

    @Test
    @DisplayName("Alta, listado, edición y desactivación de un establecimiento")
    void cicloCompletoDeEstablecimiento() throws Exception {
        String creado = mockMvc.perform(post(ESTABLECIMIENTOS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "codigo": "0007",
                                  "nombre": "Tienda Surco",
                                  "direccion": "Av. Primavera 500",
                                  "ubigeo": "150140"
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("0007"))
                .andExpect(jsonPath("$.activa").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        String id = creado.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        // Nace con su almacén y su primera caja: un local sin ellos no vende.
        mockMvc.perform(get("/api/v1/almacen/almacenes")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sucursalId == '" + id + "')].codigo").value("ALM-0007"));
        mockMvc.perform(get("/api/v1/ventas/cajas")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sucursalId == '" + id + "')].codigo").value("CAJA1"));

        mockMvc.perform(put(ESTABLECIMIENTOS + "/" + id)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Tienda Surco 2\",\"direccion\":\"Av. Primavera 900\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Tienda Surco 2"))
                // El código no está en el cuerpo de edición y debe sobrevivir.
                .andExpect(jsonPath("$.codigo").value("0007"));

        mockMvc.perform(put(ESTABLECIMIENTOS + "/" + id + "/estado")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));

        // Sigue apareciendo en el listado, desactivado. Desaparecer seria
        // perder la referencia de los comprobantes ya emitidos.
        mockMvc.perform(get(ESTABLECIMIENTOS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == '0007')].activa").value(false));

        /*
         * Y vuelve. Esto es lo que faltaba: solo existia el camino de ida —un
         * DELETE—, asi que desactivar un establecimiento no se podia deshacer
         * desde ninguna capa, ni siquiera desde el dominio, que no tenia un
         * `activar`. El dialogo de la pantalla llegaba a prometer «se puede
         * volver a activar», y era falso.
         *
         * Se comprueba que el codigo sobrevive: es lo que ata la serie al anexo
         * ante SUNAT, y reactivar tiene que devolver el mismo local y no uno
         * nuevo que se llame igual.
         */
        mockMvc.perform(put(ESTABLECIMIENTOS + "/" + id + "/estado")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.codigo").value("0007"))
                .andExpect(jsonPath("$.nombre").value("Tienda Surco 2"));

        // Repetirlo no es un error: la pantalla puede reintentar tras un fallo
        // de red sin saber si la primera peticion llego.
        mockMvc.perform(put(ESTABLECIMIENTOS + "/" + id + "/estado")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true));

        // Y el estado no se adivina: sin el, la peticion no dice que se quiere.
        mockMvc.perform(put(ESTABLECIMIENTOS + "/" + id + "/estado")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.activa").exists());
    }

    @Test
    @DisplayName("Un código repetido en la misma empresa es conflicto, no error interno")
    void codigoRepetido() throws Exception {
        // 0000 ya existe en los datos de ejemplo.
        mockMvc.perform(post(ESTABLECIMIENTOS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"0000\",\"nombre\":\"Duplicada\",\"direccion\":\"Av. Uno 1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("codigo_duplicado"))
                // El campo importa tanto como el codigo: el cliente coloca el
                // mensaje debajo del cuadro que lo tiene, y sin esto acababa en
                // un aviso de la esquina sin decir cual de los tres corregir.
                .andExpect(jsonPath("$.campos.codigo").exists());
    }

    @Test
    @DisplayName("El mensaje de un campo demasiado largo es nuestro, no el de la libreria")
    void mensajeDeLongitudEsPropio() throws Exception {
        // Sin ValidationMessages.properties, aqui salia «el tamano debe estar
        // entre 0 y 200»: el texto de fabrica de Hibernate Validator, que no
        // escribio nadie para que un usuario lo leyera.
        mockMvc.perform(post(ESTABLECIMIENTOS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"0091\",\"nombre\":\"" + "N".repeat(400)
                                + "\",\"direccion\":\"Av. Uno 1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").value("No puede pasar de 200 caracteres."));
    }

    @Test
    @DisplayName("Un código que no son cuatro dígitos se rechaza antes de tocar la base")
    void codigoConFormatoInvalido() throws Exception {
        mockMvc.perform(post(ESTABLECIMIENTOS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"7\",\"nombre\":\"Mal codigo\",\"direccion\":\"Av. Uno 1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("No se alcanza un establecimiento de otra empresa")
    void noSeAlcanzaElDeOtraEmpresa() throws Exception {
        // Se crea en la empresa que administra...
        String creado = mockMvc.perform(post(ESTABLECIMIENTOS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"0042\",\"nombre\":\"Solo de la A\",\"direccion\":\"Av. Sola 1\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        String id = creado.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        /*
         * ...y se intenta editar declarando la OTRA empresa como activa.
         *
         * Sale 403 y no 404, y conviene entender por qué: hay DOS barreras y
         * salta la de fuera. En la segunda empresa el usuario es Vendedor, que
         * no tiene `configuracion.sucursal:editar`, así que la autorización
         * corta antes de que el caso de uso llegue a mirar de quién es el
         * establecimiento.
         *
         * El filtro por empresa sigue ahí y lo cubre `noSeVeElDeOtraEmpresa`,
         * que usa una acción que el Vendedor sí puede ejecutar. Que la barrera
         * externa dispare primero es exactamente lo que se quiere; lo que no
         * hay que hacer es confundir «no llegó» con «no existe la protección».
         */
        mockMvc.perform(put(ESTABLECIMIENTOS + "/" + id)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_COMO_VENDEDOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Robada\",\"direccion\":\"Av. Robada 1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El listado de una empresa no incluye establecimientos de la otra")
    void noSeVeElDeOtraEmpresa() throws Exception {
        /*
         * Esta va por el caso de uso y no por HTTP, y la razón es informativa:
         * NO EXISTE ninguna empresa donde el usuario demo pueda listar
         * establecimientos y no sea administrador. El rol Vendedor solo recibe
         * permisos de ventas y almacén, así que por HTTP la autorización corta
         * siempre antes y el filtro por empresa nunca se llega a ejercitar.
         *
         * Probarlo por HTTP daría un verde que no significa nada: pasaría por el
         * 403, no por el filtro. Aquí se ataca el filtro directamente.
         */
        mockMvc.perform(post(ESTABLECIMIENTOS)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"0055\",\"nombre\":\"Privada de A\",\"direccion\":\"Av. A 1\"}"))
                .andExpect(status().isCreated());

        ContextoDePrueba.comoUsuarioDe(
                UUID.fromString(USUARIO_DEMO),
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                UUID.fromString(EMPRESA_COMO_VENDEDOR));

        assertThat(establecimientos.listar())
                .as("la empresa B no debe ver un establecimiento de la empresa A")
                .noneMatch(sucursal -> "0055".equals(sucursal.codigo()));

        ContextoDePrueba.comoUsuarioDe(
                UUID.fromString(USUARIO_DEMO),
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                UUID.fromString(EMPRESA_ADMINISTRADA));

        assertThat(establecimientos.listar())
                .as("su propia empresa si debe verlo")
                .anyMatch(sucursal -> "0055".equals(sucursal.codigo()));
    }
}
