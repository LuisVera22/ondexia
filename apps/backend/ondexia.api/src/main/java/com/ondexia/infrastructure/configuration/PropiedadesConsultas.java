package com.ondexia.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * El secreto con el que se comprueban las atestaciones de RUC (DT-19).
 *
 * <h2>Aquí no hay claves de proveedor</h2>
 *
 * <p>Y es el punto: la API no consulta a nadie. Las claves de Decolecta y
 * apiperu.dev viven en {@code ondexia.consultas}, que es quien sale a internet.
 * Lo único que la API necesita es el secreto compartido para verificar la firma,
 * porque verificar no requiere red.
 *
 * <h2>Dos formas de obtenerlo, y el orden importa</h2>
 *
 * <p>Si hay nombre de parámetro, manda el parámetro de SSM. Si no, la variable
 * con el valor directo, que es para desarrollo. En ese orden: en un entorno
 * desplegado, una variable de entorno olvidada no debe poder ganarle al
 * parámetro.
 *
 * @param firmaParametro nombre del parámetro en SSM, no su valor
 * @param firmaSecreto el valor, para desarrollo local
 */
@ConfigurationProperties(prefix = "ondexia.consultas")
public record PropiedadesConsultas(String firmaParametro, String firmaSecreto) {

    public PropiedadesConsultas {
        firmaParametro = enBlancoEsNulo(firmaParametro);
        firmaSecreto = enBlancoEsNulo(firmaSecreto);
    }

    /**
     * Una variable de entorno declarada y sin valor llega como cadena vacía, no
     * como nulo. Sin esto, {@code CONSULTAS_FIRMA_SECRETO=} contaría como
     * configurado y se verificarían firmas con una clave vacía — que cualquiera
     * puede reproducir.
     */
    private static String enBlancoEsNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
