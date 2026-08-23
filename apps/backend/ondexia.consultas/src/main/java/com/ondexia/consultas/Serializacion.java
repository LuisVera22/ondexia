package com.ondexia.consultas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ondexia.domain.consultas.DatosDeRuc;

/**
 * Cómo salen los datos hacia el formulario.
 *
 * <p>Se escribe campo a campo en vez de dejar que Jackson refleje el record.
 * Reflejándolo, el JSON público cambiaría de forma cada vez que alguien añada o
 * renombre un campo del dominio, y el frontend se rompería sin que el cambio
 * pareciera tener nada que ver con él. Aquí la frontera es explícita.
 *
 * <p>Los nombres son los que usa el resto de la API: camelCase, no los
 * snake_case de los proveedores.
 */
final class Serializacion {

    private Serializacion() {
    }

    static ObjectNode aJson(ObjectMapper json, DatosDeRuc datos) {
        ObjectNode nodo = json.createObjectNode();
        nodo.put("ruc", datos.ruc().valor());
        nodo.put("razonSocial", datos.razonSocial());
        nodo.put("estado", datos.estado().name());
        nodo.put("condicion", datos.condicion().name());
        nodo.put("domicilioFiscal", datos.domicilioFiscal());
        nodo.put("ubigeo", datos.ubigeo() == null ? null : datos.ubigeo().valor());
        nodo.put("distrito", datos.distrito());
        nodo.put("provincia", datos.provincia());
        nodo.put("departamento", datos.departamento());
        nodo.put("esAgenteRetencion", datos.esAgenteRetencion());
        nodo.put("esBuenContribuyente", datos.esBuenContribuyente());
        nodo.put("tipoSocietario", datos.tipoSocietario());

        // Para que la interfaz pueda decir «ACTIVO, comprobado hace un momento»
        // en vez de afirmarlo sin fecha.
        nodo.put("consultadoEn", datos.consultadoEn().toString());

        // Se envia calculado y no se deja al frontend repetir la regla: dos
        // implementaciones de la puerta del registro acabarian discrepando, y la
        // que importa es la del servidor.
        nodo.put("aptaParaRegistro", datos.aptaParaRegistro());
        nodo.put("motivoDeRechazo", datos.motivoDeRechazo());
        return nodo;
    }
}
