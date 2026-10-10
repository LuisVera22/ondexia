package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

/** PostgreSQL real y rol restringido; las consultas no filtran por empresa. */
class AislamientoSqlIT extends PruebaIntegracion {

    private static final UUID EMPRESA_A = UUID.fromString(EMPRESA_ADMINISTRADA);
    private static final UUID EMPRESA_B = UUID.fromString(EMPRESA_COMO_VENDEDOR);
    private static final String NOMBRE = "Almacén de prueba de aislamiento";

    @Autowired
    private DataSource fuente;

    @BeforeEach
    void permitirAsumirRolRestringidoEnElServidorTemporal() throws SQLException {
        try (var conexion = fuente.getConnection(); var sentencia = conexion.createStatement()) {
            sentencia.execute("grant ondexia_app to ondexia with set true");
        }
    }

    @AfterEach
    void retirarPermisoDeCambioDeRolDelEnsayo() throws SQLException {
        try (var conexion = fuente.getConnection(); var sentencia = conexion.createStatement()) {
            sentencia.execute("grant ondexia_app to ondexia with set false");
        }
    }

    @Test
    void rolDeAplicacionNoEsSuperusuarioNiOmiteRlsNiPoseeLaTabla() throws Exception {
        try (var conexion = fuente.getConnection()) {
            try {
                iniciar(conexion, EMPRESA_A);
                try (var sentencia = conexion.createStatement(); var filas = sentencia.executeQuery("""
                        select current_user, r.rolsuper, r.rolbypassrls,
                               pg_get_userbyid(c.relowner) = current_user as es_propietario,
                               c.relrowsecurity, c.relforcerowsecurity
                          from pg_roles r, pg_class c
                         where r.rolname = current_user and c.oid = 'public.almacen'::regclass
                        """)) {
                    assertThat(filas.next()).isTrue();
                    assertThat(filas.getString(1)).isEqualTo("ondexia_app");
                    assertThat(filas.getBoolean(2)).isFalse();
                    assertThat(filas.getBoolean(3)).isFalse();
                    assertThat(filas.getBoolean(4)).isFalse();
                    assertThat(filas.getBoolean(5)).isTrue();
                    assertThat(filas.getBoolean(6)).isTrue();
                }
            } finally {
                restaurarYComprobar(conexion);
            }
        }
    }

    @ParameterizedTest(name = "Empresa ajena: {0}")
    @ValueSource(strings = {"leer", "actualizar", "borrar"})
    void empresaAjenaNoAlcanzaLaFila(String operacion) throws Exception {
        try (var conexion = fuente.getConnection()) {
            try {
                var id = crearFilaPropia(conexion);
                iniciar(conexion, EMPRESA_B);
                int alcanzadas;
                if ("leer".equals(operacion)) {
                    alcanzadas = contar(conexion, id);
                } else {
                    String consulta = "actualizar".equals(operacion)
                            ? "update almacen set nombre = 'Nombre ajeno' where id = ?"
                            : "delete from almacen where id = ?";
                    try (var sentencia = conexion.prepareStatement(consulta)) {
                        sentencia.setObject(1, id);
                        alcanzadas = sentencia.executeUpdate();
                    }
                }
                assertThat(alcanzadas).isZero();
                conexion.rollback();
                iniciar(conexion, EMPRESA_A);
                try (var consultar = conexion.prepareStatement("select nombre from almacen where id = ?")) {
                    consultar.setObject(1, id);
                    try (var filas = consultar.executeQuery()) {
                        assertThat(filas.next()).isTrue();
                        assertThat(filas.getString(1)).isEqualTo(NOMBRE);
                    }
                }
            } finally {
                restaurarYComprobar(conexion);
            }
        }
    }

    @Test
    void insercionDeOtraEmpresaEsRechazadaPorRls() throws Exception {
        try (var conexion = fuente.getConnection()) {
            try {
                iniciar(conexion, EMPRESA_A);
                assertThatThrownBy(() -> insertar(conexion, UUID.randomUUID(), EMPRESA_B))
                        .isInstanceOf(SQLException.class)
                        .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("42501"));
            } finally {
                restaurarYComprobar(conexion);
            }
        }
    }

    @Test
    void mismaConexionSinEmpresaNoHeredaContextoTrasConfirmarNiRevertir() throws Exception {
        try (var conexion = fuente.getConnection()) {
            try {
                var id = crearFilaPropia(conexion);
                iniciar(conexion, null);
                assertThat(contar(conexion, id)).isZero();
                conexion.rollback();
                iniciar(conexion, EMPRESA_A);
                assertThat(contar(conexion, id)).isEqualTo(1);
                conexion.rollback();
                iniciar(conexion, null);
                assertThat(contar(conexion, id)).isZero();
            } finally {
                restaurarYComprobar(conexion);
            }
        }
    }

    private static void restaurarYComprobar(Connection conexion) throws SQLException {
        conexion.rollback();
        try (var sentencia = conexion.createStatement(); var filas = sentencia.executeQuery("""
                select pg_get_expr(polqual, polrelid), pg_get_expr(polwithcheck, polrelid)
                  from pg_policy
                 where polrelid = 'public.almacen'::regclass and polname = 'aislamiento_empresa'
                """)) {
            assertThat(filas.next()).isTrue();
            assertThat(filas.getString(1)).contains("empresa_actual()");
            assertThat(filas.getString(2)).contains("empresa_actual()");
        } finally {
            conexion.rollback();
        }
    }

    private static UUID crearFilaPropia(Connection conexion) throws SQLException {
        conexion.setAutoCommit(false);
        fijarRolYEmpresa(conexion, EMPRESA_A);
        var id = UUID.randomUUID();
        insertar(conexion, id, EMPRESA_A);
        conexion.commit();
        return id;
    }

    private static void iniciar(Connection conexion, UUID empresa) throws SQLException {
        conexion.setAutoCommit(false);
        if (Boolean.getBoolean("ondexia.pruebas.mutarAislamiento")) {
            // Solo en la transacción de ensayo; todos sus caminos hacen rollback.
            // La inserción de la fila de referencia ocurre antes, sin esta mutación.
            try (var sentencia = conexion.createStatement()) {
                sentencia.execute("alter policy aislamiento_empresa on almacen using (true) with check (true)");
            }
        }
        fijarRolYEmpresa(conexion, empresa);
    }

    private static void fijarRolYEmpresa(Connection conexion, UUID empresa) throws SQLException {
        try (var sentencia = conexion.createStatement()) {
            sentencia.execute("set local role ondexia_app");
        }
        if (empresa != null) {
            try (var sentencia = conexion.prepareStatement("select set_config('ondexia.empresa_id', ?, true)")) {
                sentencia.setString(1, empresa.toString());
                sentencia.execute();
            }
        }
    }

    private static void insertar(Connection conexion, UUID id, UUID empresa) throws SQLException {
        try (var sentencia = conexion.prepareStatement(
                "insert into almacen (id, empresa_id, codigo, nombre) values (?, ?, ?, ?)")) {
            sentencia.setObject(1, id);
            sentencia.setObject(2, empresa);
            sentencia.setString(3, id.toString().substring(0, 20));
            sentencia.setString(4, NOMBRE);
            assertThat(sentencia.executeUpdate()).isEqualTo(1);
        }
    }

    private static int contar(Connection conexion, UUID id) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement("select count(*) from almacen where id = ?")) {
            sentencia.setObject(1, id);
            try (var filas = sentencia.executeQuery()) {
                assertThat(filas.next()).isTrue();
                return filas.getInt(1);
            }
        }
    }
}
