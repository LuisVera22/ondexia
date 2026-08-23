package com.ondexia.domain.consultas;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.Ubigeo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Lo que SUNAT dijo, firmado, para que viaje por el navegador sin que nadie lo
 * toque.
 *
 * <h2>Por qué existe</h2>
 *
 * <p>La comprobación de que un RUC está activo y habido tiene que ser del
 * servidor: si el navegador dijera «está habido» y le creyéramos, cualquiera se
 * registraría con lo que quisiera. Pero la Lambda de la API <strong>no puede
 * preguntar</strong> —subred privada sin NAT (DTE §4.8)— y darle salida cuesta
 * entre 7 y 32 USD/mes.
 *
 * <p>La firma resuelve las dos cosas a la vez. Consulta quien puede salir; la
 * API valida la firma sin red y sigue siendo la autoridad, porque el navegador
 * puede reenviar la atestación pero no fabricarla.
 *
 * <h2>Por qué va en el dominio</h2>
 *
 * <p>Porque la firman y la verifican dos desplegables distintos, y el formato es
 * el contrato entre ellos. Duplicado, firmante y verificador acabarían
 * divergiendo en un espacio o en el orden de un campo, y el síntoma sería «firma
 * inválida» sin nada que señale por qué.
 *
 * <p>No añade dependencias: HMAC y Base64 son de la biblioteca estándar.
 *
 * <h2>Se firma todo, no solo la puerta</h2>
 *
 * <p>DT-19 planteaba firmar el RUC, el estado y la condición. No basta: la
 * <strong>razón social</strong> también viene de SUNAT, y si viajara sin firmar,
 * el navegador podría cambiarla. Una razón social que no coincide con el padrón
 * hace que SUNAT rechace <em>todos</em> los comprobantes de esa empresa, y el
 * fallo aparecería en la primera emisión real, no al registrar.
 *
 * <p>La consecuencia es que el «no editable» de esos campos deja de ser una
 * convención de la interfaz —que se salta con las herramientas del navegador— y
 * pasa a ser una imposibilidad.
 *
 * <h2>El formato, y por qué no es JSON</h2>
 *
 * <p>Dos partes separadas por un punto, como un JWT en miniatura:
 * {@code base64url(carga) + "." + base64url(hmac)}.
 *
 * <p>La carga es una cadena con los campos en orden fijo separados por
 * {@code U+001F}, y <strong>se transmite tal cual</strong>. Eso es
 * deliberado: si se enviara un JSON y cada lado lo volviera a serializar para
 * firmar, cualquier diferencia —el orden de las claves, un espacio, cómo se
 * escribe un nulo— rompería la verificación. Firmando y transmitiendo los mismos
 * bytes, no hay nada que canonizar.
 *
 * <p>El separador es el carácter de unidad, que no puede aparecer en una razón
 * social ni en una dirección. Con un {@code |} o una coma habría que escapar, y
 * un escape mal hecho permite mover el contenido de un campo al siguiente.
 */
public final class Atestacion {

    /**
     * Va dentro de la firma, no al lado.
     *
     * <p>Si el número de versión quedara fuera, alguien podría cambiarlo para
     * que el verificador aplicara las reglas de otra versión a una carga firmada
     * con esta.
     */
    private static final String VERSION = "1";

    private static final char SEPARADOR = (char) 0x1F;
    private static final String ALGORITMO = "HmacSHA256";

    private Atestacion() {
    }

    /** Lo que la API recupera de una atestación válida. */
    public record Contenido(DatosDeRuc datos, Instant expiraEn) {
    }

    /**
     * Firma los datos.
     *
     * @param expiraEn corto, del orden de minutos: es el tiempo que hay entre
     *     consultar el RUC y enviar el formulario. Más allá, la foto del padrón
     *     envejece y quien la reenvía podría estar reutilizando una vieja
     */
    public static String emitir(DatosDeRuc datos, Instant expiraEn, byte[] secreto) {
        String carga = carga(datos, expiraEn);
        byte[] bytes = carga.getBytes(StandardCharsets.UTF_8);
        return base64(bytes) + "." + base64(hmac(bytes, secreto));
    }

    /**
     * Comprueba la firma y devuelve lo que había dentro.
     *
     * @throws AtestacionInvalida si la firma no cuadra, si caducó o si el
     *     formato no es el esperado. No se distingue el motivo hacia fuera a
     *     propósito: quien manipula una atestación no necesita ayuda para saber
     *     qué parte le falló
     */
    public static Contenido verificar(String token, byte[] secreto, Instant ahora) {
        if (token == null || token.isBlank()) {
            throw new AtestacionInvalida("La verificación del RUC no llegó.");
        }

        int punto = token.indexOf('.');
        if (punto <= 0 || punto == token.length() - 1) {
            throw new AtestacionInvalida("La verificación del RUC está mal formada.");
        }

        byte[] carga;
        byte[] firma;
        try {
            carga = Base64.getUrlDecoder().decode(token.substring(0, punto));
            firma = Base64.getUrlDecoder().decode(token.substring(punto + 1));
        } catch (IllegalArgumentException noEsBase64) {
            throw new AtestacionInvalida("La verificación del RUC está mal formada.");
        }

        // Comparacion en tiempo constante. Con un equals corriente, el tiempo de
        // respuesta revela cuantos bytes iniciales acerto quien lo intenta, y con
        // suficientes intentos eso permite construir una firma valida byte a
        // byte.
        if (!MessageDigest.isEqual(firma, hmac(carga, secreto))) {
            throw new AtestacionInvalida("La verificación del RUC no es válida.");
        }

        return leer(new String(carga, StandardCharsets.UTF_8), ahora);
    }

