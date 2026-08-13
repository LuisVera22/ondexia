package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.application.configuracion.Identidad;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.infrastructure.salida.marca.AlmacenDeMarcaEnMemoria;
import com.ondexia.infrastructure.seguridad.ContextoDePrueba;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Identidad visual: los logos de la empresa.
 *
 * <p>El archivo no pasa por la API —el navegador lo sube directo al almacén con
 * una URL firmada— así que lo que hay que probar aquí no es la subida sino
 * <strong>la desconfianza</strong>: que entre firmar y confirmar puede pasar
 * cualquier cosa, y que el servidor comprueba con el almacén qué llegó de verdad
 * antes de guardar nada.
 *
 * <p>El doble en memoria no da por subido lo que se firma: hay que llamar a
 * {@code simularSubida} igual que en producción hay que subir. Un doble que
 * subiera solo escondería el caso más probable — la subida que no se completó.
 */
class IdentidadVisualIT extends PruebaIntegracion {

    private static final String IDENTIDAD = "/api/v1/configuracion/identidad";
    private static final UUID CUENTA = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private static final String PNG = "image/png";
    private static final long UN_PNG_RAZONABLE = 120_000L;

    @Autowired
    private Identidad identidad;

    @Autowired
    private AlmacenDeMarcaEnMemoria almacen;

    @AfterEach
    void limpiar() {
        ContextoDePrueba.limpiar();
    }

    private void comoDemo() {
        ContextoDePrueba.comoUsuarioDe(
                UUID.fromString(USUARIO_DEMO), CUENTA, UUID.fromString(EMPRESA_ADMINISTRADA));
    }

    /** Firma, sube y confirma: el recorrido completo, como lo haría el navegador. */
    private String subirYConfirmar(String logo, String tipo, long bytes) {
        var autorizacion = identidad.autorizarSubida(logo, tipo, bytes);
        almacen.simularSubida(autorizacion.clave(), tipo, bytes);
        identidad.confirmarSubida(logo, autorizacion.clave());
        return autorizacion.clave();
    }

    // ── El recorrido feliz ──────────────────────────────────────────────────

