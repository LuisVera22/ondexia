package com.ondexia.facturacion;

/**
 * Lo que el navegador dejó en {@code credenciales/<ruc>.json} (doc 14 §4): la
 * contraseña del {@code .pfx} y la clave SOL. Se leen por orden y se descartan;
 * ninguna de las dos aparece en registros, resultados ni excepciones.
 */
public record Credenciales(String claveCertificado, String claveSol) {

    public boolean completas() {
        return claveCertificado != null && !claveCertificado.isBlank()
                && claveSol != null && !claveSol.isBlank();
    }
}