    private static String carga(DatosDeRuc datos, Instant expiraEn) {
        StringBuilder sb = new StringBuilder();
        sb.append(VERSION).append(SEPARADOR);
        sb.append(datos.ruc().valor()).append(SEPARADOR);
        sb.append(texto(datos.razonSocial())).append(SEPARADOR);
        sb.append(datos.estado().name()).append(SEPARADOR);
        sb.append(datos.condicion().name()).append(SEPARADOR);
        sb.append(texto(datos.domicilioFiscal())).append(SEPARADOR);
        sb.append(datos.ubigeo() == null ? "" : datos.ubigeo().valor()).append(SEPARADOR);
        sb.append(texto(datos.distrito())).append(SEPARADOR);
        sb.append(texto(datos.provincia())).append(SEPARADOR);
        sb.append(texto(datos.departamento())).append(SEPARADOR);
        sb.append(datos.esAgenteRetencion() ? "1" : "0").append(SEPARADOR);
        sb.append(datos.esBuenContribuyente() ? "1" : "0").append(SEPARADOR);
        sb.append(texto(datos.tipoSocietario())).append(SEPARADOR);
        sb.append(datos.consultadoEn().toEpochMilli()).append(SEPARADOR);
        sb.append(expiraEn.toEpochMilli());
        return sb.toString();
    }

    private static Contenido leer(String carga, Instant ahora) {
        // -1 para conservar los campos vacios del final: con el limite por
        // omision, un tipo societario nulo seguido de dos numeros no se notaria,
        // pero un cambio de orden futuro si, y el fallo seria un
        // ArrayIndexOutOfBounds en produccion.
        String[] c = carga.split(String.valueOf(SEPARADOR), -1);
        if (c.length != 15 || !VERSION.equals(c[0])) {
            throw new AtestacionInvalida("La verificación del RUC está mal formada.");
        }

        Instant expiraEn;
        DatosDeRuc datos;
        try {
            expiraEn = Instant.ofEpochMilli(Long.parseLong(c[14]));
            datos = new DatosDeRuc(
                    new Ruc(c[1]),
                    c[2],
                    EstadoContribuyente.valueOf(c[3]),
                    CondicionDomicilio.valueOf(c[4]),
                    vacioEsNulo(c[5]),
                    c[6].isEmpty() ? null : new Ubigeo(c[6]),
                    vacioEsNulo(c[7]),
                    vacioEsNulo(c[8]),
                    vacioEsNulo(c[9]),
                    "1".equals(c[10]),
                    "1".equals(c[11]),
                    vacioEsNulo(c[12]),
                    Instant.ofEpochMilli(Long.parseLong(c[13])));
        } catch (RuntimeException noSeEntiende) {
            // La firma era valida, asi que esto no es manipulacion: es que el
            // firmante y el verificador no estan de acuerdo en el formato. Casi
            // siempre, versiones distintas desplegadas.
            throw new AtestacionInvalida(
                    "La verificación del RUC no se pudo interpretar.", noSeEntiende);
        }

        // Lo ultimo, y no lo primero: comprobar la caducidad antes de la firma
        // dejaria responder «caducada» a una atestacion inventada, que es
        // informacion gratis sobre el formato para quien lo intenta.
        if (ahora.isAfter(expiraEn)) {
            throw new AtestacionInvalida(
                    "La verificación del RUC caducó. Vuelve a consultarlo.");
        }
        return new Contenido(datos, expiraEn);
    }

    private static byte[] hmac(byte[] mensaje, byte[] secreto) {
        if (secreto == null || secreto.length == 0) {
            throw new IllegalStateException(
                    "No hay secreto para firmar ni verificar atestaciones.");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto, ALGORITMO));
            return mac.doFinal(mensaje);
        } catch (java.security.GeneralSecurityException imposible) {
            // HmacSHA256 lo exige la especificacion de la plataforma; si falta,
            // la JVM esta rota y no hay nada que hacer aqui.
            throw new IllegalStateException("HMAC-SHA256 no disponible", imposible);
        }
    }

    private static String base64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }

    private static String vacioEsNulo(String valor) {
        return valor.isEmpty() ? null : valor;
    }
}
