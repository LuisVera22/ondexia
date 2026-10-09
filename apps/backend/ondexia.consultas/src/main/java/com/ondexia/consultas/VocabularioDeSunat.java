package com.ondexia.consultas;

import com.ondexia.domain.consultas.CondicionDomicilio;
import com.ondexia.domain.consultas.ConsultaNoDisponible;
import com.ondexia.domain.consultas.EstadoContribuyente;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Traduce las cadenas del padrón a los enumerados del dominio.
 *
 * <h2>Por qué esto merece existir aparte</h2>
 *
 * <p>Porque cada proveedor escribe lo mismo de forma distinta —«SUSPENSION
 * TEMPORAL», «SUSPENSIÓN TEMPORAL», «BAJA PROVISIONAL DE OFICIO», «BAJA PROV.
 * DE OFICIO»— y la traducción es el punto donde una diferencia de puntuación se
 * convierte en una decisión fiscal equivocada. Escrito dentro de cada adaptador,
 * se corregiría en uno y no en el otro.
 *
 * <h2>Lo que no hace: adivinar</h2>
 *
 * <p>Una cadena que no reconoce <strong>no</strong> se convierte en el valor más
 * parecido ni en un defecto. Falla.
 *
 * <p>La tentación es tratar lo desconocido como «no activo» y seguir: parece
 * seguro porque rechaza. No lo es — deja pasar el caso inverso silenciosamente
 * el día que el proveedor cambie «ACTIVO» por «Activo», y entonces se rechaza a
 * todo el mundo sin que ningún registro diga por qué. Fallando, el problema
 * aparece con nombre en el primer intento.
 */
final class VocabularioDeSunat {

    private VocabularioDeSunat() {
    }

    static EstadoContribuyente estado(String crudo) {
        return switch (normalizar(crudo)) {
            case "ACTIVO" -> EstadoContribuyente.ACTIVO;
            case "BAJA PROVISIONAL" -> EstadoContribuyente.BAJA_PROVISIONAL;
            case "BAJA DEFINITIVA" -> EstadoContribuyente.BAJA_DEFINITIVA;
            case "BAJA PROVISIONAL DE OFICIO", "BAJA PROV DE OFICIO",
                 "BAJA PROVISIONAL OFICIO", "BAJA PROV POR OFICIO" ->
                    EstadoContribuyente.BAJA_PROVISIONAL_OFICIO;
            case "SUSPENSION TEMPORAL" -> EstadoContribuyente.SUSPENSION_TEMPORAL;
            case "INSCRIPCION DE OFICIO", "INSCRIPCION OFICIO" ->
                    EstadoContribuyente.INSCRIPCION_OFICIO;
            default -> throw noSeEntiende("estado", crudo);
        };
    }

    static CondicionDomicilio condicion(String crudo) {
        return switch (normalizar(crudo)) {
            case "HABIDO" -> CondicionDomicilio.HABIDO;
            case "NO HABIDO", "NO HABIDO OFICIO" -> CondicionDomicilio.NO_HABIDO;
            case "NO HALLADO" -> CondicionDomicilio.NO_HALLADO;
            case "POR VERIFICAR", "PENDIENTE", "-" -> CondicionDomicilio.POR_VERIFICAR;
            default -> throw noSeEntiende("condicion", crudo);
        };
    }

    /**
     * Mayúsculas, sin tildes, sin puntos y con un solo espacio.
     *
     * <p>Se quitan las tildes porque un proveedor manda «SUSPENSIÓN» y otro
     * «SUSPENSION», y ninguna de las dos formas es más correcta que la otra para
     * este propósito. Se quitan los puntos por «BAJA PROV. DE OFICIO».
     */
    private static String normalizar(String crudo) {
        if (crudo == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(crudo, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinTildes.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * No reintentable: el proveedor respondió, y volver a preguntar devolverá la
     * misma cadena que no sabemos leer. Lo que hace falta es código nuevo.
     */
    private static ConsultaNoDisponible noSeEntiende(String campo, String crudo) {
        return new ConsultaNoDisponible(
                "consulta_respuesta_ilegible",
                "La consulta devolvió un " + campo + " que no reconocemos: «" + crudo + "».",
                false);
    }
}
