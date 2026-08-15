package com.ondexia.admin.cuentas;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las cuentas cliente y su consumo.
 *
 * <h2>SQL y no JPA</h2>
 *
 * <p>Esto es una consulta de agregado: por cada cuenta hay que contar empresas y
 * usuarios. Con entidades habría que traerse las colecciones —o pelearse con
 * proyecciones— para acabar mostrando dos números. Y traer el modelo de
 * entidades significaría heredar las asignaciones de escritura sobre tablas de
 * inquilino, que es justo lo que el rol de este servicio tiene prohibido en la
 * base (doc 09 §6.2).
 *
 * <p>Aquí se ve lo que el panel toca, en la propia consulta: {@code cuenta},
 * {@code plan}, {@code empresa} y {@code usuario}. Nada de comprobantes.
 */
@Service
@Transactional(readOnly = true)
public class ConsultaDeCuentas {

    private final JdbcClient jdbc;

    public ConsultaDeCuentas(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /*
     * El limite efectivo es `coalesce(cuenta.limite_*, plan.max_*)`: manda lo
     * pactado con la cuenta y, si no hay pacto, lo que da su plan. Nulo en los
     * dos significa sin limite, y por eso el campo viaja como Integer y no como
     * int — un cero seria «no puede tener ninguna», que es lo contrario.
     *
     * Los usuarios se cuentan solo activos. Un usuario desactivado no entra al
     * sistema, asi que no consume plan: contarlo obligaria al cliente a subir de
     * plan por gente que ya no trabaja con el.
     */
    private static final String CUENTAS = """
            select c.id                                          as id,
                   c.nombre                                      as nombre,
                   c.plan                                        as plan_codigo,
                   p.nombre                                      as plan_nombre,
                   c.estado_suscripcion                          as estado_suscripcion,
                   coalesce(c.limite_empresas, p.max_empresas)   as limite_empresas,
                   coalesce(c.limite_usuarios, p.max_usuarios)   as limite_usuarios,
                   (select count(*) from empresa e
                     where e.cuenta_id = c.id)                   as empresas,
                   (select count(*) from usuario u
                     where u.cuenta_id = c.id and u.activo)      as usuarios,
                   c.creado_en                                   as creado_en
            from cuenta c
            join plan p on p.codigo = c.plan
            """;

    /**
     * Todas las cuentas, ordenadas por nombre.
     *
     * <p>Sin paginar, y es una decisión con fecha de caducidad: con decenas de
     * clientes una lista completa es lo más cómodo de usar y de comprobar. Pasa a
     * ser un problema cuando sean cientos, y entonces se pagina — pero paginar
     * ahora sería construir controles que nadie necesita todavía.
     */
    public List<CuentaResumen> listar() {
        return jdbc.sql(CUENTAS + " order by c.nombre")
                .query(CuentaResumen.class)
                .list();
    }
}
