package com.ondexia.infrastructure.salida.persistencia.ventas;

import com.ondexia.domain.ventas.VentasDelDia;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo vendido en un día: una sola consulta, agregada en la base.
 *
 * <h2>Por qué {@code fecha_emision} y no {@code emitido_en}</h2>
 *
 * <p>{@code emitido_en} es un instante y habría que convertirlo a la zona de
 * Lima para saber a qué día pertenece; {@code fecha_emision} ya es la fecha
 * que va impresa en el comprobante y la que SUNAT lee. Son la misma venta, pero
 * solo una de las dos es la que el cajero reconoce cuando compara con su cajón.
 *
 * <h2>La nota de crédito resta</h2>
 *
 * <p>Mismo criterio que {@link CobrosDeSesionJdbc}: los importes se guardan
 * positivos y el signo lo pone el tipo de documento. Una devolución de 50 soles
 * en el mismo día deja las ventas del día 50 soles más abajo, que es lo que
 * pasó de verdad.
 *
 * <p>Los anulados no cuentan ni en el importe ni en el número: a diferencia del
 * arqueo —que retrata un cajón en un momento— esta cifra dice qué queda vendido
 * hoy, y lo anulado no queda.
 */
@Repository
@Transactional(readOnly = true)
public class VentasDelDiaJdbc implements VentasDelDia {

    private final JdbcClient jdbc;

    public VentasDelDiaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Totales de(LocalDate fecha) {
        return jdbc.sql("""
                select count(*) as documentos,
                       coalesce(sum(case when tipo_documento = '07' then -total else total end), 0)
                           as importe
                  from documento_venta
                 where fecha_emision = ?
                   and estado <> 'ANULADO'
                """)
                .param(fecha)
                .query((fila, n) -> new Totales(fila.getInt("documentos"),
                        fila.getBigDecimal("importe")))
                .optional()
                .orElseGet(Totales::vacio);
    }
}
