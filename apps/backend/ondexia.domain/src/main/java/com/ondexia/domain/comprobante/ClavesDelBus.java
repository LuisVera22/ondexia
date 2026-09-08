package com.ondexia.domain.comprobante;

import java.util.UUID;

/**
 * Dónde queda cada cosa en el bucket del bus (doc 14 §2 y §4).
 *
 * <p>Está en el dominio y no repartido entre la API y el Emisor porque los dos
 * lo leen: si la API escribiera la orden en un prefijo y el Emisor escuchara
 * otro, nada fallaría y nada se emitiría. Un solo sitio, sin lógica.
 *
 * <pre>
 *   pendientes/{empresaId}/{ordenId}.json   la API escribe, el Emisor lee y borra
 *   resultados/{empresaId}/{ordenId}.json   el Emisor escribe, la API lee
 *   errores/{ordenId}.txt                   el Emisor escribe cuando ni siquiera
 *                                           pudo producir un resultado
 *   documentos/{ruc}/{nombre}.xml           XML firmado; el nombre es el de SUNAT
 *   documentos/{ruc}/R-{nombre}.zip         el CDR tal como llegó
 *   certificados/{ruc}.pfx                  sube el navegador; lee el Emisor
 *   credenciales/{ruc}.json                 clave SOL y contraseña del .pfx;
 *                                           sube el navegador, lee el Emisor.
 *                                           La API NO tiene lectura sobre estos dos
 * </pre>
 */
public final class ClavesDelBus {

    public static final String PENDIENTES = "pendientes/";
    public static final String RESULTADOS = "resultados/";
    public static final String ERRORES = "errores/";
    public static final String DOCUMENTOS = "documentos/";
    public static final String CERTIFICADOS = "certificados/";
    public static final String CREDENCIALES = "credenciales/";

    private ClavesDelBus() {
    }

    public static String pendiente(UUID empresaId, UUID ordenId) {
        return PENDIENTES + empresaId + "/" + ordenId + ".json";
    }

    public static String resultado(UUID empresaId, UUID ordenId) {
        return RESULTADOS + empresaId + "/" + ordenId + ".json";
    }

    public static String error(UUID ordenId) {
        return ERRORES + ordenId + ".txt";
    }

    public static String xml(String ruc, String nombreDeArchivo) {
        return DOCUMENTOS + ruc + "/" + nombreDeArchivo + ".xml";
    }

    public static String cdr(String ruc, String nombreDeArchivo) {
        return DOCUMENTOS + ruc + "/R-" + nombreDeArchivo + ".zip";
    }

    public static String certificado(String ruc) {
        return CERTIFICADOS + ruc + ".pfx";
    }

    public static String credenciales(String ruc) {
        return CREDENCIALES + ruc + ".json";
    }
}
