package com.ondexia.facturacion.sunat;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClienteSunatHttpTest {
    private HttpServer servidor;
    private java.util.concurrent.ExecutorService ejecutor;
    private final CountDownLatch terminar = new CountDownLatch(1);
    private static final byte[] TICKET = "<Envelope><Body><ticket>ticket-local</ticket></Body></Envelope>"
            .getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ejecutor = Executors.newVirtualThreadPerTaskExecutor();
        servidor.setExecutor(ejecutor);
        servidor.start();
    }

    @AfterEach
    void cerrar() {
        terminar.countDown();
        if (servidor != null) servidor.stop(0);
        if (ejecutor != null) ejecutor.shutdownNow();
    }

    @Test
    void cancelaExcesoSinEsperarElFinDelCuerpoPorBloques() {
        servidor.createContext("/respuesta", intercambio -> {
            intercambio.getRequestBody().readAllBytes();
            intercambio.sendResponseHeaders(200, 0);
            try (var salida = intercambio.getResponseBody()) {
                byte[] bloque = new byte[64 * 1024];
                Arrays.fill(bloque, (byte) ' ');
                for (int i = 0; i < 256; i++) salida.write(bloque);
                salida.write(' ');
                salida.flush();
                esperarFin();
            } catch (IOException desconectado) {
                // La cancelación del cliente puede cerrar este extremo.
            }
        });
        RespuestaSunat respuesta = assertTimeoutPreemptively(Duration.ofSeconds(4),
                () -> resumen(new ClienteSunat(Duration.ofSeconds(10))));
        assertEquals(RespuestaSunat.Tipo.SIN_RESPUESTA, respuesta.tipo());
        assertEquals("RESPUESTA_DEMASIADO_GRANDE", respuesta.codigo());
        assertNull(respuesta.respuestaOriginal());
        assertNull(respuesta.cdr());
        assertFalse(respuesta.aceptado());
        assertFalse(respuesta.rechazado());
        assertEquals("SUNAT respondió con un cuerpo que supera el límite de recepción.", respuesta.descripcion());
    }

    @Test
    void admiteExactamenteDieciseisMibYConservaElOriginal() {
        byte[] cuerpo = new byte[16 * 1024 * 1024];
        Arrays.fill(cuerpo, (byte) ' ');
        System.arraycopy(TICKET, 0, cuerpo, 0, TICKET.length);
        servidor.createContext("/respuesta", intercambio -> responder(intercambio, cuerpo));
        RespuestaSunat respuesta = resumen(new ClienteSunat(Duration.ofSeconds(10)));
        assertEquals(RespuestaSunat.Tipo.TICKET, respuesta.tipo());
        assertEquals("ticket-local", respuesta.codigo());
        assertArrayEquals(cuerpo, respuesta.respuestaOriginal());
    }

    @Test
    void rechazaExcesoTambienConLongitudDeclarada() {
        byte[] cuerpo = new byte[16 * 1024 * 1024 + 1];
        servidor.createContext("/respuesta", intercambio -> {
            try {
                responder(intercambio, cuerpo);
            } catch (IOException desconectado) {
                // El cliente ya no necesita el resto de la respuesta excesiva.
            }
        });
        RespuestaSunat respuesta = resumen(new ClienteSunat(Duration.ofSeconds(5)));
        assertEquals("RESPUESTA_DEMASIADO_GRANDE", respuesta.codigo());
        assertNull(respuesta.respuestaOriginal());
    }

    @Test
    void cuerpoTruncadoProduceDiagnosticoFijoSinDetalleDeExcepcion() {
        servidor.createContext("/respuesta", intercambio -> {
            intercambio.getRequestBody().readAllBytes();
            intercambio.sendResponseHeaders(200, 1000);
            intercambio.getResponseBody().write('<');
            intercambio.close();
        });
        RespuestaSunat respuesta = resumen(new ClienteSunat(Duration.ofSeconds(5)));
        assertEquals("SIN_CONEXION", respuesta.codigo());
        assertEquals("No se pudo completar la comunicación con SUNAT.", respuesta.descripcion());
        assertNull(respuesta.respuestaOriginal());
    }

    @Test
    void tiempoMaximoIncluyeElCuerpoDespuesDeLasCabeceras() {
        servidor.createContext("/respuesta", intercambio -> {
            intercambio.getRequestBody().readAllBytes();
            intercambio.sendResponseHeaders(200, 0);
            try (var salida = intercambio.getResponseBody()) {
                salida.write('<');
                salida.flush();
                esperarFin();
            }
        });
        RespuestaSunat respuesta = assertTimeoutPreemptively(Duration.ofSeconds(3),
                () -> resumen(new ClienteSunat(Duration.ofMillis(300))));
        assertEquals("SIN_CONEXION", respuesta.codigo());
        assertEquals("No se pudo completar la comunicación con SUNAT.", respuesta.descripcion());
        assertNull(respuesta.respuestaOriginal());
    }

    @Test
    void noReenviaCredencialesTrasRedireccion() {
        AtomicInteger visitas = new AtomicInteger();
        servidor.createContext("/destino", intercambio -> {
            visitas.incrementAndGet();
            responder(intercambio, TICKET);
        });
        servidor.createContext("/respuesta", intercambio -> {
            intercambio.getRequestBody().readAllBytes();
            intercambio.getResponseHeaders().set("Location", url("/destino"));
            intercambio.sendResponseHeaders(307, -1);
            intercambio.close();
        });
        RespuestaSunat respuesta = resumen(new ClienteSunat(Duration.ofSeconds(5)));
        assertEquals("HTTP_307", respuesta.codigo());
        assertEquals(0, visitas.get());
        assertNull(respuesta.respuestaOriginal());
    }

    @Test
    void enviarYConsultarConservanElEstadoDelTicket() {
        byte[] cuerpo = "<Envelope><Body><status><statusCode>98</statusCode></status></Body></Envelope>"
                .getBytes(StandardCharsets.UTF_8);
        servidor.createContext("/respuesta", intercambio -> responder(intercambio, cuerpo));
        var cliente = new ClienteSunat(Duration.ofSeconds(5));
        RespuestaSunat envio = cliente.enviar(url("/respuesta"), "20100000009", "usuario", "clave-sintetica", "x.zip", new byte[] {1});
        RespuestaSunat consulta = cliente.consultarTicket(url("/respuesta"), "20100000009", "usuario", "clave-sintetica", "ticket-local");
        for (var respuesta : new RespuestaSunat[] {envio, consulta}) {
            assertEquals(RespuestaSunat.Tipo.EN_PROCESO, respuesta.tipo());
            assertEquals("98", respuesta.codigo());
            assertArrayEquals(cuerpo, respuesta.respuestaOriginal());
        }
    }

    @Test
    void conservaLaInterrupcionDelHilo() {
        Thread.currentThread().interrupt();
        try {
            RespuestaSunat respuesta = resumen(new ClienteSunat(Duration.ofSeconds(5)));
            assertEquals("INTERRUMPIDO", respuesta.codigo());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private RespuestaSunat resumen(ClienteSunat cliente) {
        return cliente.enviarResumen(url("/respuesta"), "20100000009", "usuario", "clave-sintetica", "x.zip", new byte[] {1});
    }

    private String url(String ruta) {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + ruta;
    }

    private static void responder(HttpExchange intercambio, byte[] cuerpo) throws IOException {
        intercambio.getRequestBody().readAllBytes();
        intercambio.sendResponseHeaders(200, cuerpo.length);
        try (var salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }

    private void esperarFin() {
        try {
            terminar.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
        }
    }
}
