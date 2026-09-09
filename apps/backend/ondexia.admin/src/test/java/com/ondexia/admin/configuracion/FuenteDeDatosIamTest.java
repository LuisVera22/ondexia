package com.ondexia.admin.configuracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

/**
 * La firma del token de RDS.
 *
 * <h2>Por qué existe esta prueba</h2>
 *
 * <p>{@link FuenteDeDatosIam} solo se instancia con el perfil {@code aws}, así
 * que ninguna prueba la tocaba y su primer estreno fue un despliegue. Faltaba el
 * proveedor de credenciales en {@code RdsUtilities}, y el fallo no aparece al
 * construir sino al firmar:
 *
 * <pre>
 * IllegalArgumentException: CredentialProvider should be provided either in
 * GenerateAuthenticationTokenRequest object or RdsUtilities object
 * </pre>
 *
 * <p>Peor aún, llegó disfrazado. Hikari no pudo abrir la conexión, Hibernate se
 * quedó sin metadatos y el mensaje visible fue «Unable to determine Dialect
 * without JDBC metadata». Con SnapStart tomando la instantánea durante el
 * arranque, la versión quedó en {@code Failed} y el despliegue se paró después
 * de veinticinco minutos.
 *
 * <h2>Y por qué se puede probar aquí</h2>
 *
 * <p>Porque firmar el token es una <strong>operación local</strong>: no habla con
 * RDS ni con STS. Es lo mismo que permite que esto funcione en una subred sin
 * NAT, y de paso lo hace comprobable sin credenciales reales ni contenedores.
 *
 * <p>Lo que NO cubre: que RDS acepte el token. Eso depende del rol de la base, de
 * la política de {@code rds-db:connect} y de que los tres nombres de usuario
 * coincidan, y solo se puede ver desplegando.
 */
@DisplayName("FuenteDeDatosIam · firma del token")
class FuenteDeDatosIamTest {

    private static final String CLAVE = "AKIAIOSFODNN7EXAMPLE";
    private static final String SECRETO = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";

    private static final String ANFITRION = "ondexia-dev.abc123.us-east-1.rds.amazonaws.com";
    private static final String USUARIO = "ondexia_app";

    /*
     * Credenciales de ejemplo de la documentación de AWS, no de nadie. La cadena
     * predeterminada mira las propiedades del sistema antes que cualquier otra
     * cosa, así que resuelve sin red y sin depender de si la máquina que ejecuta
     * la prueba tiene un perfil de AWS configurado.
     */
    @BeforeEach
    void ponerCredenciales() {
        System.setProperty("aws.accessKeyId", CLAVE);
        System.setProperty("aws.secretAccessKey", SECRETO);
    }

    @AfterEach
    void quitarCredenciales() {
        System.clearProperty("aws.accessKeyId");
        System.clearProperty("aws.secretAccessKey");
    }

    private static final java.nio.file.Path RAIZ = java.nio.file.Path.of("/tmp/raiz-de-prueba.pem");

    private FuenteDeDatosIam fuente() {
        return new FuenteDeDatosIam(
                "jdbc:postgresql://" + ANFITRION + ":5432/ondexia",
                USUARIO,
                ANFITRION,
                5432,
                Region.US_EAST_1,
                RAIZ);
    }

    @Test
    @DisplayName("la conexión exige verify-full con el paquete de RDS en un archivo")
    void laConexionExigeVerifyFull() {
        var propiedades = fuente().propiedadesDeConexion();

        assertThat(propiedades.getProperty("sslmode")).isEqualTo("verify-full");
        assertThat(propiedades.getProperty("sslrootcert"))
                .isEqualTo(RAIZ.toString())
                .doesNotStartWith("classpath:");
    }

    @Test
    @DisplayName("firma sin lanzar: el proveedor de credenciales está puesto")
    void firmaSinLanzar() {
        assertThatCode(() -> fuente().tokenNuevo()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el token lleva el anfitrión, el usuario y la firma")
    void elTokenLlevaLoQueRdsEspera() {
        String token = fuente().tokenNuevo();

        assertThat(token)
                .as("RDS valida el anfitrión firmado contra el que recibe la conexión")
                .contains(ANFITRION)
                .as("el usuario viaja en el token, no en el saludo de PostgreSQL")
                .contains("DBUser=" + USUARIO)
                .as("es una URL prefirmada de SigV4")
                .contains("X-Amz-Signature=")
                .contains("Action=connect");
    }

    @Test
    @DisplayName("el secreto no viaja en el token")
    void elSecretoNoViaja() {
        // La firma prueba posesión del secreto sin transmitirlo. Si esto alguna
        // vez fallara, el token —que acaba en los registros de conexión de
        // PostgreSQL— sería una filtración de credenciales.
        assertThat(fuente().tokenNuevo()).doesNotContain(SECRETO);
    }
}
