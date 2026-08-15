package com.ondexia.admin.cuentas;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Qué tiene contratado una cuenta, y por qué.
 *
 * <p>Devuelve las dos cosas a la vez: la decisión escrita para la cuenta —si la
 * hay— y el resultado efectivo. Mostrar solo el efectivo dejaría al operador sin
 * saber si un módulo está encendido porque lo decidió alguien o porque viene con
 * el plan, que es justo la pregunta que se hace uno al mirar esta pantalla.
 */
@Service
@Transactional(readOnly = true)
public class ConsultaDeModulos {

    private final JdbcClient jdbc;

    public ConsultaDeModulos(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /*
     * Misma regla que aplica la API de clientes, escrita una segunda vez y aqui
     * esta el riesgo: si una cambia y la otra no, el panel mostraria algo
     * distinto de lo que el servidor autoriza. Es duplicacion consciente porque
     * el panel no puede depender de ondexia.api (doc 09 §6.1), y hay una prueba
     * en cada lado que fija el mismo comportamiento.
     */
    private static final String MODULOS = """
            select p.id                as id,
                   p.codigo            as codigo,
                   p.modulo            as modulo,
                   p.nivel             as nivel,
                   p.nombre            as nombre,
                   cm.habilitado       as decision,
                   coalesce(
                       cm.habilitado,
                       case when p.nivel = 'MODULO'
                            then pm.plan_codigo is not null
                            else pm.plan_codigo is not null
                                 or not exists (select 1 from plan_modulo x
                                                where x.permiso_id = p.id)
                       end)            as contratado
            from permiso p
            left join cuenta_modulo cm
                   on cm.permiso_id = p.id and cm.cuenta_id = :cuenta
            left join plan_modulo pm
                   on pm.permiso_id = p.id
                  and pm.plan_codigo = (select c.plan from cuenta c where c.id = :cuenta)
            where p.nivel in ('MODULO', 'SUBMODULO')
            -- Cada modulo seguido de SUS submodulos, y no todos los modulos
            -- primero. La pantalla sangra las filas SUBMODULO, asi que el orden
            -- es lo unico que dice de quien cuelgan: ordenando por nivel
            -- primero, los submodulos de Almacen aparecian debajo de Ventas y
            -- parecian suyos. El fallo no se ve en los datos, se ve en la
            -- pantalla, que es donde alguien decide apagar un modulo.
            order by p.modulo, case p.nivel when 'MODULO' then 0 else 1 end, p.nombre
            """;

    public List<ModuloContratado> de(UUID cuentaId) {
        return jdbc.sql(MODULOS)
                .param("cuenta", cuentaId)
                .query(ModuloContratado.class)
                .list();
    }
}
