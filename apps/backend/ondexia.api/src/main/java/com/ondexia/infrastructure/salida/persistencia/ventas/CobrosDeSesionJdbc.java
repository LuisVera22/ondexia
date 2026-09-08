package com.ondexia.infrastructure.salida.persistencia.ventas;

import com.ondexia.domain.ventas.CobrosDeSesion;
import com.ondexia.domain.ventas.FormaDePago;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo cobrado en una sesión de caja, por forma de pago: la suma de los pagos de
 * sus documentos no anulados. Es lo que el arqueo compara con lo declarado.
 * Sustituye a la implementación vacía de la iteración 2; {@code SesionesDeCaja}
 * no cambió.
 */
/**
 * Lo que pasó por la caja durante una sesión, por forma de pago.
 *
 * <h2>Cuenta lo que se movió, no lo que sigue vigente</h2>
 *
 * <p>Dos decisiones que parecen detalles de una consulta y son la diferencia
 * entre un arqueo que cuadra y uno que no:
 *
 * <ul>
 *   <li><strong>Un documento anulado después sigue contando aquí.</strong> El
 *       dinero entró en esta sesión y estaba en el cajón al cerrarla. Excluirlo
 *       cambiaría, semanas más tarde, el calculado de una sesión ya cerrada, y
 *       una diferencia que alguien justificó en su día pasaría a no cuadrar sin
 *       que nadie tocara nada. Esta línea decía {@code estado <> 'ANULADO'}
 *       cuando todavía no se podía anular; con la iteración 6 dejó de ser
 *       inofensiva.</li>
 *   <li><strong>La devolución de una nota de crédito resta</strong>, y resta en
 *       la sesión en la que se devolvió el dinero, que puede no ser la de la
 *       venta. Los pagos se guardan siempre positivos —lo exige
 *       {@code pago_monto_positivo}— y el signo lo pone el tipo de
 *       documento.</li>
 * </ul>
 */
@Repository
@Transactional(readOnly = true)
public class CobrosDeSesionJdbc implements CobrosDeSesion {

    private final JdbcClient jdbc;

    public CobrosDeSesionJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<FormaDePago, BigDecimal> cobradoEn(UUID sesionId) {
        var cobrado = new EnumMap<FormaDePago, BigDecimal>(FormaDePago.class);
        jdbc.sql("""
                select p.forma,
                       sum(case when d.tipo_documento = '07' then -p.monto else p.monto end) as monto
                  from pago p
                  join documento_venta d on d.id = p.documento_id
                 where d.sesion_caja_id = ?
                 group by p.forma
                """)
                .param(sesionId)
                .query((fila, n) -> Map.entry(FormaDePago.valueOf(fila.getString("forma")),
                        fila.getBigDecimal("monto")))
                .list()
                .forEach(entrada -> cobrado.put(entrada.getKey(), entrada.getValue()));
        return cobrado;
    }
}
