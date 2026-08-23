package com.ondexia.infrastructure.salida.consultas;

import com.fasterxml.jackson.databind.JsonNode;
import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.consultas.ConsultaDeRuc;
import com.ondexia.domain.consultas.DatosDeRuc;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * apiperu.dev, el relevo.
 *
 * <p>{@code POST /ruc} con el número en el cuerpo —no en la ruta, a diferencia
 * de Decolecta— y {@code Authorization: Bearer}.
 *
 * <h2>Qué no devuelve, y por qué importa</h2>
 *
 * <p><strong>No trae ubigeo</strong>, ni distrito ni provincia: solo la
 * dirección y el departamento. Y el ubigeo va en el comprobante electrónico.
 *
 * <p>Eso no invalida el relevo, pero sí significa que cuando responde este
 * proveedor la respuesta llega incompleta a propósito, con
 * {@code faltaUbigeo()} en cierto. Completarlo es asunto de quien orquesta, no
 * de aquí: este adaptador dice la verdad sobre lo que recibió.
 */
class ConsultaDeRucApiPeru implements ConsultaDeRuc {

    private final RestClient cliente;

    ConsultaDeRucApiPeru(RestClient cliente) {
        this.cliente = cliente;
    }

    @Override
    public Optional<DatosDeRuc> consultar(Ruc ruc) {
        Optional<JsonNode> respuesta = LlamadaDeConsulta.ejecutar(
                "apiperu",
                () -> cliente.post()
                        .uri("/ruc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("ruc", ruc.valor()))
                        .retrieve()
                        .body(JsonNode.class));

        return respuesta.map(cuerpo -> traducir(ruc, cuerpo.path("data")));
    }

    private static DatosDeRuc traducir(Ruc ruc, JsonNode datos) {
        return new DatosDeRuc(
                ruc,
                texto(datos, "nombre_o_razon_social"),
                VocabularioDeSunat.estado(texto(datos, "estado")),
                VocabularioDeSunat.condicion(texto(datos, "condicion")),
                texto(datos, "direccion"),
                // Sin ubigeo: ver la cabecera de la clase. No es un olvido.
                null,
                null,
                null,
                texto(datos, "departamento"),
                datos.path("es_agente_de_retencion").asBoolean(false),
                datos.path("es_buen_contribuyente").asBoolean(false),
                // Tampoco forma societaria.
                null,
                Instant.now());
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
