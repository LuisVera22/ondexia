package com.ondexia.infrastructure.seguridad;

import com.ondexia.domain.comun.ContextoOperacion;
import com.ondexia.domain.comun.OperarComoEmpresa;
import com.ondexia.infrastructure.salida.persistencia.comun.GestorTransaccionesConAislamiento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Cambia, durante una acción, las dos cosas que dicen «quién opera»: el contexto
 * que ven los casos de uso y la variable de transacción que leen las políticas
 * de Row Level Security. Restaura ambas al salir, también si la acción lanza.
 *
 * <p>Exige una transacción en curso ({@code MANDATORY}): el {@code set_config}
 * es local a la transacción, y fuera de una no habría nada que restaurar ni
 * nada que proteger. Vive en este paquete porque {@code ContextoActual} solo se
 * toca desde aquí, a propósito.
 *
 * <p>Lo ejercitan {@code RegistroIT}, {@code RegistroDeEmpresaIT} y
 * {@code ConfiguracionEmpresaIT}: el almacén y la caja que nacen con la empresa
 * solo se ven desde la API si se escribieron bajo la empresa correcta.
 */
@Component
public class OperadorComoEmpresa implements OperarComoEmpresa {

    private static final String SENTENCIA = "select set_config(?, ?, true)";

    @PersistenceContext
    private EntityManager gestor;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void ejecutar(ContextoOperacion contexto, Runnable accion) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("OperarComoEmpresa exige una transacción en curso.");
        }
        Optional<ContextoOperacion> anterior = ContextoActual.obtener();
        UUID empresaAnterior = anterior.map(ContextoOperacion::empresaId).orElse(null);

        // Lo pendiente en el contexto de persistencia es de la empresa ANTERIOR
        // —la anotacion de bitacora del alta, por ejemplo— y tiene que llegar a
        // la base mientras la base todavia la ve. Si se escribiera despues del
        // cambio, arrastrado por una consulta de la accion, el RLS lo rechazaria
        // como fila de otra empresa.
        gestor.flush();

        ContextoActual.establecer(contexto);
        fijarEmpresa(contexto.empresaId());
        try {
            accion.run();
            // Y lo que la accion dejo pendiente tiene que llegar MIENTRAS la base
            // ve a esta empresa: al confirmar, ya con la variable restaurada, el
            // RLS lo rechazaria.
            gestor.flush();
        } catch (RuntimeException | Error fallo) {
            // La transaccion esta condenada y PostgreSQL rechaza cualquier orden
            // hasta que se revierta: intentar restaurar la variable solo
            // sustituiria la causa real por «transaction is aborted». La variable
            // muere con la transaccion; el contexto si se restaura.
            restaurarContexto(anterior);
            throw fallo;
        }
        // Primero la variable de la base, despues el contexto: si lo primero
        // falla, el contexto sigue diciendo la verdad sobre lo que la base ve.
        fijarEmpresa(empresaAnterior);
        restaurarContexto(anterior);
    }

    private static void restaurarContexto(Optional<ContextoOperacion> anterior) {
        if (anterior.isPresent()) {
            ContextoActual.establecer(anterior.get());
        } else {
            ContextoActual.limpiar();
        }
    }

    private void fijarEmpresa(UUID empresaId) {
        gestor.unwrap(Session.class).doWork(conexion -> {
            try (PreparedStatement sentencia = conexion.prepareStatement(SENTENCIA)) {
                sentencia.setString(1, GestorTransaccionesConAislamiento.VARIABLE_EMPRESA);
                sentencia.setString(2, empresaId == null ? "" : empresaId.toString());
                sentencia.execute();
            } catch (SQLException e) {
                throw new IllegalStateException(
                        "No se pudo cambiar la empresa del aislamiento de la transacción", e);
            }
        });
    }
}
