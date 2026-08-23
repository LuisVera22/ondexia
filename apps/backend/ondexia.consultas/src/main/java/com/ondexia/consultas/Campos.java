package com.ondexia.consultas;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Leer un campo de texto de una respuesta JSON.
 *
 * <p>Existe porque los tres proveedores representan «no hay dato» de tres
 * formas: el campo ausente, {@code null}, y la cadena vacía. Tratadas distinto,
 * una empresa acabaría con el distrito guardado como {@code ""} y otra con nulo,
 * y luego la vista tendría que defenderse de las dos.
 */
final class Campos {

    private Campos() {
    }

    /** @return el texto sin espacios alrededor, o {@code null} si no hay nada */
    static String texto(JsonNode json, String campo) {
        JsonNode valor = json.path(campo);
        if (valor.isMissingNode() || valor.isNull()) {
            return null;
        }
        String texto = valor.asText().trim();
        return texto.isEmpty() ? null : texto;
    }
}
