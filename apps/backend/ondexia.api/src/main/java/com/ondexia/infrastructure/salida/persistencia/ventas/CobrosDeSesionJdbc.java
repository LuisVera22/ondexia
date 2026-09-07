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
                select p.forma, sum(p.monto) as monto
                  from pago p
                  join documento_venta d on d.id = p.documento_id
                 where d.sesion_caja_id = ? and d.estado <> 'ANULADO'
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
