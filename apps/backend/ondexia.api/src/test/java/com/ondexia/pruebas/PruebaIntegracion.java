package com.ondexia.pruebas;

import com.ondexia.domain.identidad.Rol;
import com.ondexia.domain.identidad.RolRepositorio;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
// Spring Boot 4 movio esta anotacion al modulo spring-boot-webmvc-test.
// El paquete antiguo era org.springframework.boot.test.autoconfigure.web.servlet.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base de las pruebas de integracion.
 *
 * <h2>PostgreSQL de verdad, no H2</h2>
 *
 * H2 seria mas rapido de arrancar y no serviria para nada aqui. Este esquema
 * depende de cosas que H2 no tiene o simula distinto: Row Level Security,
 * {@code UNIQUE NULLS NOT DISTINCT}, {@code jsonb}, disparadores de restriccion
 * diferidos, {@code set_config} con alcance de transaccion. Son justamente las
 * piezas que hay que probar — probar las demas contra otro motor solo da una
 * confianza que no corresponde a nada.
 *
 * <h2>Un solo servidor para toda la suite</h2>
 *
 * Se arranca en un bloque estatico y no se para. Arrancar uno por clase de
 * prueba multiplica el tiempo de la suite por el numero de clases; el proceso
 * de Maven termina y Ryuk, el vigilante de Testcontainers, se encarga de
 * retirarlo. De donde sale —contenedor o servidor externo— lo decide
 * {@link ServidorDePruebas}.
 *
 * <h2>Perfil local</h2>
 *
 * Se activa a proposito, por dos motivos. Da un {@code JwtEncoder} con el que
 * emitir tokens reales —de modo que las pruebas recorren la misma cadena de
 * seguridad que produccion, decodificacion de JWT incluida, en vez de saltarsela
 * con un contexto de autenticacion simulado—. Y carga los datos de ejemplo, con
 * lo que esos datos quedan verificados en cada ejecucion en lugar de pudrirse
 * hasta el dia que alguien intenta usarlos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public abstract class PruebaIntegracion {

    private static final String ROL_APLICACION = "ondexia";

    // Las credenciales del servidor son las del SUPERUSUARIO BOOTSTRAP, y no
    // las usa ninguna prueba. Ver crearRolDeAplicacion().
    private static final ServidorDePruebas SERVIDOR = ServidorDePruebas.arrancar();

    static {
        crearRolDeAplicacion();
    }

    /**
     * Crea el rol y la base de datos con los que corre la aplicacion.
     *
     * <h2>Por que no se usa el usuario del contenedor</h2>
     *
     * Un superusuario de PostgreSQL <strong>no esta sujeto a ninguna politica de
     * Row Level Security</strong>, y {@code FORCE ROW LEVEL SECURITY} tampoco le
     * alcanza: {@code FORCE} solo afecta al propietario de la tabla. Si las
     * pruebas corrieran como {@code POSTGRES_USER}, el aislamiento multiempresa
     * estaria activo y sin ningun efecto, y las pruebas que lo verifican
     * pasarian o fallarian por razones ajenas al codigo.
     *
     * <h2>Por que no basta con degradarlo</h2>
     *
     * No se puede. PostgreSQL lo rechaza:
     *
     * <pre>
     * ALTER ROLE postgres NOSUPERUSER;
     * ERROR:  permission denied to alter role
     * DETAIL: The bootstrap superuser must have the SUPERUSER attribute.
     * </pre>
     *
     * <p>La unica salida es que la aplicacion use otro rol. Este metodo lo crea,
     * igual que hace {@code docker/initdb/10-rol-aplicacion.sql} en el entorno
     * local, y por el mismo motivo.
     */
    private static void crearRolDeAplicacion() {
        try (java.sql.Connection conexion = java.sql.DriverManager.getConnection(
                SERVIDOR.urlBootstrap(), SERVIDOR.usuario(), SERVIDOR.contrasena());
                java.sql.Statement sentencia = conexion.createStatement()) {
            // En un contenedor recien creado no hay nada que borrar. En un
            // servidor externo queda lo de la ejecucion anterior, y las
            // pruebas parten siempre de un servidor limpio.
            ServidorDePruebas.borrarRestosDeEjecucionesAnteriores(sentencia);
            /*
             * CREATEROLE, porque las migraciones crean roles.
             *
             * En RDS quien aplica Flyway es el usuario maestro, que puede
             * crearlos. Aqui el rol de la aplicacion es el dueno de su base y
             * nada mas, asi que la V14 —que crea ondexia_app y ondexia_panel
             * tambien fuera de RDS, para que GrantsDeLaAplicacionIT pueda mirar
             * sus privilegios— moria con «permission denied to create role».
             *
             * CREATEROLE no exime de Row Level Security: eso solo lo hacen
             * SUPERUSER y BYPASSRLS, y este rol no tiene ninguno de los dos. El
             * aislamiento se sigue probando de verdad.
             */
            sentencia.execute("CREATE ROLE " + ROL_APLICACION
                    + " LOGIN CREATEROLE PASSWORD '" + ROL_APLICACION + "'");
            // Dueno de su base: le basta para que Flyway cree tablas, funciones,
            // disparadores y politicas, sin ningun privilegio de cluster.
            sentencia.execute(
                    "CREATE DATABASE " + ROL_APLICACION + " OWNER " + ROL_APLICACION);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(
                    "No se pudo preparar el rol de aplicacion en el servidor de pruebas", e);
        }
    }

    @DynamicPropertySource
    static void configurarBaseDatos(DynamicPropertyRegistry registro) {
        // Se compone la URL a mano en vez de usar la del servidor: esa apunta a
        // la base de mantenimiento (postgres), no a la de la aplicacion.
        registro.add("spring.datasource.url", () -> "jdbc:postgresql://"
                + SERVIDOR.anfitrion() + ":" + SERVIDOR.puerto() + "/" + ROL_APLICACION);
        registro.add("spring.datasource.username", () -> ROL_APLICACION);
        registro.add("spring.datasource.password", () -> ROL_APLICACION);
    }

    // Identificadores de los datos de ejemplo (db/local/V900). Son literales
    // fijos justamente para poder referenciarlos aqui sin consultarlos antes.
    protected static final String SUB_DEMO = "usuario-demo";
    protected static final String EMPRESA_ADMINISTRADA = "00000000-0000-4000-8000-000000000010";
    protected static final String EMPRESA_COMO_VENDEDOR = "00000000-0000-4000-8000-000000000011";
    protected static final String USUARIO_DEMO = "00000000-0000-4000-8000-000000000002";
    protected static final UUID CUENTA_DEMO = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    private RolRepositorio rolesDeLaBase;

    /**
     * Un rol A MEDIDA de la cuenta demo (V900): {@code VENDEDOR} o
     * {@code ALMACENERO}.
     *
     * <p>Antes de la V16 eran roles del sistema y las pruebas los pedian con
     * {@code buscarPredefinido}. Ya no existen como tales —el sistema solo trae
     * ADMINISTRADOR— y la cuenta demo los tiene creados como los tendria un
     * cliente cualquiera, que es ademas el caso que las pruebas de roles deben
     * ejercitar: un rol que no puede todo y que la cuenta puede editar.
     */
    protected Rol rolDeLaCuenta(String codigo) {
        return rolesDeLaBase.listarDisponibles(CUENTA_DEMO).stream()
                .filter(rol -> CUENTA_DEMO.equals(rol.cuentaId()) && rol.codigo().equals(codigo))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "La cuenta demo no tiene el rol " + codigo + "; revisa la V900."));
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private JwtEncoder emisorTokens;

    /**
     * Emite un token valido para el sujeto indicado.
     *
     * <p>Es el mismo emisor que usa {@code /desarrollo/token}, asi que el token
     * pasa por el decodificador real y por {@code ResolutorContexto} igual que
     * en produccion.
     */
    protected String tokenPara(String sub) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://desarrollo.ondexia.local")
                .subject(sub)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(Duration.ofMinutes(10)))
                .claim("token_use", "access")
                // El correo va dentro porque el alta lo necesita: crea la fila
                // `usuario` a partir del token, nunca del cuerpo de la peticion.
                // Sin esta reclamacion el registro responde 401, que es como se
                // detecto — un 401 emitido por nuestro propio controlador y no
                // por la cadena de seguridad, que despista bastante.
                .claim("email", sub + "@prueba.ondexia.local")
                .build();
        return emisorTokens.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * Token como los que emite Cognito de verdad: <strong>sin</strong> la
     * reclamacion {@code email}.
     *
     * <p>El token de ACCESO de Cognito no la lleva —vive en el de identidad— y
     * es el de acceso el que llega a la API. `tokenPara` si la incluye por
     * comodidad, y esa comodidad escondio un fallo: el alta guardaba el `sub`
     * como correo y nadie lo noto hasta mirar la tabla.
     */
    protected String tokenSinCorreo(String sub) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://desarrollo.ondexia.local")
                .subject(sub)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(Duration.ofMinutes(10)))
                .claim("token_use", "access")
                // Como Cognito: el username de un pool con acceso por correo es
                // el propio UUID, no el correo. Ese fue el respaldo que
                // escribia basura.
                .claim("username", sub)
                .build();
        return emisorTokens.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * Token de IDENTIDAD, el unico que lleva el correo firmado por Cognito.
     *
     * <p>Lo exige {@code POST /api/v1/registro/vinculo} y nada mas. Se separa de
     * {@code tokenPara} en vez de anadirle parametros porque la diferencia entre
     * los dos —{@code token_use} y {@code email_verified}— es justo lo que decide
     * si alguien puede reclamar la invitacion de otra persona: mezclarlos haria
     * que una prueba pasara por el camino equivocado sin que se note.
     *
     * @param verificado a false emula a quien se registro y aun no confirmo su
     *                   buzon, o a quien puso el correo de otro
     */
    protected String tokenDeIdentidad(String sub, String email, boolean verificado) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://desarrollo.ondexia.local")
                .subject(sub)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(Duration.ofMinutes(10)))
                .claim("token_use", "id")
                .claim("email", email)
                .claim("email_verified", verificado)
                .build();
        return emisorTokens.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    protected String autorizacionDemo() {
        return "Bearer " + tokenPara(SUB_DEMO);
    }
}
