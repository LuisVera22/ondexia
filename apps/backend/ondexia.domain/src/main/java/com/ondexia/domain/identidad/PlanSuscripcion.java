package com.ondexia.domain.identidad;

/**
 * Plan contratado.
 *
 * <p>Se persiste por nombre y no por posicion ({@code EnumType.STRING}): con
 * {@code ORDINAL}, insertar un valor nuevo en medio del enumerado reescribe en
 * silencio el significado de cada fila ya guardada.
 *
 * <p>Los limites de cada plan no viven aqui. Cuando existan seran una tabla,
 * porque una promocion o un plan a medida para un cliente grande no puede
 * exigir un despliegue.
 */
public enum PlanSuscripcion {
    ESENCIAL,
    PROFESIONAL,
    CORPORATIVO
}
