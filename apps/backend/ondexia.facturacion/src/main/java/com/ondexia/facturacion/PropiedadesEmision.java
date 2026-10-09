package com.ondexia.facturacion;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del Emisor.
 *
 * <p>No hay ninguna clave aquí. Las de cada empresa —contraseña del certificado
 * y clave SOL— viven en el bucket, por RUC, y se leen por orden
 * ({@code credenciales/<ruc>.json}). Lo único que se configura es dónde está el
 * bucket y a qué URL de SUNAT se envía según el modo.
 *
 * @param bucket el del bus; vacío en local, donde el bus es un directorio
 * @param directorioLocal dónde va el bus en local
 * @param urlBeta el servicio de pruebas de SUNAT
 * @param urlProduccion el real. Lo que se envía aquí tiene valor tributario
 * @param tiempoDeEspera por envío. SUNAT tarda segundos; el Emisor no tiene la
 *                       pasarela detrás cortando a los 29 s, así que puede ser generoso
 */
@ConfigurationProperties(prefix = "ondexia.emision")
public record PropiedadesEmision(
        String bucket,
        String directorioLocal,
        String urlBeta,
        String urlProduccion,
        Duration tiempoDeEspera) {

    public PropiedadesEmision {
        directorioLocal = enBlanco(directorioLocal) ? "./bus-local" : directorioLocal.trim();
        urlBeta = enBlanco(urlBeta)
                ? "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService" : urlBeta.trim();
        urlProduccion = enBlanco(urlProduccion)
                ? "https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService" : urlProduccion.trim();
        tiempoDeEspera = tiempoDeEspera == null ? Duration.ofSeconds(40) : tiempoDeEspera;
    }

    private static boolean enBlanco(String valor) {
        return valor == null || valor.isBlank();
    }
}
