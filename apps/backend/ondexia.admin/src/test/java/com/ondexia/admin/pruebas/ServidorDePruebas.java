package com.ondexia.admin.pruebas;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * De dónde sale el PostgreSQL contra el que corren las pruebas de integración.
 *
 * <h2>Testcontainers por omisión, un servidor externo si se pide</h2>
 *
 * <p>Con Docker disponible no hay nada que configurar: se arranca un contenedor
 * y se tira al terminar, como siempre. Pero hay entornos sin Docker —agentes de
 * CI remotos, contenedores sin acceso al socket, algunas máquinas de desarrollo—
 * y ahí la suite entera moría antes de la primera aserción con «Could not find a
 * valid Docker environment». Que las 171 pruebas de integración no se puedan
 * correr es peor que cualquier fallo que pudieran encontrar.
 *
 * <p>Tres variables de entorno apuntan a un servidor ya levantado, con un
 * usuario que pueda crear roles y bases:
 *
 * <pre>
 * ONDEXIA_PRUEBAS_BD_URL=jdbc:postgresql://localhost:5432/postgres
 * ONDEXIA_PRUEBAS_BD_USUARIO=postgres
 * ONDEXIA_PRUEBAS_BD_CONTRASENA=postgres
 * </pre>
 *
 * <p>Las pruebas crean y destruyen su propia base en ese servidor en cada
 * ejecución, así que da igual qué quedó de la vez anterior. Lo que no cambia en
 * ningún caso es lo que se prueba: el rol de aplicación sigue siendo uno sin
 * SUPERUSER ni BYPASSRLS, porque de otro modo el aislamiento multiempresa no se
 * estaría probando (ver {@link PruebaIntegracion}).
 */
record ServidorDePruebas(String urlBootstrap, String usuario, String contrasena, String anfitrion,
        int puerto) {

    private static final String VARIABLE_URL = "ONDEXIA_PRUEBAS_BD_URL";

    static ServidorDePruebas arrancar() {
        String externa = System.getenv(VARIABLE_URL);
        if (externa != null && !externa.isBlank()) {
            return desdeVariables(externa);
        }
        // Debe coincidir con compose.yaml y con la version de RDS en ondexia.infra.
        //
        // La clase de Testcontainers 2.0 ya no es generica: el <?> con
        // autorreferencia que llevaba la 1.x desaparecio al reorganizar los
        // modulos. Los ejemplos que circulan siguen escribiendo
        // PostgreSQLContainer<?>.
        //
        // Las credenciales del contenedor son las del SUPERUSUARIO BOOTSTRAP, y
        // el panel las usa para migrar, como hace el usuario maestro en RDS.
        PostgreSQLContainer contenedor = new PostgreSQLContainer(
                DockerImageName.parse("postgres:17-alpine"))
                .withDatabaseName("postgres")
                .withUsername("postgres")
                .withPassword("postgres");
        // No se para: el proceso de Maven termina y Ryuk lo retira.
        contenedor.start();
        return new ServidorDePruebas(contenedor.getJdbcUrl(), contenedor.getUsername(),
                contenedor.getPassword(), contenedor.getHost(), contenedor.getFirstMappedPort());
    }

    private static ServidorDePruebas desdeVariables(String url) {
        // jdbc:postgresql://anfitrion:puerto/base — se necesitan anfitrion y
        // puerto sueltos para componer la URL de la base de la aplicacion.
        var sinEsquema = url.substring("jdbc:postgresql://".length());
        var autoridad = sinEsquema.substring(0, sinEsquema.indexOf('/'));
        int dosPuntos = autoridad.lastIndexOf(':');
        String anfitrion = dosPuntos < 0 ? autoridad : autoridad.substring(0, dosPuntos);
        int puerto = dosPuntos < 0 ? 5432 : Integer.parseInt(autoridad.substring(dosPuntos + 1));
        return new ServidorDePruebas(url,
                variable("ONDEXIA_PRUEBAS_BD_USUARIO"),
                variable("ONDEXIA_PRUEBAS_BD_CONTRASENA"),
                anfitrion, puerto);
    }

    /**
     * Deja el servidor como si fuera nuevo, en lo que a estas pruebas toca.
     *
     * <p>Las migraciones crean roles de CLUSTER —{@code ondexia_app},
     * {@code ondexia_panel}, {@code ondexia_migraciones}— que en un contenedor
     * mueren con el. En un servidor externo sobreviven de una ejecucion a la
     * siguiente, y no son inocuos: la V8 salta su segundo bloque solo si
     * {@code ondexia_app} no existe, y si existe intenta un
     * {@code ALTER DEFAULT PRIVILEGES FOR ROLE ondexia_admin} que aqui no tiene
     * rol al que aplicarse. Es decir: la segunda ejecucion fallaria en una
     * migracion que en RDS y en un contenedor limpio pasa.
     *
     * <p>Se borran las bases de los DOS modulos antes que los roles: un rol con
     * privilegios en cualquier base no se puede borrar. Consecuencia que hay que
     * saber: la API y el panel no pueden correr sus pruebas A LA VEZ contra el
     * mismo servidor externo. En secuencia, como las corre Maven, si.
     */
    static void borrarRestosDeEjecucionesAnteriores(java.sql.Statement sentencia)
            throws java.sql.SQLException {
        for (String base : new String[] {"ondexia", "ondexia_panel_pruebas"}) {
            sentencia.execute("DROP DATABASE IF EXISTS " + base);
        }
        // `ondexia` al final: en PostgreSQL 16 quien crea un rol con CREATEROLE
        // queda como concedente de su membresia, y no se puede borrar mientras
        // existan los roles que creo.
        for (String rol : new String[] {"ondexia_app", "ondexia_panel", "ondexia_migraciones",
                "ondexia"}) {
            sentencia.execute("DROP ROLE IF EXISTS " + rol);
        }
    }

    private static String variable(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(VARIABLE_URL + " esta definida pero falta " + nombre);
        }
        return valor;
    }
}
