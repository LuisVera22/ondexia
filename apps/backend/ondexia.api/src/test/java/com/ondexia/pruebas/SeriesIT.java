package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.application.comprobante.AsignadorDeCorrelativo;
import com.ondexia.application.comprobante.Series;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.infrastructure.seguridad.ContextoDePrueba;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Entrega 3: series y correlativos.
 *
 * <p>La prueba que justifica esta clase entera es {@link #sinDuplicadosNiHuecos()}.
 * El resto es el CRUD de siempre; ese método es el único capaz de detectar el
 * fallo que de verdad importa, y no se reproduce a mano: un correlativo
 * duplicado solo aparece cuando dos cajas emiten en el mismo instante, que en
 * producción es la hora punta del mostrador y aquí hay que provocarlo.
 */
class SeriesIT extends PruebaIntegracion {

    private static final String SERIES = "/api/v1/configuracion/series";
    private static final UUID CUENTA = UUID.fromString("00000000-0000-4000-8000-000000000001");

    /** Casa matriz de EMPRESA_ADMINISTRADA, según los datos de ejemplo (V900). */
    private static final UUID SUCURSAL_MATRIZ =
            UUID.fromString("00000000-0000-4000-8000-000000000020");

    @Autowired
    private Series series;

    @Autowired
    private AsignadorDeCorrelativo asignador;

    @Autowired
    private PlatformTransactionManager gestorTransacciones;

    @AfterEach
    void limpiar() {
        ContextoDePrueba.limpiar();
    }

    private void comoUsuarioEn(String empresa) {
        ContextoDePrueba.comoUsuarioDe(
                UUID.fromString(USUARIO_DEMO), CUENTA, UUID.fromString(empresa));
    }

    // ── El correlativo bajo concurrencia ────────────────────────────────────

    @Test
    @DisplayName("Doce hilos pidiendo correlativo: sin duplicados y sin huecos")
    void sinDuplicadosNiHuecos() throws Exception {
        comoUsuarioEn(EMPRESA_ADMINISTRADA);
        var serie = series.registrar(SUCURSAL_MATRIZ, "01", "F900", 0);

        final int hilos = 12;
        var plantilla = new TransactionTemplate(gestorTransacciones);
        var enPosicion = new CountDownLatch(hilos);
        var salida = new CountDownLatch(1);

        List<Future<Long>> pedidos;
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(hilos)) {
            List<Callable<Long>> tareas = java.util.stream.IntStream.range(0, hilos)
                    .<Callable<Long>>mapToObj(i -> () -> {
                        /*
                         * El contexto va atado al hilo, así que cada uno fija el
                         * suyo. Sin esto la política de RLS no vería la empresa y
                         * la serie «no existiría» — un fallo que se leería como
                         * error de datos y no de aislamiento.
                         */
                        ContextoDePrueba.comoUsuarioDe(
                                UUID.fromString(USUARIO_DEMO), CUENTA,
                                UUID.fromString(EMPRESA_ADMINISTRADA));
                        try {
                            // Todos esperan a que los doce estén listos. Sin esta
                            // barrera, el primero termina antes de que arranque el
                            // último y la prueba pasaría aunque no hubiera bloqueo
                            // ninguno.
                            enPosicion.countDown();
                            salida.await(10, TimeUnit.SECONDS);
                            return plantilla.execute(estado -> asignador.siguienteNumero(serie.id()));
                        } finally {
                            ContextoDePrueba.limpiar();
                        }
                    })
                    .toList();

            pedidos = tareas.stream().map(ejecutor::submit).toList();
            enPosicion.await(10, TimeUnit.SECONDS);
            salida.countDown();

            List<Long> numeros = new java.util.ArrayList<>();
            for (Future<Long> pedido : pedidos) {
                numeros.add(pedido.get(30, TimeUnit.SECONDS));
            }

            // Las dos condiciones, por separado, porque fallan por motivos
            // distintos: duplicados = no hay bloqueo; huecos = alguien perdió un
            // número por el camino.
            assertThat(numeros)
                    .as("ningún número se repite")
                    .doesNotHaveDuplicates();
            assertThat(numeros)
                    .as("del 1 al %d, sin saltarse ninguno", hilos)
                    .containsExactlyInAnyOrderElementsOf(
                            java.util.stream.LongStream.rangeClosed(1, hilos).boxed().toList());
        }

        comoUsuarioEn(EMPRESA_ADMINISTRADA);
        assertThat(series.listar())
                .filteredOn(s -> "F900".equals(s.serie()))
                .singleElement()
                .extracting(s -> s.ultimoNumero())
                .isEqualTo((long) hilos);
    }

    @Test
    @DisplayName("Pedir correlativo sin transacción abierta es un error inmediato")
    void sinTransaccionNoHayCorrelativo() {
        /*
         * La propagación es MANDATORY a propósito. Con REQUIRED, llamar sin
         * transacción crearía una, confirmaría el número y volvería; si la
         * creación del comprobante fallara después, ese número quedaría consumido
         * para siempre — un hueco que hay que justificar ante SUNAT.
         *
         * Esta prueba fija esa decisión: si alguien cambia la anotación a
         * REQUIRED «porque daba error», esto se pone rojo y explica por qué.
         */
        comoUsuarioEn(EMPRESA_ADMINISTRADA);
        var serie = series.registrar(SUCURSAL_MATRIZ, "03", "B900", 0);

        assertThatThrownBy(() -> asignador.siguienteNumero(serie.id()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("Si la transacción se deshace, el número vuelve a estar disponible")
    void elNumeroSeDevuelveAlDeshacer() {
        /*
         * Es la razón entera de no usar una SEQUENCE. Una secuencia entrega el
         * número fuera de la transacción y no lo devuelve nunca; esta columna sí.
         */
        comoUsuarioEn(EMPRESA_ADMINISTRADA);
        var serie = series.registrar(SUCURSAL_MATRIZ, "01", "F901", 0);

        var plantilla = new TransactionTemplate(gestorTransacciones);

        assertThatThrownBy(() -> plantilla.execute(estado -> {
            asignador.siguienteNumero(serie.id());
            throw new IllegalStateException("el comprobante falló después de pedir el número");
        })).isInstanceOf(IllegalStateException.class);

        long siguiente = plantilla.execute(estado -> asignador.siguienteNumero(serie.id()));

        assertThat(siguiente)
                .as("el número deshecho se reutiliza: es el 1, no el 2")
                .isEqualTo(1L);
    }

    // ── Formato de serie y catálogo 01 ──────────────────────────────────────

    @Test
    @DisplayName("La letra de la serie tiene que corresponder al tipo de documento")
    void laLetraDependeDelTipo() throws Exception {
        // B para una factura: SUNAT lo rechaza, y conviene enterarse ahora y no
        // con la serie entera ya emitida.
        mockMvc.perform(post(SERIES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sucursalId":"%s","tipoDocumento":"01","serie":"B001"}"""
                                .formatted(SUCURSAL_MATRIZ)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("serie_invalida"));

        // Y la de una nota de crédito acepta tanto F como B, porque hereda la
        // letra del documento que corrige.
        mockMvc.perform(post(SERIES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sucursalId":"%s","tipoDocumento":"07","serie":"BC77"}"""
                                .formatted(SUCURSAL_MATRIZ)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("La serie se guarda en mayúsculas y el siguiente número sale formateado")
    void altaYFormato() throws Exception {
        mockMvc.perform(post(SERIES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sucursalId":"%s","tipoDocumento":"01","serie":"f010"}"""
                                .formatted(SUCURSAL_MATRIZ)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serie").value("F010"))
                .andExpect(jsonPath("$.ultimoNumero").value(0))
                // Lo que de verdad mira quien abre la pantalla.
                .andExpect(jsonPath("$.siguienteNumero").value("F010-00000001"))
                .andExpect(jsonPath("$.tipoDocumentoNombre").value("Factura"));
    }

    @Test
    @DisplayName("El número inicial existe para migrar desde otro sistema")
    void numeroInicialParaMigracion() throws Exception {
        mockMvc.perform(post(SERIES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sucursalId":"%s","tipoDocumento":"01","serie":"F020",
                                 "numeroInicial":4300}"""
                                .formatted(SUCURSAL_MATRIZ)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ultimoNumero").value(4300))
                .andExpect(jsonPath("$.siguienteNumero").value("F020-00004301"));
    }

    @Test
    @DisplayName("Una serie repetida en la misma empresa y tipo es conflicto")
    void serieRepetida() throws Exception {
        mockMvc.perform(post(SERIES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sucursalId":"%s","tipoDocumento":"01","serie":"F030"}"""
                                .formatted(SUCURSAL_MATRIZ)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(SERIES)
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sucursalId":"%s","tipoDocumento":"01","serie":"f030"}"""
                                .formatted(SUCURSAL_MATRIZ)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("serie_duplicada"));
    }

    @Test
    @DisplayName("El catálogo 01 se sirve para el desplegable del alta")
    void catalogoDeTipos() throws Exception {
        mockMvc.perform(get(SERIES + "/tipos-documento")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[?(@.codigo == '01')].nombre").value("Factura"));
    }

    // ── Aislamiento y estado ────────────────────────────────────────────────

    @Test
    @DisplayName("Dos empresas pueden tener la misma serie")
    void laSerieEsUnicaPorEmpresa() {
        // La restricción es UNIQUE (empresa_id, tipo_documento, serie): dos
        // contribuyentes distintos numeran su F001 cada uno por su cuenta.
        comoUsuarioEn(EMPRESA_ADMINISTRADA);
        series.registrar(SUCURSAL_MATRIZ, "01", "F040", 0);

        comoUsuarioEn(EMPRESA_COMO_VENDEDOR);
        // La casa matriz de la otra empresa, según V900. Ojo: la ...021 es
        // Miraflores y pertenece a la PRIMERA empresa, no a esta.
        UUID sucursalDeB = UUID.fromString("00000000-0000-4000-8000-000000000022");
        var deB = series.registrar(sucursalDeB, "01", "F040", 0);

        assertThat(deB.serie()).isEqualTo("F040");
        assertThat(deB.empresaId()).isEqualTo(UUID.fromString(EMPRESA_COMO_VENDEDOR));

        // Y la política de RLS impide verse entre sí.
        assertThat(series.listar())
                .as("la empresa B solo ve la suya")
                .filteredOn(s -> "F040".equals(s.serie()))
                .singleElement()
                .extracting(s -> s.empresaId())
                .isEqualTo(UUID.fromString(EMPRESA_COMO_VENDEDOR));
    }

    @Test
    @DisplayName("Desactivar no reinicia el correlativo, y reactivar continúa donde iba")
    void desactivarConservaElNumero() throws Exception {
        comoUsuarioEn(EMPRESA_ADMINISTRADA);
        var serie = series.registrar(SUCURSAL_MATRIZ, "01", "F050", 0);

        var plantilla = new TransactionTemplate(gestorTransacciones);
        plantilla.execute(estado -> asignador.siguienteNumero(serie.id()));
        plantilla.execute(estado -> asignador.siguienteNumero(serie.id()));

        ContextoDePrueba.limpiar();

        mockMvc.perform(put(SERIES + "/" + serie.id() + "/estado")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false))
                .andExpect(jsonPath("$.ultimoNumero").value(2));

        // Desactivada no emite.
        comoUsuarioEn(EMPRESA_ADMINISTRADA);
        assertThatThrownBy(() ->
                plantilla.execute(estado -> asignador.siguienteNumero(serie.id())))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("desactivada");
        ContextoDePrueba.limpiar();

        mockMvc.perform(put(SERIES + "/" + serie.id() + "/estado")
                        .header("Authorization", autorizacionDemo())
                        .header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":true}"))
                .andExpect(status().isOk())
                // Lo que importa: sigue en 2, no vuelve a 0.
                .andExpect(jsonPath("$.ultimoNumero").value(2));

        comoUsuarioEn(EMPRESA_ADMINISTRADA);
        long siguiente = plantilla.execute(estado -> asignador.siguienteNumero(serie.id()));
        assertThat(siguiente).isEqualTo(3L);
    }

    @Test
    @DisplayName("No se puede colgar una serie de un establecimiento de otra empresa")
    void noSePuedeUsarUnEstablecimientoAjeno() {
        // Igual que en almacenes: `sucursal` queda FUERA de RLS, así que aquí el
        // filtro sí es responsabilidad del código.
        comoUsuarioEn(EMPRESA_COMO_VENDEDOR);

        assertThatThrownBy(() -> series.registrar(SUCURSAL_MATRIZ, "01", "F060", 0))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("no existe en esta empresa");
    }
}
