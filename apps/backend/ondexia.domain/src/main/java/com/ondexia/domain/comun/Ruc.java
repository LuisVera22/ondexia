package com.ondexia.domain.comun;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;

/**
 * Registro Único de Contribuyentes.
 *
 * <p><strong>No existe un {@code Ruc} inválido en memoria.</strong> Esa es toda
 * la idea del value object: la validación ocurre al construirlo, así que a
 * partir de ahí ningún método necesita volver a comprobarla. Con un
 * {@code String}, cada sitio que lo recibe tiene que decidir si confía o
 * revalida, y basta con que uno decida mal.
 *
 * @param valor once dígitos, con el verificador correcto
 */
public record Ruc(String valor) {

    /**
     * Pesos del algoritmo de SUNAT, aplicados a los diez primeros dígitos.
     *
     * <p>No es un invento nuestro: es el mismo dígito verificador que usa el
     * padrón. Comprobarlo en local no dice que el RUC exista —para eso está
     * {@code ConsultaDeRuc}— pero ataja la errata de tecleo, que es el error
     * frecuente, y ahorra una llamada de red por cada una.
     */
    private static final int[] PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    public Ruc {
        if (valor == null || valor.isBlank()) {
            throw new ReglaDeNegocioViolada("ruc_requerido", "El RUC es obligatorio.");
        }
        valor = valor.trim();

        if (!valor.matches("\\d{11}")) {
            throw new ReglaDeNegocioViolada(
                    "ruc_invalido", "El RUC debe tener exactamente 11 dígitos.");
        }
        if (verificadorDe(valor) != valor.charAt(10) - '0') {
            throw new ReglaDeNegocioViolada(
                    "ruc_invalido",
                    "El RUC " + valor + " no es válido: el dígito verificador no cuadra. "
                            + "Suele ser un error de tecleo.");
        }
    }

    private static int verificadorDe(String digitos) {
        int suma = 0;
        for (int i = 0; i < PESOS.length; i++) {
            suma += (digitos.charAt(i) - '0') * PESOS[i];
        }
        int resto = 11 - (suma % 11);
        return resto >= 10 ? resto - 10 : resto;
    }

    /**
     * Los dos primeros dígitos indican el tipo de contribuyente. {@code 10} es
     * persona natural con negocio, {@code 20} persona jurídica.
     */
    public boolean esPersonaJuridica() {
        return valor.startsWith("20");
    }

    @Override
    public String toString() {
        return valor;
    }
}
