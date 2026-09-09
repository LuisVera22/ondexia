package com.ondexia.infrastructure.salida.persistencia.almacen;

import com.ondexia.domain.almacen.Existencia;
import com.ondexia.domain.almacen.ExistenciasRepositorio;
import com.ondexia.domain.almacen.MovimientoStock;
import com.ondexia.domain.comun.ProveedorDeContexto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * El libro y su proyección, con SQL directo y no con entidades.
 *
 * <p>Lo que hace especial a esta tabla es la suma atómica: dos ventas del mismo
 * producto a la vez no pueden leer la misma existencia y escribir cada una la
 * suya. Un {@code INSERT ... ON CONFLICT DO UPDATE SET cantidad = stock.cantidad +
 * EXCLUDED.cantidad} lo resuelve en la base sin bloquear nada a mano; con
 * entidades sería leer, sumar y guardar, que es justo la carrera.
 *
 * <p>Comparte transacción y conexión con JPA: el gestor de transacciones es el
 * de JPA y expone la conexión al {@code JdbcClient}, así que la variable del RLS
 * fijada al abrir la transacción también rige aquí. Se vacía el contexto de
 * persistencia antes de escribir, por si el caso de uso dejó algo pendiente que
 * estas filas referencian.
 */
@Repository
@Transactional(readOnly = true)
public class ExistenciasAdaptador implements ExistenciasRepositorio {

    private final JdbcClient jdbc;
    private final ProveedorDeContexto contexto;

    @PersistenceContext
    private EntityManager gestor;

    public ExistenciasAdaptador(JdbcClient jdbc, ProveedorDeContexto contexto) {
        this.jdbc = jdbc;
        this.contexto = contexto;
    }

    @Override
    public Optional<Existencia> buscar(UUID almacenId, UUID productoId) {
        return jdbc.sql("select almacen_id, producto_id, cantidad from stock "
                        + "where almacen_id = ? and producto_id = ?")
                .params(almacenId, productoId)
                .query((fila, n) -> new Existencia(fila.getObject("almacen_id", UUID.class),
                        fila.getObject("producto_id", UUID.class), fila.getBigDecimal("cantidad")))
                .optional();
    }

    @Override
    public List<Existencia> existenciasDe(UUID productoId) {
        return jdbc.sql("select s.almacen_id, s.producto_id, s.cantidad from stock s "
                        + "join almacen a on a.id = s.almacen_id "
                        + "where s.producto_id = ? order by a.codigo")
                .param(productoId)
                .query((fila, n) -> new Existencia(fila.getObject("almacen_id", UUID.class),
                        fila.getObject("producto_id", UUID.class), fila.getBigDecimal("cantidad")))
                .list();
    }

    @Override
    public List<MovimientoStock> movimientosDe(UUID productoId, int maximo) {
        return jdbc.sql("select id, almacen_id, producto_id, cantidad, tipo, documento_tipo, "
                        + "documento_id, motivo, usuario_id, creado_en from movimiento_stock "
                        + "where producto_id = ? order by creado_en desc limit ?")
                .params(productoId, maximo)
                .query((fila, n) -> new MovimientoStock(
                        fila.getObject("id", UUID.class),
                        fila.getObject("almacen_id", UUID.class),
                        fila.getObject("producto_id", UUID.class),
                        fila.getBigDecimal("cantidad"),
                        MovimientoStock.Tipo.valueOf(fila.getString("tipo")),
                        fila.getString("documento_tipo"),
                        fila.getObject("documento_id", UUID.class),
                        fila.getString("motivo"),
                        fila.getObject("usuario_id", UUID.class),
                        // pgjdbc no convierte timestamptz a Instant directamente.
                        fila.getObject("creado_en", java.time.OffsetDateTime.class).toInstant()))
                .list();
    }

    @Override
    @Transactional
    public Existencia mover(MovimientoStock movimiento) {
        gestor.flush();
        UUID empresaId = contexto.obligatorio().empresaActivaObligatoria();

        jdbc.sql("insert into movimiento_stock (id, empresa_id, almacen_id, producto_id, cantidad, "
                        + "tipo, documento_tipo, documento_id, motivo, usuario_id, creado_en) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(movimiento.id(), empresaId, movimiento.almacenId(), movimiento.productoId(),
                        movimiento.cantidad(), movimiento.tipo().name(), movimiento.documentoTipo(),
                        movimiento.documentoId(), movimiento.motivo(), movimiento.usuarioId(),
                        movimiento.creadoEn() == null
                                ? java.sql.Timestamp.from(Instant.now())
                                : java.sql.Timestamp.from(movimiento.creadoEn()))
                .update();

        BigDecimal cantidad = jdbc.sql("insert into stock (id, empresa_id, almacen_id, producto_id, cantidad) "
                        + "values (?, ?, ?, ?, ?) "
                        + "on conflict (almacen_id, producto_id) do update "
                        + "set cantidad = stock.cantidad + excluded.cantidad, actualizado_en = now() "
                        + "returning cantidad")
                .params(UUID.randomUUID(), empresaId, movimiento.almacenId(), movimiento.productoId(),
                        movimiento.cantidad())
                .query(BigDecimal.class)
                .single();

        return new Existencia(movimiento.almacenId(), movimiento.productoId(), cantidad);
    }
}
