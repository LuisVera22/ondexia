package com.ondexia.domain.identidad;

/**
 * Cuánto puede tener esta cuenta y cuánto tiene.
 *
 * <h2>{@code null} es «sin límite», no «cero»</h2>
 *
 * <p>Es la trampa de este objeto y la razón de que no sea un {@code int}. El
 * plan a demanda del doc 04 §2.2 es negociable por cliente, y se representa con
 * ausencia de límite. Con un entero, «sin límite» tendría que ser un centinela
 * —cero, o {@code Integer.MAX_VALUE}— y el día que alguien compare con
 * {@code <} sin acordarse, el cliente que paga más es el único que no puede
 * crear nada.
 *
 * @param maxEmpresas techo efectivo, ya resuelto: lo pactado con la cuenta si
 *     hay algo pactado, y si no lo que dé el plan. {@code null} es sin límite
 * @param empresasUsadas cuántas hay dadas de alta ahora mismo
 */
public record LimitesDeCuenta(Integer maxEmpresas, int empresasUsadas) {

    /**
     * Si cabe una empresa más.
     *
     * <p>La comparación es {@code >=} y no {@code >}: con el cupo justo lleno
     * —dos de dos— no cabe otra. Escrito con {@code >} se cuela siempre una de
     * más, y es el error que nadie ve hasta que un cliente del plan de dos tiene
     * tres empresas.
     */
    public boolean cabeOtraEmpresa() {
        return maxEmpresas == null || empresasUsadas < maxEmpresas;
    }

    /**
     * Cómo se le explica a quien se topa con el límite.
     *
     * <p>Con las cifras dentro. «Has alcanzado el límite de tu plan» obliga a
     * buscar cuál es ese límite; «2 de 2» se entiende sin salir de la pantalla y
     * deja claro que hace falta cambiar de plan, no reintentar.
     */
    public String motivoDelTope() {
        if (cabeOtraEmpresa()) {
            return null;
        }
        return "Tu plan permite " + maxEmpresas + " empresa"
                + (maxEmpresas == 1 ? "" : "s") + " y ya tienes " + empresasUsadas
                + ". Para registrar otra hay que ampliar el plan.";
    }
}
