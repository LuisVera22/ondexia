package com.ondexia.consultas;

import com.fasterxml.jackson.databind.JsonNode;
import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.consultas.ConsultaDeRuc;
import com.ondexia.domain.consultas.DatosDeRuc;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;

/**
 * apiperu.dev, el relevo.
 *
 * <p>{@code POST /ruc} con el número en el cuerpo —no en la ruta, a diferencia
 * de Decolecta.
 *
 * <h2>Qué no devuelve, y por qué importa</h2>
 *
 * <p><strong>No trae ubigeo</strong>, ni distrito ni provincia: solo la
 * dirección y el departamento. Y el ubigeo va en el comprobante electrónico.
 *
 * <p>Eso no invalida el relevo, pero cuando responde este proveedor la respuesta
 * llega incompleta a propósito, con {@code faltaUbigeo()} en cierto. Completarlo
 * es asunto de quien orquesta; aquí se dice la verdad sobre lo que llegó.
 */
class ProveedorApiPeru implements ConsultaDeRuc {

    private final ClienteDelPadron cliente;
    private final String base;

    ProveedorApiPeru(ClienteDelPadron cliente, String base) {
        this.cliente = cliente;
        this.base = base;
    }

    @Override
    public Optional<DatosDeRuc> consultar(Ruc ruc) {
        // El RUC son once digitos ya validados, asi que no hay nada que escapar
        // aqui; con cualquier otro dato esto tendria que construirse con Jackson.
        String cuerpo = "{\"ruc\":\"" + ruc.valor() + "\"}";

        return cliente.post(URI.create(base + "/ruc"), cuerpo)
                .map(json -> traducir(ruc, json.path("data")));
    }

    private static DatosDeRuc traducir(Ruc ruc, JsonNode datos) {
        return new DatosDeRuc(
                ruc,
                Campos.texto(datos, "nombre_o_razon_social"),
                VocabularioDeSunat.estado(Campos.texto(datos, "estado")),
                VocabularioDeSunat.condicion(Campos.texto(datos, "condicion")),
                Campos.texto(datos, "direccion"),
                // Sin ubigeo, distrito ni provincia: ver la cabecera. No es un olvido.
                null,
                null,
                null,
                Campos.texto(datos, "departamento"),
                datos.path("es_agente_de_retencion").asBoolean(false),
                datos.path("es_buen_contribuyente").asBoolean(false),
                // Tampoco forma societaria.
                null,
                Instant.now());
    }
}
