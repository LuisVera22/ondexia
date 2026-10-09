package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Dos conexiones reales, rol sujeto a RLS y bloqueos comprobados sin pausas arbitrarias. */
class ConcurrenciaDeTiposIT extends PruebaIntegracion {
    private static final String ALMACEN = "00000000-0000-4000-8000-000000000090";
    @Autowired private DataSource origen;

    private Connection abrir() throws Exception {
        var conexion = origen.getConnection();
        conexion.setAutoCommit(false);
        preparar(conexion);
        return conexion;
    }
    private void preparar(Connection conexion) throws Exception {
        try (var sentencia = conexion.createStatement()) {
            sentencia.execute("select set_config('ondexia.empresa_id', '" + EMPRESA_ADMINISTRADA + "', true)");
            sentencia.execute("set local lock_timeout = '200ms'");
        }
    }
    private String crear() throws Exception {
        String id = UUID.randomUUID().toString();
        try (var conexion = abrir(); var sentencia = conexion.createStatement()) {
            sentencia.execute("insert into producto (id, empresa_id, codigo, nombre, unidad_medida, afectacion_igv, tipo, controla_stock) values ('"
                    + id + "', '" + EMPRESA_ADMINISTRADA + "', 'C-" + id.substring(0, 20) + "', 'Concurrencia', 'NIU', '10', 'BIEN', true)");
            conexion.commit();
        }
        return id;
    }
    private void convertir(Connection conexion, String id) throws Exception {
        try (var sentencia = conexion.createStatement()) {
            sentencia.execute("update producto set tipo='SERVICIO', controla_stock=false, unidad_medida='ZZ' where id='" + id + "'");
        }
    }
    private void stock(Connection conexion, String id) throws Exception {
        try (var sentencia = conexion.createStatement()) {
            sentencia.execute("insert into stock (id, empresa_id, almacen_id, producto_id, cantidad) values ('"
                    + UUID.randomUUID() + "', '" + EMPRESA_ADMINISTRADA + "', '" + ALMACEN + "', '" + id + "', 0)");
        }
    }
    @Test void conversionConfirmadaImpideCrearStockInclusoCero() throws Exception {
        String id = crear();
        try (var primera = abrir(); var segunda = abrir()) {
            convertir(primera, id);
            assertThatThrownBy(() -> stock(segunda, id)).isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("55P03"));
            segunda.rollback(); primera.commit(); preparar(segunda);
            assertThatThrownBy(() -> stock(segunda, id)).isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
            segunda.rollback();
        }
    }
    @Test void stockConfirmadoImpideConvertirAunqueNoTengaMovimientos() throws Exception {
        String id = crear();
        try (var primera = abrir(); var segunda = abrir()) {
            stock(primera, id);
            assertThatThrownBy(() -> convertir(segunda, id)).isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("55P03"));
            segunda.rollback(); primera.commit(); preparar(segunda);
            assertThatThrownBy(() -> convertir(segunda, id)).isInstanceOf(SQLException.class)
                    .hasMessageContaining("bien_con_historia_de_stock");
            segunda.rollback();
        }
    }
}
