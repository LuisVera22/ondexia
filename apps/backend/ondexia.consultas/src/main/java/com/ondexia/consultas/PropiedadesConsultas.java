package com.ondexia.consultas;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de las consultas al padrón.
 *
 * <h2>Cada clave, en dos formas</h2>
 *
 * <p>{@code …Parametro} es el <strong>nombre</strong> de un parámetro de SSM;
 * {@code …Token} o {@code …Privada}, el valor directo. Manda el parámetro si
 * está, y ese orden importa: en un entorno desplegado una variable de entorno
 * olvidada no debe poder ganarle al parámetro, porque el síntoma sería firmar con
 * una clave distinta de la que la API espera y el error diría «la verificación no
 * es válida» sin mencionar la configuración.
 *
 * <p>El valor directo existe para desarrollo. Sin él haría falta credenciales de
 * AWS y un parámetro creado para consultar un RUC desde un portátil, y la
 * alternativa habitual a eso es que alguien pegue la clave en el código «solo un
 * momento».
 *
 * <h2>Ninguna clave tiene valor por omisión</h2>
 *
 * <p>Un defecto de relleno haría que la aplicación llamara al proveedor con un
 * Bearer inválido, y el error sería un 401 en vez de «no está configurado». Lo
 * mismo con la firma: firmar con una clave vacía haría que las dos partes
 * «funcionaran» sin que la firma protegiera de nada.
 *
 * @param tiempoDeEspera por proveedor, no total
 */
@ConfigurationProperties(prefix = "ondexia.consultas")
public record PropiedadesConsultas(
        String firmaParametro,
        String firmaPrivada,
        String decolectaUrl,
        String decolectaParametro,
        String decolectaToken,
        String apiperuUrl,
        String apiperuParametro,
        String apiperuToken,
        Duration tiempoDeEspera) {

    public PropiedadesConsultas {
        decolectaUrl = valorODefecto(decolectaUrl, "https://api.decolecta.com/v1");
        apiperuUrl = valorODefecto(apiperuUrl, "https://api.apiperu.dev");

        /*
         * Corto a proposito. API Gateway corta a los 29 s, y aqui hay dos
         * proveedores en cascada: con 6 s cada uno, el peor caso cabe con
         * margen. Un tiempo generoso convierte un proveedor lento en un 504
         * opaco de la pasarela, que es el error mas dificil de diagnosticar de
         * los dos.
         */
        tiempoDeEspera = tiempoDeEspera == null ? Duration.ofSeconds(6) : tiempoDeEspera;

        firmaParametro = enBlancoEsNulo(firmaParametro);
        firmaPrivada = enBlancoEsNulo(firmaPrivada);
        decolectaParametro = enBlancoEsNulo(decolectaParametro);
        decolectaToken = enBlancoEsNulo(decolectaToken);
        apiperuParametro = enBlancoEsNulo(apiperuParametro);
        apiperuToken = enBlancoEsNulo(apiperuToken);
    }

    /**
     * Qué claves de proveedor llegaron, para el mensaje de arranque.
     *
     * <p>Existe porque «no hay proveedor configurado» es cierto y no sirve: con
     * cuatro sitios de donde puede venir el valor, y dos formas de escribir cada
     * uno mal, hay que ver los cuatro a la vez. Un nombre de variable con una
     * letra cambiada se detecta de un vistazo aquí, y de ninguna otra forma.
     *
     * <p>Va la longitud y no el valor. Una clave a medio pegar es el fallo más
     * frecuente después del nombre mal escrito, y la longitud lo delata sin
     * escribir el secreto en un registro que CloudWatch conserva.
     */
    public String resumenDeProveedores() {
        return "decolecta-parametro (CONSULTAS_DECOLECTA_PARAMETRO) " + presencia(decolectaParametro)
                + "; decolecta-token (CONSULTAS_DECOLECTA_TOKEN) " + presencia(decolectaToken)
                + "; apiperu-parametro (CONSULTAS_APIPERU_PARAMETRO) " + presencia(apiperuParametro)
                + "; apiperu-token (CONSULTAS_APIPERU_TOKEN) " + presencia(apiperuToken);
    }

    private static String presencia(String valor) {
        return valor == null ? "ausente" : "presente, " + valor.length() + " caracteres";
    }

    /** Si algún valor hay que ir a buscarlo a SSM. */
    public boolean necesitaSsm() {
        return firmaParametro != null || decolectaParametro != null || apiperuParametro != null;
    }

    /**
     * Una variable de entorno declarada y sin valor llega como cadena vacía, no
     * como nulo. Sin esto, {@code CONSULTAS_DECOLECTA_TOKEN=} contaría como
     * configurada y el proveedor se llamaría con un Bearer vacío — un 401 con
     * todo el aspecto de un problema ajeno.
     */
    private static String enBlancoEsNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static String valorODefecto(String valor, String defecto) {
        return valor == null || valor.isBlank() ? defecto : valor.trim();
    }
}
