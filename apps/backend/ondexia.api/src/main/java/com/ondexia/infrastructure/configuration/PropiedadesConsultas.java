package com.ondexia.infrastructure.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Proveedores de consulta a fuentes públicas (DT-19).
 *
 * <h2>Por qué las claves son opcionales</h2>
 *
 * <p>Porque no todos los entornos tienen todas. En local suele haber una sola;
 * en la Lambda de la API no hay ninguna, y ahí no es un olvido: esa Lambda no
 * tiene salida a internet y quien consulta es {@code ondexia.consultas}. Un
 * arranque que exigiera la clave dejaría la API sin levantar en el único entorno
 * donde no le hace falta.
 *
 * <p>Lo que sí es obligatorio es que la ausencia se note. Sin ninguna clave no
 * se registra un {@code ConsultaDeRuc} silencioso que devuelva vacío —eso diría
 * «ese RUC no existe» a todo el mundo—, sino uno que falla diciendo que no está
 * configurado.
 *
 * @param tiempoDeEspera por proveedor, no total
 */
@ConfigurationProperties(prefix = "ondexia.consultas")
public record PropiedadesConsultas(
        String decolectaUrl,
        String decolectaToken,
        String apiperuUrl,
        String apiperuToken,
        Duration tiempoDeEspera) {

    public PropiedadesConsultas {
        decolectaUrl = valorODefecto(decolectaUrl, "https://api.decolecta.com/v1");
        apiperuUrl = valorODefecto(apiperuUrl, "https://api.apiperu.dev");

        // Corto a proposito. API Gateway corta a los 29 s, y aqui hay dos
        // proveedores en cascada mas el trabajo de la propia peticion: con 6 s
        // por proveedor, el peor caso cabe con margen. Un tiempo generoso
        // convierte un proveedor lento en un 504 opaco de la pasarela, que es
        // el error mas dificil de diagnosticar de los dos.
        tiempoDeEspera = tiempoDeEspera == null ? Duration.ofSeconds(6) : tiempoDeEspera;

        decolectaToken = enBlancoEsNulo(decolectaToken);
        apiperuToken = enBlancoEsNulo(apiperuToken);
    }

    public boolean tieneDecolecta() {
        return decolectaToken != null;
    }

    public boolean tieneApiPeru() {
        return apiperuToken != null;
    }

    /**
     * Una variable de entorno sin definir llega como cadena vacía, no como nulo.
     * Sin esto, {@code CONSULTAS_DECOLECTA_TOKEN=} contaría como configurado y
     * el proveedor se llamaría con un Bearer vacío.
     */
    private static String enBlancoEsNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static String valorODefecto(String valor, String defecto) {
        return valor == null || valor.isBlank() ? defecto : valor.trim();
    }
}
