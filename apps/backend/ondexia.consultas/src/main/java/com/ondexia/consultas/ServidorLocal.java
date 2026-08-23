package com.ondexia.consultas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.error.ErrorDeDominio;
import com.ondexia.domain.consultas.ConsultaNoDisponible;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.util.Optional;

/**
 * El mismo servicio, servido por HTTP, para desarrollar con el SPA en local.
 *
 * <h2>Por qué existe</h2>
 *
 * <p>Porque {@code GET /consultas/ruc/{ruc}} solo existe en API Gateway. En un
 * portátil, la API de Spring no tiene esa ruta —ni debe tenerla: no puede
 * consultar nada desplegada— y {@link ManejadorDeConsultas} es un manejador de
 * Lambda, no un servidor. Sin esto, el formulario de alta de empresa no se puede
 * probar en local: hay que desplegar para ver si el autocompletado funciona.
 *
 * <p>Sin dependencias: {@code com.sun.net.httpserver} viene en la JDK. Traer un
 * servidor de verdad para dos rutas de desarrollo abultaría el artefacto de la
 * Lambda, que es lo contrario de por qué este módulo no lleva Spring.
 *
 * <h2>NUNCA se despliega, y hay tres cosas que lo impiden</h2>
 *
 * <ul>
 *   <li>Lambda invoca {@link ManejadorDeConsultas}, no esta clase: Terraform fija
 *       el handler y este {@code main} no es alcanzable desde ahí.
 *   <li>Escucha solo en {@code 127.0.0.1}. Aunque alguien lo arrancara en un
 *       servidor, nadie de fuera llegaría.
 *   <li><strong>No valida ningún token.</strong> Desplegado, quien autentica es
 *       el autorizador de Cognito de la pasarela; aquí no hay pasarela, así que
 *       esto responde a cualquiera que alcance el puerto. Es aceptable en
 *       {@code localhost} y en ningún otro sitio.
 * </ul>
 */
public final class ServidorLocal {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RUTA = "/consultas/ruc/";

    /**
     * El origen del servidor de desarrollo de Angular.
     *
     * <p>Hace falta CORS porque el SPA vive en el 4200 y esto en el 8081 — dos
     * orígenes. Desplegado no hay CORS que poner aquí: la ruta cuelga de la misma
     * pasarela que la API, así que para el navegador es el mismo origen que el
     * resto de las llamadas.
     */
    private static final String ORIGEN_SPA = "http://localhost:4200";

    private ServidorLocal() {
    }

    public static void main(String[] argumentos) throws IOException {
        int puerto = puerto();
        ServicioDeConsultas servicio = ServicioDeConsultas.desdeElEntorno(System::getenv);

        HttpServer servidor = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), puerto), 0);
        servidor.createContext(RUTA, intercambio -> atender(servicio, intercambio));
        servidor.start();

        /*
         * Se imprime la IP y no «localhost», y no es un detalle de estilo.
         *
         * Esto se ata al loopback IPv4. En una maquina donde `localhost` resuelve
         * primero a ::1 —lo hace Windows, y ahi mismo `ng serve` acaba escuchando
         * en [::1]— una peticion a http://localhost:8081 puede dar
         * ERR_CONNECTION_REFUSED con el servidor perfectamente arrancado. Ese
         * error es indistinguible de «no lo he lanzado», y se pierde un buen rato
         * buscandolo en el sitio equivocado.
         *
         * Por eso config.json apunta a 127.0.0.1 y por eso este mensaje dice la
         * IP: lo que se copia de aqui funciona.
         */
        System.out.println("[consultas] escuchando en http://127.0.0.1:" + puerto + RUTA + "{ruc}");
        System.out.println("[consultas] solo para desarrollo: no valida tokens");
    }

    private static int puerto() {
        String valor = System.getenv("CONSULTAS_PUERTO");
        return valor == null || valor.isBlank() ? 8081 : Integer.parseInt(valor.trim());
    }

    private static void atender(ServicioDeConsultas servicio, HttpExchange intercambio)
            throws IOException {

        intercambio.getResponseHeaders().add("Access-Control-Allow-Origin", ORIGEN_SPA);
        intercambio.getResponseHeaders().add("Access-Control-Allow-Headers",
                "Authorization, Content-Type, X-Empresa-Id");
        intercambio.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");

        // El SPA manda Authorization, asi que el navegador hace comprobacion
        // previa. Sin atender OPTIONS, la peticion real nunca sale y en la consola
        // solo se ve un error de red sin cuerpo.
        if ("OPTIONS".equals(intercambio.getRequestMethod())) {
            intercambio.sendResponseHeaders(204, -1);
            intercambio.close();
            return;
        }

        if (!"GET".equals(intercambio.getRequestMethod())) {
            responder(intercambio, 405, error("metodo_no_permitido", "Solo GET.", false));
            return;
        }

        String crudo = intercambio.getRequestURI().getPath().substring(RUTA.length());

        try {
            Ruc ruc = new Ruc(crudo);
            Optional<ServicioDeConsultas.Resultado> resultado = servicio.consultar(ruc);

            if (resultado.isEmpty()) {
                responder(intercambio, 404, error("ruc_no_encontrado",
                        "SUNAT no tiene registrado el RUC " + ruc.valor() + ".", false));
                return;
            }

            ObjectNode cuerpo = JSON.createObjectNode();
            cuerpo.set("datos", Serializacion.aJson(JSON, resultado.get().datos()));
            cuerpo.put("atestacion", resultado.get().atestacion());
            responder(intercambio, 200, cuerpo);

        } catch (ConsultaNoDisponible noSePudo) {
            responder(intercambio, 503,
                    error(noSePudo.getCodigo(), noSePudo.getMessage(), noSePudo.esReintentable()));

        } catch (ErrorDeDominio noValido) {
            // Digito verificador que no cuadra, sobre todo. Se responde sin salir
            // a la red: es el error mas frecuente y no hay razon para gastar una
            // consulta de un plan de pago en una errata de tecleo.
            responder(intercambio, 400, error(noValido.getCodigo(), noValido.getMessage(), false));
        }
    }

    private static ObjectNode error(String codigo, String mensaje, boolean reintentable) {
        ObjectNode cuerpo = JSON.createObjectNode();
        cuerpo.put("codigo", codigo);
        cuerpo.put("mensaje", mensaje);
        cuerpo.put("reintentable", reintentable);
        return cuerpo;
    }

    private static void responder(HttpExchange intercambio, int estado, ObjectNode cuerpo)
            throws IOException {

        byte[] bytes = JSON.writeValueAsBytes(cuerpo);
        intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        intercambio.sendResponseHeaders(estado, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
        System.out.println("[consultas] " + intercambio.getRequestURI().getPath()
                + " -> " + estado);
    }
}
