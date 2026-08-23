package com.ondexia.infrastructure.salida.consultas;

import com.fasterxml.jackson.databind.JsonNode;
import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.Ubigeo;
import com.ondexia.domain.consultas.ConsultaDeRuc;
import com.ondexia.domain.consultas.DatosDeRuc;
import java.time.Instant;
import java.util.Optional;
import org.springframework.web.client.RestClient;

/**
 * Decolecta, el proveedor principal.
 *
 * <p>Es el principal porque es el más completo de la cascada: trae ubigeo,
 * distrito, provincia y los locales anexos, y su endpoint {@code /full} añade la
 * forma societaria. Los otros dos rellenan huecos suyos, no lo sustituyen.
 *
 * <p>{@code GET /v1/sunat/ruc/full?numero=…} con {@code Authorization: Bearer}.
 * Se usa {@code /full} y no el básico porque {@code tipo} —la forma societaria—
 * solo está ahí, y es un campo que el formulario deja de preguntar precisamente
 * porque lo trae la consulta.
 */
class ConsultaDeRucDecolecta implements ConsultaDeRuc {

    private final RestClient cliente;

    ConsultaDeRucDecolecta(RestClient cliente) {
        this.cliente = cliente;
    }

    @Override
    public Optional<DatosDeRuc> consultar(Ruc ruc) {
        Optional<JsonNode> respuesta = LlamadaDeConsulta.ejecutar(
                "decolecta",
                () -> cliente.get()
                        .uri(uri -> uri.path("/sunat/ruc/full")
                                .queryParam("numero", ruc.valor())
                                .build())
                        .retrieve()
                        .body(JsonNode.class));

        return respuesta.map(cuerpo -> traducir(ruc, cuerpo));
    }

    private static DatosDeRuc traducir(Ruc ruc, JsonNode json) {
        return new DatosDeRuc(
                ruc,
                texto(json, "razon_social"),
                VocabularioDeSunat.estado(texto(json, "estado")),
                VocabularioDeSunat.condicion(texto(json, "condicion")),
                texto(json, "direccion"),
                ubigeo(json),
                texto(json, "distrito"),
                texto(json, "provincia"),
                texto(json, "departamento"),
                json.path("es_agente_retencion").asBoolean(false),
                json.path("es_buen_contribuyente").asBoolean(false),
                texto(json, "tipo"),
                Instant.now());
    }

    /**
     * El ubigeo se descarta si no tiene la forma que exige el comprobante.
     *
     * <p>Un ubigeo mal formado no es mejor que ninguno: llegaría hasta el XML y
     * SUNAT lo rechazaría allí, mucho después y con un mensaje suyo. Devolver
     * {@code null} hace que {@code faltaUbigeo()} lo diga a tiempo.
     */
    private static Ubigeo ubigeo(JsonNode json) {
        String crudo = texto(json, "ubigeo");
        if (crudo == null) {
            return null;
        }
        try {
            return new Ubigeo(crudo);
        } catch (RuntimeException noValido) {
            return null;
        }
    }

    private static String texto(JsonNode json, String campo) {
        JsonNode valor = json.path(campo);
        if (valor.isMissingNode() || valor.isNull()) {
            return null;
        }
        String texto = valor.asText().trim();
        return texto.isEmpty() ? null : texto;
    }
}