    @Test
    @DisplayName("La pantalla recibe los tres huecos con su límite")
    void losTresHuecosLleganSiempre() throws Exception {
        /*
         * Sobre EMPRESA_ADMINISTRADA y no sobre la otra: el usuario de ejemplo
         * es VENDEDOR en la segunda, y Vendedor no tiene `configuracion.identidad`
         * —responde 403 y la prueba fallaría por el motivo equivocado—.
         *
         * Y no se comprueba que estén vacíos, porque la suite comparte base y
         * otra prueba puede haber subido antes. Que empiezan vacíos lo fija
         * elAislamientoLoPoneLaBase, que usa una empresa que nadie toca.
         */
        mockMvc.perform(get(IDENTIDAD)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                // Los tres siempre, cargados o no: la pantalla es una lista de
                // huecos, no de archivos existentes.
                .andExpect(jsonPath("$.length()").value(3))
                // El máximo viaja para poder avisar ANTES de subir, en vez de
                // dejar que el usuario espere y falle.
                .andExpect(jsonPath("$[?(@.logo == 'logo_ticket')].maximoBytes").value(262144))
                .andExpect(jsonPath("$[?(@.logo == 'logo_principal')].maximoBytes").value(1048576));
    }

    @Test
    @DisplayName("Autorizar, subir y confirmar deja el logo publicado")
    void recorridoCompleto() throws Exception {
        String cuerpo = """
                {"tipoContenido":"image/png","bytes":%d}""".formatted(UN_PNG_RAZONABLE);

        String respuesta = mockMvc.perform(post(IDENTIDAD + "/logo_principal/subida")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").isNotEmpty())
                .andExpect(jsonPath("$.validaSegundos").value(300))
                .andReturn().getResponse().getContentAsString();

        String clave = respuesta.replaceAll(".*\"clave\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        // La clave lleva la empresa dentro: es lo que impide confirmar la de otra.
        assertThat(clave).startsWith("empresas/" + EMPRESA_ADMINISTRADA + "/logo_principal/");

        // Lo que hace el navegador.
        almacen.simularSubida(clave, PNG, UN_PNG_RAZONABLE);

        mockMvc.perform(put(IDENTIDAD + "/logo_principal")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clave\":\"%s\"}".formatted(clave)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.logo == 'logo_principal')].url").isNotEmpty());
    }

    // ── La desconfianza, que es el motivo de que haya dos pasos ─────────────

    @Test
    @DisplayName("Confirmar sin haber subido no guarda nada")
    void confirmarSinSubir() throws Exception {
        /*
         * El caso más probable en producción: el usuario cierra la pestaña, se
         * corta la conexión, la subida devuelve 403 por firma caducada. Si el
         * servidor se fiara de la confirmación, la fila apuntaría a un objeto
         * que no existe y la pantalla mostraría un logo roto.
         */
        comoDemo();
        var autorizacion = identidad.autorizarSubida("simbolo", PNG, 40_000L);
        ContextoDePrueba.limpiar();

        mockMvc.perform(put(IDENTIDAD + "/simbolo")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clave\":\"%s\"}".formatted(autorizacion.clave())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("subida_no_completada"));
    }

    @Test
    @DisplayName("Si lo subido no es lo prometido, se rechaza al confirmar")
    void loSubidoNoEsLoPrometido() {
        /*
         * Se declara un PNG de 100 KB y se sube un archivo de 900 KB. La firma
         * ata el tipo —S3 lo rechazaría por su cuenta— pero NO el tamaño: una URL
         * prefirmada de PUT no admite un rango de longitud, eso solo lo hace la
         * política de POST, que el SDK de Java no genera.
         *
         * De ahí la segunda comprobación, contra lo que el almacén dice tener.
         */
        comoDemo();
        var autorizacion = identidad.autorizarSubida("logo_ticket", PNG, 100_000L);

        almacen.simularSubida(autorizacion.clave(), PNG, 900_000L);

        assertThatThrownBy(() -> identidad.confirmarSubida("logo_ticket", autorizacion.clave()))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("no puede pasar de");
    }

    @Test
    @DisplayName("No se puede confirmar la clave de otra empresa")
    void claveDeOtraEmpresa() {
        // La clave se manda desde el cliente. Que sea difícil de adivinar no es
        // un control de acceso: se comprueba que empiece por el prefijo de la
        // empresa activa.
        comoDemo();
        String ajena = "empresas/%s/logo_principal/%s.png"
                .formatted(EMPRESA_COMO_VENDEDOR, UUID.randomUUID());
        almacen.simularSubida(ajena, PNG, UN_PNG_RAZONABLE);

        assertThatThrownBy(() -> identidad.confirmarSubida("logo_principal", ajena))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("no corresponde a esta empresa");
    }

    // ── Formatos ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("El SVG no se acepta")
    void nadaDeSvg() throws Exception {
        /*
         * Decisión del propietario, 2026-08-13. Un SVG es XML que puede llevar
         * JavaScript, y este es el único sitio donde un usuario sube un archivo
         * que después se muestra a otros —incluido dentro de un PDF y en la
         * barra superior—. Servirlo desde otro dominio reduce el daño y no
         * cierra el caso.
         */
        mockMvc.perform(post(IDENTIDAD + "/logo_principal/subida")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoContenido\":\"image/svg+xml\",\"bytes\":9000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("formato_no_admitido"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("código dentro")));
    }

    @Test
    @DisplayName("Un archivo demasiado grande se rechaza antes de firmar")
    void demasiadoGrande() throws Exception {
        // No se firma una subida que vamos a rechazar: el usuario no debe
        // esperar a que suba un megabyte para enterarse.
        mockMvc.perform(post(IDENTIDAD + "/logo_ticket/subida")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoContenido\":\"image/png\",\"bytes\":900000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("archivo_demasiado_grande"))
                // El mensaje dice el límite y lo que pesa el suyo: sin eso, hay
                // que adivinar cuánto recortar.
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("256 KB")));
    }

    // ── Reemplazar y quitar no son lo mismo ─────────────────────────────────

    @Test
    @DisplayName("Reemplazar conserva el archivo anterior")
    void reemplazarNoBorra() {
        /*
         * El logo viejo sigue en el almacén porque los comprobantes ya emitidos
         * lo referencian por su clave. Borrarlo dejaría documentos con un hueco
         * donde iba el logo — reescribir la historia en silencio, que es lo que
         * el versionado del bucket pretende evitar (DTE §5.8).
         */
        comoDemo();
        String primera = subirYConfirmar("simbolo", PNG, 30_000L);
        String segunda = subirYConfirmar("simbolo", PNG, 35_000L);

        assertThat(primera).isNotEqualTo(segunda);
        assertThat(almacen.describir(primera))
                .as("el anterior sigue ahí")
                .isPresent();
        assertThat(almacen.describir(segunda)).isPresent();
    }

    @Test
    @DisplayName("Quitar sí borra el archivo")
    void quitarBorra() throws Exception {
        // «Quitar» significa «no quiero logo», no «cambié de logo». Dejar el
        // objeto huérfano sería acumular archivos que nadie referencia.
        comoDemo();
        String clave = subirYConfirmar("logo_principal", PNG, UN_PNG_RAZONABLE);
        ContextoDePrueba.limpiar();

        mockMvc.perform(delete(IDENTIDAD + "/logo_principal")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                // El filtro devuelve [null], no una lista vacía: el hueco sigue
                // ahí, es su URL la que desaparece.
                .andExpect(jsonPath("$[?(@.logo == 'logo_principal')].url")
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.nullValue())));

        assertThat(almacen.describir(clave)).isEmpty();
    }

    @Test
    @DisplayName("Quitar dos veces no es un error")
    void quitarEsIdempotente() throws Exception {
        mockMvc.perform(delete(IDENTIDAD + "/simbolo")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk());

        mockMvc.perform(delete(IDENTIDAD + "/simbolo")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk());
    }

    // ── Aislamiento ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cada empresa ve sus propios logos")
    void elAislamientoLoPoneLaBase() {
        comoDemo();
        subirYConfirmar("logo_principal", PNG, UN_PNG_RAZONABLE);

        ContextoDePrueba.comoUsuarioDe(
                UUID.fromString(USUARIO_DEMO), CUENTA, UUID.fromString(EMPRESA_COMO_VENDEDOR));

        assertThat(identidad.consultar())
                .as("la otra empresa no ve el logo de la primera")
                .allMatch(estado -> estado.url() == null);
    }
}
