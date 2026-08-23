package com.ondexia.consultas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.error.ErrorDeDominio;
import com.ondexia.domain.consultas.ConsultaNoDisponible;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

/**
 * La puerta de la Lambda.
 *
 * <h2>Qué NO hace, y es lo importante</h2>
 *
 * <p>No comprueba quién llama. Lo hace el autorizador JWT de Cognito de la
 * pasarela, que ya existe para el resto de la API ({@code api.tf}): esta función
 * cuelga de una ruta de la <strong>misma</strong> HTTP API, así que llega
 * invocada solo si el token era válido. Repetir la validación aquí sería una
 * segunda implementación de la autenticación, y dos implementaciones acaban
 * teniendo dos comportamientos.
 *
 * <p>Tampoco decide si la empresa puede registrarse. Devuelve lo que SUNAT dice,
 * firmado; quien decide es la API al validar la atestación.
 *
 * <h2>Por qué el servicio es estático</h2>
 *
 * <p>Para construirse una vez por contenedor y no por invocación. Crear el
 * cliente de SSM y leer los parámetros cuesta cientos de milisegundos, y eso es
 * espera que mira una persona con el cursor en un formulario.
 *
 * <p>Y si la construcción falla —falta un parámetro, el rol no tiene permiso— no
 * se deja escapar la excepción del inicializador estático: eso llega al cliente
 * como un {@code ExceptionInInitializerError} que no se parece en nada al
 * problema real. Se guarda y se responde 503 diciendo qué falta.
 */
public class ManejadorDeConsultas implements RequestStreamHandler {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final ServicioDeConsultas SERVICIO;
    private static final String MOTIVO_DE_NO_ARRANCAR;

    static {
        ServicioDeConsultas servicio = null;
        String motivo = null;
        try {
            servicio = ServicioDeConsultas.desdeElEntorno(System::getenv);
        } catch (RuntimeException noSePudo) {
            motivo = noSePudo.getMessage();
            System.err.println("[consultas] no se pudo configurar: " + motivo);
        }
        SERVICIO = servicio;
        MOTIVO_DE_NO_ARRANCAR = motivo;
    }

    @Override
    public void handleRequest(InputStream entrada, OutputStream salida, Context contexto)
            throws IOException {

        JsonNode evento = JSON.readTree(entrada);
        JSON.writeValue(salida, SERVICIO == null ? sinConfigurar() : atender(evento));
    }

    private static ObjectNode sinConfigurar() {
        System.err.println("[consultas] invocación sin configuración: " + MOTIVO_DE_NO_ARRANCAR);
        // El motivo no viaja al cliente: nombra parametros y roles nuestros.
        return sobre(503, cuerpoDeError("consulta_sin_configurar",
                "La verificación de RUC no está disponible.", false));
    }

    private static ObjectNode atender(JsonNode evento) {
        String crudo = rucDelEvento(evento);
        if (crudo == null) {
            return sobre(400, cuerpoDeError("ruc_requerido", "Falta el RUC a consultar.", false));
        }

        Ruc ruc;
        try {
            ruc = new Ruc(crudo);
        } catch (ErrorDeDominio noValido) {
            // El digito verificador no cuadra. Se responde sin salir a la red: es
            // el error mas frecuente y no hay razon para gastar una consulta de un
            // plan de pago en una errata de tecleo.
            return sobre(400, cuerpoDeError(noValido.getCodigo(), noValido.getMessage(), false));
        }

        try {
            Optional<ServicioDeConsultas.Resultado> resultado = SERVICIO.consultar(ruc);
            if (resultado.isEmpty()) {
                return sobre(404, cuerpoDeError("ruc_no_encontrado",
                        "SUNAT no tiene registrado el RUC " + ruc.valor() + ".", false));
            }
            return sobre(200, cuerpoDeExito(resultado.get()));

        } catch (ConsultaNoDisponible noSePudo) {
            // 503 y no 500: no es un fallo de esta función, y el cliente puede
            // querer reintentar. Se le dice si merece la pena en vez de dejarlo
            // adivinar por el código.
            return sobre(503, cuerpoDeError(noSePudo.getCodigo(), noSePudo.getMessage(),
                    noSePudo.esReintentable()));
        }
    }

    /**
     * El RUC puede llegar por la ruta o por el parámetro de consulta.
     *
     * <p>Se aceptan los dos porque la pasarela puede enrutar de las dos formas y
     * averiguar cuál a base de desplegar es lento. Por el cuerpo no: un GET con
     * cuerpo lo descartan varios intermediarios sin avisar.
     */
    private static String rucDelEvento(JsonNode evento) {
        for (String donde : new String[] {"pathParameters", "queryStringParameters"}) {
            JsonNode valor = evento.path(donde).path("ruc");
            if (valor.isTextual() && !valor.asText().isBlank()) {
                return valor.asText().trim();
            }
        }
        return null;
    }

    private static ObjectNode cuerpoDeExito(ServicioDeConsultas.Resultado resultado) {
        ObjectNode cuerpo = JSON.createObjectNode();

        // `datos` viaja para que el formulario se rellene sin decodificar nada, y
        // NO se usa para decidir: cualquiera puede cambiarlo antes de reenviarlo.
        // Lo único que la API cree es la atestación.
        cuerpo.set("datos", Serializacion.aJson(JSON, resultado.datos()));
        cuerpo.put("atestacion", resultado.atestacion());
        return cuerpo;
    }

    private static ObjectNode cuerpoDeError(String codigo, String mensaje, boolean reintentable) {
        ObjectNode cuerpo = JSON.createObjectNode();
        cuerpo.put("codigo", codigo);
        cuerpo.put("mensaje", mensaje);
        cuerpo.put("reintentable", reintentable);
        return cuerpo;
    }

    /**
     * El sobre que espera la pasarela con formato de payload 2.0.
     *
     * <p>El cuerpo va como <strong>cadena</strong>, no como objeto. Es el error
     * clásico de esta integración: devolviendo un objeto, la pasarela responde
     * 500 sin decir por qué y el rastro no menciona el formato.
     */
    private static ObjectNode sobre(int estado, ObjectNode cuerpo) {
        ObjectNode sobre = JSON.createObjectNode();
        sobre.put("statusCode", estado);
        sobre.putObject("headers").put("Content-Type", "application/json; charset=utf-8");
        try {
            sobre.put("body", JSON.writeValueAsString(cuerpo));
        } catch (JsonProcessingException imposible) {
            throw new IllegalStateException("No se pudo serializar la respuesta", imposible);
        }
        return sobre;
    }
}
