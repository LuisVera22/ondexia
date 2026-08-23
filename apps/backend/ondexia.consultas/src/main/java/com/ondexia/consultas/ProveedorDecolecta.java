package com.ondexia.consultas;

import com.fasterxml.jackson.databind.JsonNode;
import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.Ubigeo;
import com.ondexia.domain.consultas.ConsultaDeRuc;
import com.ondexia.domain.consultas.DatosDeRuc;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;

/**
 * Decolecta, el proveedor principal.
 *
 * <p>Es el principal porque es el más completo de la cascada: trae ubigeo,
 * distrito, provincia y los locales anexos, y {@code /full} añade la forma
 * societaria. Los otros dos rellenan huecos suyos, no lo sustituyen.
 *
 * <p>Se usa {@code /full} y no el endpoint básico porque {@code tipo} —la forma
 * societaria— solo está ahí, y es justo el campo que el formulario deja de
 * preguntar porque lo trae la consulta.
 */
class ProveedorDecolecta implements ConsultaDeRuc {

    private final ClienteDelPadron cliente;
    private final String base;

    ProveedorDecolecta(ClienteDelPadron cliente, String base) {
        this.cliente = cliente;
        this.base = base;
    }

    @Override
    public Optional<DatosDeRuc> consultar(Ruc ruc) {
        return cliente.get(URI.create(base + "/sunat/ruc/full?numero=" + ruc.valor()))
                .map(json -> traducir(ruc, json));
    }

    private static DatosDeRuc traducir(Ruc ruc, JsonNode json) {
        return new DatosDeRuc(
                ruc,
                Campos.texto(json, "razon_social"),
                VocabularioDeSunat.estado(Campos.texto(json, "estado")),
                VocabularioDeSunat.condicion(Campos.texto(json, "condicion")),
                Campos.texto(json, "direccion"),
                ubigeo(json),
                Campos.texto(json, "distrito"),
                Campos.texto(json, "provincia"),
                Campos.texto(json, "departamento"),
                json.path("es_agente_retencion").asBoolean(false),
                json.path("es_buen_contribuyente").asBoolean(false),
                Campos.texto(json, "tipo"),
                Instant.now());
    }

    /**
     * Un ubigeo mal formado se descarta.
     *
     * <p>No es mejor que ninguno: llegaría hasta el XML y SUNAT lo rechazaría
     * allí, mucho después y con un mensaje suyo. Devolviendo nulo,
     * {@code faltaUbigeo()} lo dice a tiempo.
     */
    private static Ubigeo ubigeo(JsonNode json) {
        String crudo = Campos.texto(json, "ubigeo");
        if (crudo == null) {
            return null;
        }
        try {
            return new Ubigeo(crudo);
        } catch (RuntimeException noValido) {
            return null;
        }
    }
}
