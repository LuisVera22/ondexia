package com.ondexia.domain.consultas;

/**
 * Estado del contribuyente en el padrón de SUNAT.
 *
 * <p>La lista es la de SUNAT y no una simplificación nuestra. Reducirla a
 * «activo o no» parece inofensivo hasta que hay que explicarle a alguien por qué
 * no puede registrar su empresa: una baja definitiva y una suspensión temporal
 * llevan a la misma negativa pero a conversaciones muy distintas, y el mensaje
 * solo puede distinguirlas si el dato las distingue.
 */
public enum EstadoContribuyente {

    ACTIVO,
    BAJA_PROVISIONAL,
    BAJA_DEFINITIVA,
    BAJA_PROVISIONAL_OFICIO,
    SUSPENSION_TEMPORAL,
    INSCRIPCION_OFICIO;

    /**
     * Si SUNAT lo reconoce como contribuyente en ejercicio.
     *
     * <p>Solo {@link #ACTIVO}. No es una lista de excepciones porque un estado
     * nuevo en el padrón debe entrar por defecto como «no apto»: si el criterio
     * fuera «todo menos las bajas», un valor que no previmos permitiría emitir.
     */
    public boolean permiteEmitir() {
        return this == ACTIVO;
    }
}
