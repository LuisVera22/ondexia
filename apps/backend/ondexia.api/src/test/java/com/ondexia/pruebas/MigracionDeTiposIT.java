package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Recrea la forma anterior del catálogo y ejecuta el SQL real de V26; todo se revierte al terminar. */
class MigracionDeTiposIT extends PruebaIntegracion {
    @Autowired private DataSource origen;

    @Test void rellenoDesdeControlDeStockYRestauracionDelAislamiento() throws Exception {
        try (var conexion = origen.getConnection(); var sentencia = conexion.createStatement()) {
            conexion.setAutoCommit(false);
            try {
                // Solo dentro de esta transacción de prueba: restaurar la forma V25 del catálogo.
                sentencia.execute("drop function impedir_servicio_con_stock() cascade");
                sentencia.execute("drop function exigir_bien_para_stock() cascade");
                sentencia.execute("alter table producto drop column tipo cascade");
                for (int empresa : new int[]{10, 11}) {
                    String id = "00000000-0000-4000-8000-0000000000" + empresa;
                    sentencia.execute("select set_config('ondexia.empresa_id','" + id + "',true)");
                    for (boolean controla : new boolean[]{true, false}) {
                        sentencia.execute("insert into producto (id,empresa_id,codigo,nombre,unidad_medida,afectacion_igv,precio_lista,controla_stock) values ('"
                                + UUID.randomUUID() + "','" + id + "','MIG-" + (controla ? "B" : "S")
                                + "','Anterior','NIU','10',123456789012.123456," + controla + ")");
                    }
                }
                sentencia.execute("select set_config('ondexia.empresa_id','',true)");
                try (var recurso = getClass().getResourceAsStream("/db/migration/V26__tipo_explicito_de_producto.sql")) {
                    assertThat(recurso).isNotNull();
                    sentencia.execute(new String(recurso.readAllBytes(), StandardCharsets.UTF_8));
                }
                try (var resultado = sentencia.executeQuery("select count(*) from producto")) {
                    resultado.next(); assertThat(resultado.getInt(1)).isZero();
                }
                for (int empresa : new int[]{10, 11}) {
                    sentencia.execute("select set_config('ondexia.empresa_id','00000000-0000-4000-8000-0000000000" + empresa + "',true)");
                    try (var resultado = sentencia.executeQuery("select codigo,tipo,precio_lista,unidad_medida from producto where codigo like 'MIG-%' order by codigo")) {
                        assertThat(resultado.next()).isTrue(); assertThat(resultado.getString("codigo")).isEqualTo("MIG-B");
                        assertThat(resultado.getString("tipo")).isEqualTo("BIEN");
                        assertThat(resultado.getBigDecimal("precio_lista")).isEqualByComparingTo("123456789012.123456");
                        assertThat(resultado.next()).isTrue(); assertThat(resultado.getString("codigo")).isEqualTo("MIG-S");
                        assertThat(resultado.getString("tipo")).isEqualTo("SERVICIO");
                        assertThat(resultado.getString("unidad_medida")).isEqualTo("NIU");
                        assertThat(resultado.next()).isFalse();
                    }
                }
                try (var resultado = sentencia.executeQuery("select column_default,is_nullable from information_schema.columns where table_name='producto' and column_name='tipo'")) {
                    assertThat(resultado.next()).isTrue(); assertThat(resultado.getString(1)).isNull();
                    assertThat(resultado.getString(2)).isEqualTo("NO");
                }
                try (var resultado = sentencia.executeQuery("select relrowsecurity,relforcerowsecurity from pg_class where relname='producto'")) {
                    resultado.next(); assertThat(resultado.getBoolean(1)).isTrue(); assertThat(resultado.getBoolean(2)).isTrue();
                }
            } finally {
                conexion.rollback();
            }
        }
    }
}
