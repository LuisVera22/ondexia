package com.ondexia.infrastructure.salida.persistencia.identidad;

import com.ondexia.domain.identidad.LimitesDeCuenta;
import com.ondexia.domain.identidad.LimitesDeCuentaRepositorio;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * El tope de empresas de una cuenta, en una consulta.
 *
 * <h2>Por qué SQL y no JPA</h2>
 *
 * <p>Porque no hay agregado que cargar. Hace falta un {@code coalesce} entre dos
 * tablas y un {@code count} sobre una tercera, y con entidades serían tres
 * viajes y una entidad {@code Plan} que no existe en el dominio y que nadie más
 * necesitaría — creada solo para poder leer un número.
 *
 * <p>Un viaje también importa por dónde corre esto: en Lambda, cada consulta es
 * latencia sobre una conexión que no está en la misma máquina.
 *
 * <h2>El coalesce es la regla, no un detalle</h2>
 *
 * <p>{@code cuenta.limite_empresas} es lo pactado con este cliente y
 * {@code plan.max_empresas} lo que da su plan (V9). Manda el primero si existe.
 * Olvidarlo dejaría bloqueado justo al cliente que pagó una empresa adicional —
 * el que menos conviene bloquear.
 *
 * <p>{@code NULL} en las dos significa <strong>sin límite</strong>, que es el
 * plan a demanda del doc 04 §2.2. Se propaga como {@code null} hasta
 * {@link LimitesDeCuenta}, que es quien sabe qué hacer con él; convertirlo aquí
 * a cero o a {@code MAX_VALUE} escondería la distinción en la capa que menos
 * pinta tiene de decidirla.
 */
@Repository
public class LimitesDeCuentaJdbc implements LimitesDeCuentaRepositorio {

    private static final String CONSULTA = """
            select coalesce(c.limite_empresas, p.max_empresas)              as tope,
                   (select count(*) from empresa e where e.cuenta_id = c.id) as usadas
              from cuenta c
              join plan p on p.codigo = c.plan
             where c.id = :cuenta
            """;

    private final JdbcClient jdbc;

    public LimitesDeCuentaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public LimitesDeCuenta de(UUID cuentaId) {
        Map<String, Object> fila = jdbc.sql(CONSULTA)
                .param("cuenta", cuentaId)
                .query()
                .singleRow();

        /*
         * Se cuentan TODAS las empresas de la cuenta, activas o no.
         *
         * Contar solo las activas parece mas justo y es un agujero: desactivar
         * una empresa no la borra —sus comprobantes se conservan cinco anos— asi
         * que un cliente del plan de dos podria tener veinte desactivadas y
         * seguir dando de alta. El limite es de RUC registrados, no de RUC en uso.
         */
        Number tope = (Number) fila.get("tope");
        Number usadas = (Number) fila.get("usadas");

        return new LimitesDeCuenta(
                tope == null ? null : tope.intValue(),
                usadas == null ? 0 : usadas.intValue());
    }
}
