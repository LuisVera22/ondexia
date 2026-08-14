package com.ondexia.domain.identidad;

import java.util.Set;
import java.util.UUID;

/**
 * Qué módulos tiene contratados una cuenta.
 *
 * <p>Es la frontera comercial, y es distinta de los permisos: el rol dice qué
 * puede hacer <em>esta persona</em>, y esto dice qué está disponible <em>para
 * esta cuenta</em>. Un administrador con todos los permisos del mundo no entra a
 * un módulo que su cuenta no tiene.
 *
 * <p>Se resuelve como decisión sobre plan: manda {@code cuenta_modulo} si hay
 * fila, y si no manda {@code plan_modulo}. Ver doc 09 §4.2.
 */
public interface ModulosContratadosRepositorio {

    /**
     * Códigos de módulo y submódulo contratados, <strong>sin acción</strong>:
     * {@code almacen}, {@code almacen.producto}. Es lo que
     * {@link Permisos#limitadoA(Set)} espera recibir.
     *
     * <p>Un submódulo solo sale si su módulo también está contratado. Devolver un
     * submódulo cuyo módulo está apagado dejaría pasar permisos por debajo de una
     * puerta cerrada.
     */
    Set<String> contratadosDe(UUID cuentaId);
}
