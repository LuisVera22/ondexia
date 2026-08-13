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

        /*
         * Siembra de datos de ejemplo, solo fuera de produccion.
         *
         * Existe porque falta una pieza del sistema: nada rellena
         * usuario.cognito_sub, que es lo que une una cuenta de Cognito con la
         * de la aplicacion. El alta de usuarios es de la Entrega 4 y hasta
         * entonces no hay forma de entrar a un entorno recien creado.
         *
         * Se invoca con:
         *   {"sembrar": {"email": "...", "sub": "<sub de Cognito>"}}
         *
         * Reutiliza V900__datos_de_ejemplo.sql —el mismo juego contra el que
         * corren las 32 pruebas— en vez de un INSERT escrito para la ocasion,
         * que seria otro sitio donde el esquema y los datos pueden divergir.
         */
        @SuppressWarnings("unchecked")
        Map<String, String> semilla = evento == null
                ? null
                : (Map<String, String>) evento.get("sembrar");

        if (semilla != null) {
            return sembrar(url, semilla, contexto);
        }

        var flyway = Flyway.configure()
                .dataSource(url, variable("BD_USUARIO"), variable("BD_CONTRASENA"))
                .locations("classpath:db/migration")
                /*
                 * Fuera de orden permitido, y no es una concesion: es lo que
                 * exige el diseno de los datos de ejemplo.
                 *
                 * V900__datos_de_ejemplo.sql lleva ese numero alto A PROPOSITO,
                 * para dejar libre la numeracion baja a las migraciones de
                 * esquema reales — lo explica su propia cabecera. La
                 * consecuencia es que en cualquier entorno donde se haya
                 * sembrado, la V900 queda aplicada y toda migracion nueva (V3,
                 * V4...) llega con version MENOR que la ultima registrada.
                 *
                 * Con la validacion estricta, Flyway lo rechaza:
                 *
                 *   Detected resolved migration not applied to database: 3
                 *
                 * Y no es un aviso teorico: paso al aplicar la V3 en dev.
                 *
                 * El riesgo habitual del fuera de orden —que dos ramas creen
                 * versiones solapadas y se apliquen en distinto orden en cada
                 * entorno— no aplica aqui: la unica version alta es la semilla,
                 * que solo existe fuera de produccion y no toca el esquema.
                 */
                .outOfOrder(true)
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

    /**
     * Aplica los datos de ejemplo y ata el usuario demo a una cuenta de Cognito.
     *
     * <p>Dos salvaguardas, porque esto crea un usuario administrador con una
     * identidad conocida: se niega en produccion, y el {@code sub} tiene que
     * venir en la peticion — no hay ninguno por omision que pudiera colarse.
     */
    private static String sembrar(String url, Map<String, String> semilla, Context contexto) {
        String entorno = variable("ENTORNO");
        if ("prod".equalsIgnoreCase(entorno)) {
            throw new IllegalStateException(
                    "La siembra de datos de ejemplo no se ejecuta en produccion.");
        }

        String email = semilla.get("email");
        String sub = semilla.get("sub");
        if (email == null || email.isBlank() || sub == null || sub.isBlank()) {
            throw new IllegalArgumentException(
                    "Hacen falta 'email' y 'sub' dentro de 'sembrar'.");
        }

        var resultado = Flyway.configure()
                .dataSource(url, variable("BD_USUARIO"), variable("BD_CONTRASENA"))
                // La carpeta de ejemplo se anade SOLO aqui. En la migracion
                // normal Flyway ni la mira, que es lo que impide que los datos
                // de demostracion lleguen a produccion por descuido.
                .locations("classpath:db/migration", "classpath:db/local")
                // Mismo motivo que en la migracion normal: la V900 de la
                // semilla deja fuera de orden a toda migracion de esquema
                // posterior.
                .outOfOrder(true)
                .cleanDisabled(true)
                .load()
                .migrate();

        /*
         * Se localiza por el ID FIJO del usuario demo, no por su correo.
         *
         * V900 usa identificadores literales a proposito —lo explica en su
         * cabecera— y eso es justo lo que hace falta aqui: el correo del
         * ejemplo es demo@ondexia.com, que no es el buzon de nadie, asi que la
         * primera cosa que hay que cambiar es precisamente el correo. Buscar
         * por el campo que se va a modificar solo funcionaria la primera vez.
         *
         * Con el ID, la operacion es idempotente: se puede repetir para
         * reapuntar el usuario a otra cuenta de Cognito sin volver a sembrar.
         */
        final String USUARIO_DEMO = "00000000-0000-4000-8000-000000000002";

        int vinculados;
        try (var conexion = java.sql.DriverManager.getConnection(
                        url, variable("BD_USUARIO"), variable("BD_CONTRASENA"));
                var sentencia = conexion.prepareStatement(
                        "update usuario set cognito_sub = ?, email = ?, actualizado_en = now()"
                                + " where id = ?::uuid")) {
            sentencia.setString(1, sub);
            sentencia.setString(2, email);
            sentencia.setString(3, USUARIO_DEMO);
            vinculados = sentencia.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("No se pudo vincular el usuario con Cognito.", e);
        }

        if (vinculados == 0) {
            throw new IllegalStateException(
                    "No existe el usuario de ejemplo " + USUARIO_DEMO + ". "
                            + "Revisa que la siembra haya aplicado V900.");
        }

        String resumen = "Semilla aplicada (%d migraciones). %s vinculado al sub %s."
                .formatted(resultado.migrationsExecuted, email, sub);
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
