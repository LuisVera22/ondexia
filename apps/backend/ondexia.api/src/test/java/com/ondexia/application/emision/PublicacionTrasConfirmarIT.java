package com.ondexia.application.emision;

import com.ondexia.domain.comprobante.OrdenDeEmision;
import com.ondexia.domain.comprobante.BusDeEmision;
import com.ondexia.pruebas.PruebaIntegracion;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** F17: commit y rollback reales; la entrega externa se observa con un bus simulado. */
class PublicacionTrasConfirmarIT extends PruebaIntegracion {
    @Autowired private DataSource fuente;
    private Connection conexion;
    private JdbcClient consultas;
    private TransactionTemplate transaccion;
    private final BusDeEmision bus = mock(BusDeEmision.class);
    private final OrdenDeEmision orden = mock(OrdenDeEmision.class);

    @BeforeEach
    void prepararTablaTemporal() throws Exception {
        conexion = fuente.getConnection();
        var exclusiva = new SingleConnectionDataSource(conexion, true);
        consultas = JdbcClient.create(exclusiva);
        transaccion = new TransactionTemplate(new JdbcTransactionManager(exclusiva));
        consultas.sql("CREATE TEMP TABLE prueba_confirmacion_emision "
                + "(id INTEGER UNIQUE DEFERRABLE INITIALLY DEFERRED) ON COMMIT PRESERVE ROWS").update();
    }

    @AfterEach
    void limpiarContextoYTabla() throws Exception {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        try {
            consultas.sql("DROP TABLE prueba_confirmacion_emision").update();
        } finally {
            conexion.close();
        }
    }

    @Test
    void sinTransaccionNiSincronizacionNoPublica() {
        exigirRechazoSinPublicacion();
    }

    @Test
    void soloSincronizacionNoRegistraPublicacion() {
        TransactionSynchronizationManager.initSynchronization();
        exigirRechazoSinPublicacion();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void soloMarcaDeTransaccionNoPublica() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        exigirRechazoSinPublicacion();
    }

    @Test
    void publicaUnaVezDespuesDeConfirmarLaFila() {
        transaccion.executeWithoutResult(estado -> {
            insertarFila();
            EmisionElectronica.publicarTrasConfirmar(bus, orden);
            verifyNoInteractions(bus);
        });
        assertThat(filas()).isEqualTo(1);
        verify(bus, times(1)).publicar(orden);
        verifyNoMoreInteractions(bus);
    }

    @Test
    void excepcionAntesDeConfirmarNoPublicaNiConservaFila() {
        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> {
            insertarFila();
            EmisionElectronica.publicarTrasConfirmar(bus, orden);
            throw new IllegalStateException("Fallo previo a la confirmación");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("Fallo previo a la confirmación");
        assertThat(filas()).isZero();
        verifyNoInteractions(bus);
    }

    @Test
    void rollbackExplicitoNoPublicaNiConservaFila() {
        transaccion.executeWithoutResult(estado -> {
            insertarFila();
            EmisionElectronica.publicarTrasConfirmar(bus, orden);
            estado.setRollbackOnly();
        });
        assertThat(filas()).isZero();
        verifyNoInteractions(bus);
    }

    @Test
    void falloDeConfirmacionNoPublicaNiConservaFilas() {
        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> {
            insertarFila();
            insertarFila();
            EmisionElectronica.publicarTrasConfirmar(bus, orden);
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(filas()).isZero();
        verifyNoInteractions(bus);
    }

    @Test
    void falloDelBusSePropagaSinDeshacerLaFilaConfirmada() {
        doThrow(new IllegalStateException("Bus no disponible")).when(bus).publicar(orden);
        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> {
            insertarFila();
            EmisionElectronica.publicarTrasConfirmar(bus, orden);
        })).isInstanceOf(IllegalStateException.class).hasMessage("Bus no disponible");
        assertThat(filas()).isEqualTo(1);
        verify(bus, times(1)).publicar(orden);
        verifyNoMoreInteractions(bus);
    }

    private void exigirRechazoSinPublicacion() {
        assertThatThrownBy(() -> EmisionElectronica.publicarTrasConfirmar(bus, orden))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La publicación de emisión requiere una transacción real con sincronización activa.");
        verifyNoInteractions(bus);
    }

    private void insertarFila() {
        consultas.sql("INSERT INTO prueba_confirmacion_emision (id) VALUES (1)").update();
    }

    private int filas() {
        return consultas.sql("SELECT COUNT(*) FROM prueba_confirmacion_emision")
                .query(Integer.class).single();
    }
}
