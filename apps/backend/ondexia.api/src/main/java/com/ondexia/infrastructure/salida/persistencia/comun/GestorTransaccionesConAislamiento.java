package com.ondexia.infrastructure.salida.persistencia.comun;

import com.ondexia.domain.comun.ContextoOperacion;
import com.ondexia.domain.comun.ProveedorDeContexto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Gestor de transacciones que fija el inquilino de PostgreSQL al abrir cada una.
 *
 * <h2>La trampa que resuelve</h2>
 *
 * Row Level Security lee el inquilino de una variable de sesión. Lambda reutiliza
 * contenedores y HikariCP reutiliza conexiones: si la variable se fija a nivel de
 * sesión y no se restablece, la petición del cliente B puede ejecutarse sobre una
 * conexión que quedó apuntando al cliente A. No produce ningún error — produce
 * datos ajenos, correctos en forma, en la respuesta equivocada.
 *
 * <p>El tercer argumento de {@code set_config} en {@code true} significa «solo
 * para esta transacción», y PostgreSQL lo revierte al confirmar o deshacer. La
 * limpieza no la hace nuestro código, así que no se puede olvidar.
 *
 * <p>Se sobreescribe {@code doBegin} porque es el único punto que corre
 * <em>después</em> de que exista la conexión y <em>antes</em> de la primera
 * consulta.
 *
 * <h2>Sin contexto no se ve nada</h2>
 *
 * Se fija la cadena vacía; las políticas usan
 * {@code nullif(current_setting(...), '')::uuid}, que produce NULL, y una
 * comparación con NULL no es cierta. <strong>Falla cerrado.</strong> Un despiste
 * deja al sistema sin datos —ruidoso y se arregla— y nunca con datos de más.
 *
 * <p>Consecuencia práctica: una consulta fuera de transacción no ve ninguna
 * fila. Por eso los adaptadores llevan {@code @Transactional} a la vista.
 */
public class GestorTransaccionesConAislamiento extends JpaTransactionManager {

    /** Debe coincidir letra por letra con el que usan las políticas de la V1. */
    public static final String VARIABLE_EMPRESA = "ondexia.empresa_id";

    private static final String SENTENCIA = "select set_config(?, ?, true)";

    private final transient ProveedorDeContexto contexto;

    public GestorTransaccionesConAislamiento(EntityManagerFactory fabrica,
            ProveedorDeContexto contexto) {
        super(fabrica);
        this.contexto = contexto;
    }

    @Override
    protected void doBegin(Object transaccion, TransactionDefinition definicion) {
        super.doBegin(transaccion, definicion);
        aplicarAislamiento();
    }

    private void aplicarAislamiento() {
        EntityManagerFactory fabrica = getEntityManagerFactory();
        if (fabrica == null) {
            return;
        }
        if (!(TransactionSynchronizationManager.getResource(fabrica)
                instanceof EntityManagerHolder portador)) {
            return;
        }
        EntityManager gestor = portador.getEntityManager();
        if (gestor == null) {
            return;
        }

        String empresa = contexto.actual()
                .map(ContextoOperacion::empresaId)
                .map(UUID::toString)
                .orElse("");

        gestor.unwrap(Session.class).doWork(conexion -> {
            try (PreparedStatement sentencia = conexion.prepareStatement(SENTENCIA)) {
                sentencia.setString(1, VARIABLE_EMPRESA);
                sentencia.setString(2, empresa);
                sentencia.execute();
            } catch (SQLException e) {
                // Si el aislamiento no se pudo fijar, la transacción no debe
                // continuar. Las políticas fallan cerradas, pero un fallo aquí
                // apunta a algo peor —conexión rota, permisos cambiados— que hay
                // que ver, no absorber.
                throw new IllegalStateException(
                        "No se pudo fijar el aislamiento multiempresa de la transacción", e);
            }
        });
    }
}
