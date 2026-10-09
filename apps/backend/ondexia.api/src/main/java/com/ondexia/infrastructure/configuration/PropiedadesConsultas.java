package com.ondexia.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * La clave pública con la que se comprueban las atestaciones de RUC (DT-19).
 *
 * <h2>Aquí no hay ningún secreto, y es el punto</h2>
 *
 * <p>La API no consulta a nadie: las claves de Decolecta y apiperu.dev viven en
 * {@code ondexia.consultas}, que es quien sale a internet, y la clave privada de
 * firma también. Lo único que llega aquí es la <strong>pública</strong>.
 *
 * <p>Eso es lo que permite pasarla por variable de entorno sin preocuparse. Con
 * un HMAC haría falta el mismo secreto en las dos partes, y la API no puede
 * leerlo de SSM —subred privada sin NAT— así que acabaría en el estado de
 * Terraform. Justo lo que este proyecto se quitó de encima al pasar la base de
 * datos a autenticación por IAM.
 *
 * @param firmaPublica clave Ed25519 en X.509, en base64; admite el envoltorio PEM
 */
@ConfigurationProperties(prefix = "ondexia.consultas")
public record PropiedadesConsultas(String firmaPublica) {

    public PropiedadesConsultas {
        // Una variable de entorno declarada y sin valor llega como cadena vacia,
        // no como nulo. Sin esto, CONSULTAS_FIRMA_PUBLICA= contaria como
        // configurada y el arranque fallaria al analizar una clave vacia en vez
        // de decir que no esta puesta.
        firmaPublica = firmaPublica == null || firmaPublica.isBlank() ? null : firmaPublica.trim();
    }
}
