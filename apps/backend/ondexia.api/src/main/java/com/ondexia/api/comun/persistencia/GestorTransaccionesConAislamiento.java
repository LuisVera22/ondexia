package com.ondexia.api.comun.persistencia;

import com.ondexia.api.comun.seguridad.ContextoActual;
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
 * Gestor de transacciones que fija el inquilino de PostgreSQL al abrir cada
 * transaccion.
 *
 * <h2>Que problema resuelve</h2>
 *
 * Row Level Security necesita saber sobre que empresa opera la sesion, y lo lee
 * de una variable de configuracion. Esa variable hay que ponerla, y el momento
 * en que se pone determina si RLS protege algo o no protege nada.
 *
 * <p><strong>La trampa.</strong> Lambda reutiliza contenedores, y HikariCP
 * reutiliza conexiones. Si la variable se fija a nivel de sesion y no se
 * restablece, la peticion del cliente B puede ejecutarse sobre una conexion que
 * quedo apuntando al cliente A. Es exactamente la fuga que RLS venia a evitar,
 * y no produce ningun error: produce datos ajenos, correctos en forma, en la
 * respuesta equivocada.
 *
 * <h2>Como se resuelve</h2>
 *
 * Con el tercer argumento de {@code set_config} en {@code true}, que significa
 * «solo para esta transaccion». PostgreSQL lo revierte solo al confirmar o
 * deshacer. No hay ninguna limpieza que se pueda olvidar, porque no la hace
 * nuestro codigo: la hace el motor.
 *
 * <p>Se sobreescribe {@code doBegin} porque es el unico punto que corre
 * <em>despues</em> de que exista la conexion y <em>antes</em> de la primera
 * consulta. Un aspecto alrededor de {@code @Transactional} no serviria: no hay
 * garantia de que se ejecute dentro de la transaccion en lugar de envolverla.
 *
 * <h2>Que pasa si no hay contexto</h2>
 *
 * Se fija la cadena vacia. Las politicas usan
 * {@code nullif(current_setting(...), '')::uuid}, que produce {@code NULL}, y
 * una comparacion con {@code NULL} no es cierta: no se ve ninguna fila.
 * <strong>Falla cerrado.</strong> Un despiste de configuracion deja al sistema
 * sin datos, que es ruidoso y se arregla; nunca con datos de mas, que es
 * silencioso y no se detecta.
 *
 * <h2>La consecuencia que hay que tener presente al escribir consultas</h2>
 *
 * Si no hay transaccion, este codigo no se ejecuta, y sin el la variable no
 * esta puesta: la consulta no ve <strong>ninguna</strong> fila. Sin error.
 *
 * <p>Y hay un caso concreto donde eso ocurre sin que nadie lo haya decidido:
 * <strong>los metodos de consulta derivados de Spring Data no son
 * transaccionales.</strong> Solo lo son los heredados de
 * {@code SimpleJpaRepository} —{@code findAll}, {@code findById},
 * {@code count}—, que traen su propio {@code @Transactional}. Un
 * {@code findByEmpresaIdAndCodigo} declarado en una interfaz de repositorio y
 * llamado directamente se ejecuta fuera de toda transaccion, y devuelve vacio.
 *
 * <p>En la ruta normal no pasa: los servicios llevan {@code @Transactional} y
 * el repositorio se llama desde dentro. Pero conviene saberlo, porque el
 * sintoma —«la consulta no encuentra la fila que acabo de guardar»— apunta a
 * cualquier sitio menos a su causa. Se descubrio asi, escribiendo las pruebas
 * de la bitacora.
 *
 * <p>Regla: toda consulta declarada sobre una tabla con RLS lleva
 * {@code @Transactional(readOnly = true)}.
 *
 * <h2>Que tablas cubre hoy</h2>
 *
 * Las que tienen {@code empresa_id} y no participan en resolver el propio
 * contexto. Quedan fuera {@code empresa}, {@code sucursal} y
 * {@code usuario_empresa}: son las que hay que leer <em>para saber</em> cual es
 * la empresa activa, asi que someterlas a una politica que depende de esa misma
 * respuesta las dejaria vacias siempre. Su control compensatorio es que solo se
 * consultan filtrando por el {@code usuario_id} que sale del token —nunca por
 * un valor que mande el cliente— y esa consulta vive en un solo sitio,
 * {@code ResolutorContexto}.
 *
 * <p>Toda tabla transaccional nueva se anade con {@code activar_rls_empresa} de
 * la migracion V1. Es una linea, y la V1 explica por que no es opcional.
 */
public class GestorTransaccionesConAislamiento extends JpaTransactionManager {

    /**
     * Nombre de la variable de sesion. Debe coincidir letra por letra con el que
     * usan las politicas de la migracion.
     */
    public static final String VARIABLE_EMPRESA = "ondexia.empresa_id";

    private static final String SENTENCIA = "select set_config(?, ?, true)";

    public GestorTransaccionesConAislamiento(EntityManagerFactory fabrica) {
        super(fabrica);
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

        Object recurso = TransactionSynchronizationManager.getResource(fabrica);
        if (!(recurso instanceof EntityManagerHolder portador)) {
            return;
        }

        EntityManager gestor = portador.getEntityManager();
        if (gestor == null) {
            return;
        }

        String empresa = ContextoActual.obtener()
                .map(contexto -> contexto.empresaId() == null ? null : contexto.empresaId())
                .map(UUID::toString)
                .orElse("");

        gestor.unwrap(Session.class).doWork(conexion -> {
            try (PreparedStatement sentencia = conexion.prepareStatement(SENTENCIA)) {
                sentencia.setString(1, VARIABLE_EMPRESA);
                sentencia.setString(2, empresa == null ? "" : empresa);
                sentencia.execute();
            } catch (SQLException e) {
                // Si el aislamiento no se pudo fijar, la transaccion no debe
                // continuar. Seguir significaria consultar sin la variable
                // puesta, y aunque las politicas fallan cerradas, un fallo aqui
                // apunta a algo peor —conexion rota, permisos cambiados— que hay
                // que ver, no absorber.
                throw new IllegalStateException(
                        "No se pudo fijar el aislamiento multiempresa de la transaccion", e);
            }
        });
    }
}
