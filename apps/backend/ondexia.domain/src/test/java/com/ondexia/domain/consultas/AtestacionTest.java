package com.ondexia.domain.consultas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.Ubigeo;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * La firma que sustituye a la salida a internet de la API.
 *
 * <h2>Por qué es de las cosas más importantes que probar del sistema</h2>
 *
 * <p>Porque es lo único que impide que el navegador declare su propio RUC como
 * activo y habido. Y su modo de fallo es del peor tipo: si la verificación
 * aceptara cualquier cosa, <strong>todo funcionaría</strong> —los registros
 * pasarían, nadie vería un error— y el agujero solo se notaría cuando alguien lo
 * usara a propósito.
 *
 * <p>Las pruebas que importan son por tanto las negativas: que una firma tocada
 * no pase, que un campo cambiado no pase, que una atestación vieja no pase.
 */
class AtestacionTest {

    private static final KeyPair CLAVES = generar();
    private static final KeyPair OTRAS = generar();
    private static final Instant AHORA = Instant.parse("2026-08-23T10:00:00Z");

    private static KeyPair generar() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (NoSuchAlgorithmException sinEd25519) {
            throw new IllegalStateException("Java 21 trae Ed25519 de serie", sinEd25519);
        }
    }

    private static String emitir(DatosDeRuc datos) {
        return Atestacion.emitir(datos, EXPIRA, CLAVES.getPrivate());
    }

    private static Atestacion.Contenido verificar(String token, Instant cuando) {
        return Atestacion.verificar(token, CLAVES.getPublic(), cuando);
    }
    private static final Instant EXPIRA = AHORA.plus(Duration.ofMinutes(10));

    private static DatosDeRuc datos() {
        return new DatosDeRuc(new Ruc("20601030013"), "ONDEXIA S.A.C.",
                EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO,
                "AV. AREQUIPA 100", new Ubigeo("150101"), "LIMA", "LIMA", "LIMA",
                true, false, "SOCIEDAD ANONIMA CERRADA", AHORA);
    }

    @Nested
    @DisplayName("Ida y vuelta")
    class IdaYVuelta {

        @Test
        void lo_que_se_firma_es_lo_que_se_lee() {
            String token = emitir(datos());

            Atestacion.Contenido contenido = verificar(token, AHORA);

            assertThat(contenido.datos()).isEqualTo(datos());
            assertThat(contenido.expiraEn()).isEqualTo(EXPIRA);
        }

        /**
         * El ubigeo y el tipo societario faltan cuando responde el proveedor de
         * respaldo. Si el formato no soportara nulos, el relevo produciría
         * atestaciones que no se pueden leer — y solo los días que el principal
         * esté caído.
         */
        @Test
        void los_campos_opcionales_sobreviven_vacios() {
            DatosDeRuc sinUbigeo = new DatosDeRuc(new Ruc("20601030013"), "ONDEXIA S.A.C.",
                    EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO,
                    "AV. AREQUIPA 100", null, null, null, "LIMA", false, false, null, AHORA);

            Atestacion.Contenido leido = verificar(emitir(sinUbigeo), AHORA);

            assertThat(leido.datos()).isEqualTo(sinUbigeo);
            assertThat(leido.datos().faltaUbigeo()).isTrue();
        }

        /**
         * El separador es U+001F precisamente para que ningún texto real pueda
         * contenerlo. Esta prueba usa lo que sí aparece en direcciones peruanas
         * —comas, guiones, barras— y comprobaría que no se cuelan como
         * separadores si algún día alguien cambia a un carácter imprimible.
         */
        @Test
        void los_textos_con_puntuacion_no_parten_los_campos() {
            DatosDeRuc conComas = new DatosDeRuc(new Ruc("20601030013"),
                    "COMERCIAL LOS ANDES S.R.L. | EX-ANDINA, S.A.",
                    EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO,
                    "AV. LA MARINA NRO. 100 INT. 2-B, URB. SAN MIGUEL",
                    new Ubigeo("150136"), "SAN MIGUEL", "LIMA", "LIMA",
                    false, false, "S.R.L.", AHORA);

            assertThat(verificar(emitir(conComas), AHORA).datos())
                    .isEqualTo(conComas);
        }

        /** Dos emisiones iguales dan lo mismo, o la firma no serviría de nada. */
        @Test
        void es_determinista() {
            assertThat(emitir(datos()))
                    .isEqualTo(emitir(datos()));
        }

        /** Sin padding y en base64 de URL: viaja en JSON y quizá en una ruta. */
        @Test
        void el_token_es_seguro_en_una_url() {
            assertThat(emitir(datos()))
                    .doesNotContain("+", "/", "=");
        }
    }

    @Nested
    @DisplayName("Lo que no debe pasar")
    class Rechazos {

        @Test
        void otra_clave_no_vale() {
            String token = emitir(datos());

            assertThatThrownBy(() -> Atestacion.verificar(token, OTRAS.getPublic(), AHORA))
                    .isInstanceOf(AtestacionInvalida.class);
        }

        @Test
        void una_firma_tocada_no_vale() {
            String token = emitir(datos());
            int punto = token.indexOf('.');
            String firma = token.substring(punto + 1);
            char primero = firma.charAt(0);
            String tocada = (primero == 'A' ? 'B' : 'A') + firma.substring(1);

            assertThatThrownBy(() ->
                    verificar(token.substring(0, punto + 1) + tocada, AHORA))
                    .isInstanceOf(AtestacionInvalida.class);
        }

        /**
         * El ataque real: cambiar NO_HABIDO por HABIDO en la carga y reenviar.
         *
         * <p>Es la razón de que la firma cubra los campos y no solo el RUC.
         */
        @Test
        @DisplayName("cambiar la condición en la carga no pasa la verificación")
        void manipular_la_carga_no_vale() {
            DatosDeRuc noHabido = new DatosDeRuc(new Ruc("20601030013"), "ONDEXIA S.A.C.",
                    EstadoContribuyente.ACTIVO, CondicionDomicilio.NO_HABIDO,
                    "AV. AREQUIPA 100", new Ubigeo("150101"), "LIMA", "LIMA", "LIMA",
                    false, false, null, AHORA);

            String token = emitir(noHabido);
            int punto = token.indexOf('.');
            String carga = new String(
                    Base64.getUrlDecoder().decode(token.substring(0, punto)),
                    StandardCharsets.UTF_8);

            String falsificada = carga.replace("NO_HABIDO", "HABIDO");
            assertThat(falsificada).isNotEqualTo(carga);

            String tokenFalso = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(falsificada.getBytes(StandardCharsets.UTF_8))
                    + token.substring(punto);

            assertThatThrownBy(() -> verificar(tokenFalso, AHORA))
                    .isInstanceOf(AtestacionInvalida.class);
        }

        /**
         * Y el mismo ataque sobre la razón social, que es el que DT-19 dejaba
         * abierto al firmar solo la puerta: una razón social que no coincide con
         * el padrón hace que SUNAT rechace todos los comprobantes.
         */
        @Test
        void manipular_la_razon_social_tampoco() {
            String token = emitir(datos());
            int punto = token.indexOf('.');
            String carga = new String(
                    Base64.getUrlDecoder().decode(token.substring(0, punto)),
                    StandardCharsets.UTF_8)
                    .replace("ONDEXIA S.A.C.", "OTRA EMPRESA S.A.");

            String tokenFalso = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(carga.getBytes(StandardCharsets.UTF_8))
                    + token.substring(punto);

            assertThatThrownBy(() -> verificar(tokenFalso, AHORA))
                    .isInstanceOf(AtestacionInvalida.class);
        }

        @Test
        void una_atestacion_caducada_no_vale() {
            String token = emitir(datos());

            assertThatThrownBy(() ->
                    verificar(token, EXPIRA.plusMillis(1)))
                    .isInstanceOf(AtestacionInvalida.class)
                    .hasMessageContaining("caducó");
        }

        @Test
        void justo_en_el_limite_todavia_vale() {
            String token = emitir(datos());

            assertThatCode(() -> verificar(token, EXPIRA))
                    .doesNotThrowAnyException();
        }

        @Test
        void lo_que_no_es_un_token_no_revienta_de_cualquier_manera() {
            for (String basura : new String[] {"", "   ", "sin-punto", ".", "a.", ".b",
                    "no-base64-@@@.tampoco-@@@"}) {
                assertThatThrownBy(() -> verificar(basura, AHORA))
                        .withFailMessage("entrada rechazada de forma controlada: «%s»", basura)
                        .isInstanceOf(AtestacionInvalida.class);
            }
        }

        @Test
        void nulo_tampoco() {
            assertThatThrownBy(() -> verificar(null, AHORA))
                    .isInstanceOf(AtestacionInvalida.class);
        }

        /**
         * Sin clave no se firma ni se verifica.
         *
         * <p>Lo peligroso sería lo contrario: firmar o verificar con una clave
         * ausente y que las dos partes «funcionaran». La firma no protegería de
         * nada y nada lo delataría.
         */
        @Test
        void sin_clave_no_se_firma_ni_se_verifica() {
            assertThatThrownBy(() -> Atestacion.emitir(datos(), EXPIRA, null))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> Atestacion.verificar(emitir(datos()), null, AHORA))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    /**
     * Que las claves de openssl se lean tal cual salen.
     *
     * <h2>Por qué esto merece una prueba con valores literales</h2>
     *
     * <p>Porque generar el par y usarlo en la misma JVM no prueba nada del
     * formato: {@code KeyPairGenerator} y {@code KeyFactory} se entienden entre
     * ellos por definición. Lo que puede fallar es lo que va a pasar de verdad
     * —pegar la salida de {@code openssl} en un parámetro de SSM— y ese fallo
     * aparecería al desplegar, no aquí.
     *
     * <p>Las claves son de un par generado con openssl para esta prueba y no se
     * usan en ningún entorno. Da igual que estén en el repositorio, y por eso
     * están: hacen la prueba reproducible sin depender de tener openssl.
     */
    @Nested
    @DisplayName("Claves de openssl")
    class Openssl {

        private static final String PRIVADA_PEM = """
                -----BEGIN PRIVATE KEY-----
                MC4CAQAwBQYDK2VwBCIEIKA3itKbjL1Lu/BLbAXSt2igZr8kb8tD5uSNcRYckQ+w
                -----END PRIVATE KEY-----
                """;

        private static final String PUBLICA_PEM = """
                -----BEGIN PUBLIC KEY-----
                MCowBQYDK2VwAyEArH+O+lntDsX9UwpK0dFrbTzckioDM9zhI7jnoLq7URw=
                -----END PUBLIC KEY-----
                """;

        @Test
        @DisplayName("con el envoltorio PEM y los saltos de linea, tal como salen")
        void un_par_de_openssl_firma_y_verifica() {
            String token = Atestacion.emitir(datos(), EXPIRA,
                    Atestacion.clavePrivada(PRIVADA_PEM));

            Atestacion.Contenido leido = Atestacion.verificar(
                    token, Atestacion.clavePublica(PUBLICA_PEM), AHORA);

            assertThat(leido.datos()).isEqualTo(datos());
        }

        /** Y también recortadas a mano, que es lo que hará medio mundo. */
        @Test
        void tambien_sin_el_envoltorio() {
            String soloBase64 = PUBLICA_PEM
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .strip();

            assertThat(Atestacion.clavePublica(soloBase64))
                    .isEqualTo(Atestacion.clavePublica(PUBLICA_PEM));
        }

        /**
         * La pública de OTRO par no verifica. Comprueba que la prueba de arriba
         * no pasa por casualidad —por ejemplo, si `verificar` devolviera cierto
         * ante cualquier cosa.
         */
        @Test
        void la_publica_equivocada_no_verifica() {
            String token = Atestacion.emitir(datos(), EXPIRA,
                    Atestacion.clavePrivada(PRIVADA_PEM));

            assertThatThrownBy(() ->
                    Atestacion.verificar(token, CLAVES.getPublic(), AHORA))
                    .isInstanceOf(AtestacionInvalida.class);
        }

        @Test
        void una_clave_que_no_lo_es_falla_diciendolo() {
            assertThatThrownBy(() -> Atestacion.clavePublica("esto no es una clave"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Ed25519");
        }
    }
}
