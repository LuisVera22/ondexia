package com.ondexia.infrastructure.salida.persistencia.identidad;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PermisoJpaRepository extends JpaRepository<PermisoJpa, UUID> {

    /**
     * Devuelve cadenas y no entidades: comprobar si alguien puede ejecutar una
     * acción es una pertenencia a conjunto, y materializar 200 entidades
     * gestionadas para leer un campo de cada una es trabajo puro.
     */
    @Query("select p.codigo from RolJpa r join r.permisos p where r.id = :rolId")
    Set<String> findCodigosByRolId(UUID rolId);

    /**
     * El catálogo entero, ordenado de forma que los tres niveles de una misma
     * rama salgan juntos y en orden: el módulo, su submódulo, y las funciones.
     *
     * <p>El orden por {@code nivel} descendente no es capricho: alfabéticamente
     * es {@code FUNCION < MODULO < SUBMODULO}, y al revés queda
     * {@code SUBMODULO, MODULO, FUNCION} — tampoco sirve. Se ordena explícitamente
     * con un CASE para que el árbol se pueda construir de una pasada.
     */
    @Query("""
            select p from PermisoJpa p
            order by
                case p.nivel when 'MODULO' then 0 when 'SUBMODULO' then 1 else 2 end,
                p.modulo asc,
                p.accion asc
            """)
    List<PermisoJpa> findCatalogoOrdenado();

    /**
     * Los módulos y submódulos que una cuenta tiene contratados.
     *
     * <p>Consulta nativa y no JPQL porque el modelo es «decisión sobre valor por
     * omisión» y eso necesita {@code coalesce} sobre dos uniones externas: manda
     * la fila de {@code cuenta_modulo} si existe, y si no, la pertenencia a
     * {@code plan_modulo}. Un {@code left join} con {@code coalesce} no se expresa
     * en JPQL sin retorcerlo, y este es el sitio equivocado para ser ingenioso.
     *
     * <p>El {@code case} sobre el nivel no es adorno. {@code plan_modulo} solo
     * lleva filas de MODULO, así que exigir pertenencia a un submódulo lo dejaba
     * SIEMPRE fuera de contrato — y con él todas sus funciones. Un submódulo se
     * incluye si ningún plan lo menciona; en cuanto algún plan lo menciona, solo
     * lo tienen los que lo mencionan. Es el mismo idioma de decisiones del doc 09
     * §4.2 aplicado al plan, y es lo que permitirá poner las guías de remisión en
     * los planes altos añadiendo dos filas, sin sembrar las demás.
     *
     * <p>La segunda mitad —el {@code exists}— es la que impide devolver un
     * submódulo cuyo módulo está apagado. Sin ella, apagar {@code almacen} dejaría
     * pasar {@code almacen.producto} y sus funciones: una puerta cerrada con las
     * ventanas abiertas.
     *
     * <p>No hay filtro por {@code cuenta_id} más allá del parámetro, y es
     * deliberado: estas tablas no tienen política de fila (doc 09 §3.1), así que
     * el aislamiento aquí lo pone esta consulta. Cambiarla por una que reciba la
     * cuenta de otro sitio es cambiar la frontera entre clientes.
     */
    @Query(value = """
            with decidido as (
                select p.modulo as modulo,
                       p.nivel  as nivel,
                       coalesce(
                           cm.habilitado,
                           case when p.nivel = 'MODULO'
                                then pm.plan_codigo is not null
                                else pm.plan_codigo is not null
                                     or not exists (select 1 from plan_modulo x
                                                    where x.permiso_id = p.id)
                           end) as contratado
                from permiso p
                left join cuenta_modulo cm
                       on cm.permiso_id = p.id
                      and cm.cuenta_id = cast(:cuentaId as uuid)
                left join plan_modulo pm
                       on pm.permiso_id = p.id
                      and pm.plan_codigo = (select c.plan from cuenta c where c.id = cast(:cuentaId as uuid))
                where p.nivel in ('MODULO', 'SUBMODULO')
            )
            select d.modulo
            from decidido d
            where d.contratado
              and (d.nivel = 'MODULO'
                   or exists (select 1
                              from decidido m
                              where m.nivel = 'MODULO'
                                and m.contratado
                                and m.modulo = split_part(d.modulo, '.', 1)))
            """, nativeQuery = true)
    Set<String> findModulosContratados(String cuentaId);
}
