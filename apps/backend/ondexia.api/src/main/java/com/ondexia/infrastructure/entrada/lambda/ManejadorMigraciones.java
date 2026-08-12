package com.ondexia.infrastructure.entrada.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * Ejecuta las migraciones de Flyway, una vez, bajo demanda.
 *
 * <p>Comparte artefacto con {@link ManejadorLambda} —mismo ZIP, otro handler—
 * pero es una función distinta en Terraform, sin SnapStart y con más tiempo de
 * espera.
 *
 * <h2>Por qué las migraciones no corren al arrancar la API</h2>
 *
 * <p>En un servidor de toda la vida, migrar al arrancar es lo natural: hay un
 * proceso, arranca una vez. En Lambda hay N entornos de ejecución que aparecen
 * cuando les toca, así que «al arrancar» significa <em>en cada arranque en
 * frío</em>, y varios a la vez cuando llega una ráfaga de tráfico.
 *
 * <p>Flyway toma un candado en la base, de modo que no se corrompe nada: los
 * demás esperan. Pero esperan dentro de la petición de un usuario, contra un
 * tiempo de espera de 29 segundos en API Gateway. El resultado de desplegar una
 * migración larga sería una tanda de 504 mientras el primero migra.
 *
 * <p>Hay una segunda razón, más incómoda: convierte cada arranque en frío en un
 * momento en el que el esquema <em>podría</em> cambiar. Separarlo hace que
 * migrar sea un acto explícito, con su propio registro y su propio resultado.
 *
 * <h2>Sin Spring, a propósito</h2>
 *
 * <p>Levantar el contexto entero para llamar a {@code migrate()} costaría
 * segundos y arrastraría a Hibernate, que valida el esquema contra las
 * entidades — justo lo que todavía no cuadra cuando hay una migración
 * pendiente. Sería un arranque que falla antes de poder arreglar lo que falla.
 *
 * <p>Se invoca a mano o desde el despliegue:
 *
 * <pre>{@code
 * aws lambda invoke --function-name ondexia-dev-migraciones --payload '{}' salida.json
 * }</pre>
 */
public class ManejadorMigraciones implements RequestHandler<Map<String, Object>, String> {

    @Override
    public String handleRequest(Map<String, Object> evento, Context contexto) {
        String url = "jdbc:postgresql://%s:%s/%s".formatted(
                variable("BD_HOST"), variable("BD_PUERTO"), variable("BD_NOMBRE"));

        var flyway = Flyway.configure()
                .dataSource(url, variable("BD_USUARIO"), variable("BD_CONTRASENA"))
                .locations("classpath:db/migration")
                // Mismo criterio que application.yml: 'clean' no está
                // disponible en ningún entorno. Es un borrado del esquema
                // completo a una llamada de distancia.
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load();

        MigrateResult resultado = flyway.migrate();

        String resumen = "Migraciones aplicadas: %d. Esquema en la version %s."
                .formatted(resultado.migrationsExecuted, resultado.targetSchemaVersion);
        contexto.getLogger().log(resumen);
        return resumen;
    }

    private static String variable(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) {
            // Falla ruidosamente y con el nombre dentro. La alternativa —tomar
            // un valor por omisión— acabaría migrando contra localhost, que en
            // Lambda no existe, con un error de conexión que no dice cuál es la
            // variable que falta.
            throw new IllegalStateException(
                    "Falta la variable de entorno " + nombre + " en la funcion de migraciones.");
        }
        return valor;
    }
}
