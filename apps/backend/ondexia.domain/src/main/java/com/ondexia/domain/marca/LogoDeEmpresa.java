package com.ondexia.domain.marca;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Los tres archivos de marca de una empresa, con lo que cada uno admite.
 *
 * <h2>PNG y JPG. No SVG.</h2>
 *
 * <p>Un SVG es un documento XML que puede llevar JavaScript dentro, y este es el
 * único sitio del sistema donde un usuario sube un archivo que después se
 * muestra a otros. Servirlo desde otro dominio —como ya hace el bucket de marca
 * (DTE §5.8)— reduce el daño pero no cierra el caso: el logo acaba embebido en
 * PDF y en la barra superior.
 *
 * <p>Se pierde poco. Un logo vectorial se exporta a PNG de 1024 px una vez, y a
 * cambio desaparece el vector «suba su logo» convertido en XSS almacenado.
 *
 * <h2>Los límites son del dominio, no del formulario</h2>
 *
 * <p>El navegador comprueba el tamaño antes de subir, pero esa comprobación la
 * salta cualquiera que llame a la API directamente. Aquí es donde de verdad se
 * decide, y por eso el servidor firma la subida <em>por</em> un tamaño concreto y
 * después comprueba contra S3 lo que realmente llegó.
 */
public enum LogoDeEmpresa {

    /** Encabezado de la aplicación, PDF de comprobantes e informes. */
    PRINCIPAL("logo_principal", "Logo principal", 1_048_576L),

    /**
     * Impresión térmica de boletas.
     *
     * <p>Más pequeño que el principal a propósito: una impresora de ticket
     * imprime a 384 px de ancho, así que un archivo de un megabyte solo puede
     * ser un logo equivocado.
     */
    TICKET("logo_ticket", "Logo para ticket", 262_144L),

    /** Barra lateral colapsada y pestaña del navegador. */
    SIMBOLO("simbolo", "Símbolo", 262_144L);

    /** Los que el navegador anuncia para PNG y JPG. */
    private static final List<String> TIPOS_ADMITIDOS = List.of("image/png", "image/jpeg");

    private final String columna;
    private final String nombre;
    private final long maximoBytes;

    LogoDeEmpresa(String columna, String nombre, long maximoBytes) {
        this.columna = columna;
        this.nombre = nombre;
        this.maximoBytes = maximoBytes;
    }

    /** Nombre del hueco, tal como viaja en la URL: {@code logo_principal}. */
    public String columna() {
        return columna;
    }

    public String nombre() {
        return nombre;
    }

    public long maximoBytes() {
        return maximoBytes;
    }

    public static LogoDeEmpresa porColumna(String columna) {
        return Arrays.stream(values())
                .filter(logo -> logo.columna.equalsIgnoreCase(columna))
                .findFirst()
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "logo_desconocido",
                        "'" + columna + "' no es un logo de la empresa."));
    }

    /**
     * Comprueba tipo y tamaño, y devuelve la extensión que le corresponde.
     *
     * <p>Se valida <strong>antes</strong> de firmar y <strong>otra vez</strong>
     * contra lo que S3 recibió. La primera vez es para no firmar una subida que
     * vamos a rechazar; la segunda porque entre la firma y la subida el cliente
     * puede mandar otra cosa, y quien llama a la API a mano lo hará.
     */
    public String exigirValido(String tipoContenido, long bytes) {
        String tipo = tipoContenido == null ? "" : tipoContenido.trim().toLowerCase(Locale.ROOT);

        if (!TIPOS_ADMITIDOS.contains(tipo)) {
            throw new ReglaDeNegocioViolada(
                    "formato_no_admitido",
                    "El " + nombre.toLowerCase(Locale.ROOT) + " admite PNG o JPG. "
                            + "El SVG no se acepta: puede llevar código dentro.");
        }

        if (bytes <= 0) {
            throw new ReglaDeNegocioViolada(
                    "archivo_vacio", "El archivo está vacío.");
        }

        if (bytes > maximoBytes) {
            throw new ReglaDeNegocioViolada(
                    "archivo_demasiado_grande",
                    "El " + nombre.toLowerCase(Locale.ROOT) + " no puede pasar de "
                            + (maximoBytes / 1024) + " KB. El tuyo son "
                            + (bytes / 1024) + " KB.");
        }

        return "image/png".equals(tipo) ? "png" : "jpg";
    }
}
