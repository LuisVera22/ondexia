package com.ondexia.domain.consultas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.Ubigeo;
import java.nio.charset.StandardCharsets;
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

    private static final byte[] SECRETO = "un-secreto-de-prueba".getBytes(StandardCharsets.UTF_8);
    private static final Instant AHORA = Instant.parse("2026-08-23T10:00:00Z");
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
            String token = Atestacion.emitir(datos(), EXPIRA, SECRETO);

            Atestacion.Contenido contenido = Atestacion.verificar(token, SECRETO, AHORA);

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

            Atestacion.Contenido leido = Atestacion.verificar(
                    Atestacion.emitir(sinUbigeo, EXPIRA, SECRETO), SECRETO, AHORA);

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

            assertThat(Atestacion.verificar(
                    Atestacion.emitir(conComas, EXPIRA, SECRETO), SECRETO, AHORA).datos())
                    .isEqualTo(conComas);
        }

        /** Dos emisiones iguales dan lo mismo, o la firma no serviría de nada. */
        @Test
        void es_determinista() {
            assertThat(Atestacion.emitir(datos(), EXPIRA, SECRETO))
                    .isEqualTo(Atestacion.emitir(datos(), EXPIRA, SECRETO));
        }

        /** Sin padding y en base64 de URL: viaja en JSON y quizá en una ruta. */
        @Test
        void el_token_es_seguro_en_una_url() {
            assertThat(Atestacion.emitir(datos(), EXPIRA, SECRETO))
                    .doesNotContain("+", "/", "=");
        }
    }

    @Nested
    @DisplayName("Lo que no debe pasar")
    class Rechazos {

        @Test
        void otro_secreto_no_vale() {
            String token = Atestacion.emitir(datos(), EXPIRA, SECRETO);

            assertThatThrownBy(() -> Atestacion.verificar(
                    token, "otro-secreto".getBytes(StandardCharsets.UTF_8), AHORA))
                    .isInstanceOf(AtestacionInvalida.class);
        }

        @Test
        void una_firma_tocada_no_vale() {
            String token = Atestacion.emitir(datos(), EXPIRA, SECRETO);
            int punto = token.indexOf('.');
            String firma = token.substring(punto + 1);
            char primero = firma.charAt(0);
            String tocada = (primero == 'A' ? 'B' : 'A') + firma.substring(1);

            assertThatThrownBy(() ->
                    Atestacion.verificar(token.substring(0, punto + 1) + tocada, SECRETO, AHORA))
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

            String token = Atestacion.emitir(noHabido, EXPIRA, SECRETO);
            int punto = token.indexOf('.');
            String carga = new String(
                    Base64.getUrlDecoder().decode(token.substring(0, punto)),
                    StandardCharsets.UTF_8);

            String falsificada = carga.replace("NO_HABIDO", "HABIDO");
            assertThat(falsificada).isNotEqualTo(carga);

            String tokenFalso = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(falsificada.getBytes(StandardCharsets.UTF_8))
                    + token.substring(punto);

            assertThatThrownBy(() -> Atestacion.verificar(tokenFalso, SECRETO, AHORA))
                    .isInstanceOf(AtestacionInvalida.class);
        }

        /**
         * Y el mismo ataque sobre la razón social, que es el que DT-19 dejaba
         * abierto al firmar solo la puerta: una razón social que no coincide con
         * el padrón hace que SUNAT rechace todos los comprobantes.
         */
        @Test
        void manipular_la_razon_social_tampoco() {
            String token = Atestacion.emitir(datos(), EXPIRA, SECRETO);
            int punto = token.indexOf('.');
            String carga = new String(
                    Base64.getUrlDecoder().decode(token.substring(0, punto)),
                    StandardCharsets.UTF_8)
                    .replace("ONDEXIA S.A.C.", "OTRA EMPRESA S.A.");

            String tokenFalso = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(carga.getBytes(StandardCharsets.UTF_8))
                    + token.substring(punto);

            assertThatThrownBy(() -> Atestacion.verificar(tokenFalso, SECRETO, AHORA))
                    .isInstanceOf(AtestacionInvalida.class);
        }

        @Test
        void una_atestacion_caducada_no_vale() {
            String token = Atestacion.emitir(datos(), EXPIRA, SECRETO);

            assertThatThrownBy(() ->
                    Atestacion.verificar(token, SECRETO, EXPIRA.plusMillis(1)))
                    .isInstanceOf(AtestacionInvalida.class)
                    .hasMessageContaining("caducó");
        }

        @Test
        void justo_en_el_limite_todavia_vale() {
            String token = Atestacion.emitir(datos(), EXPIRA, SECRETO);

            assertThatCode(() -> Atestacion.verificar(token, SECRETO, EXPIRA))
                    .doesNotThrowAnyException();
        }

        @Test
        void lo_que_no_es_un_token_no_revienta_de_cualquier_manera() {
            for (String basura : new String[] {"", "   ", "sin-punto", ".", "a.", ".b",
                    "no-base64-@@@.tampoco-@@@"}) {
                assertThatThrownBy(() -> Atestacion.verificar(basura, SECRETO, AHORA))
                        .withFailMessage("entrada rechazada de forma controlada: «%s»", basura)
                        .isInstanceOf(AtestacionInvalida.class);
            }
        }

        @Test
        void nulo_tampoco() {
            assertThatThrownBy(() -> Atestacion.verificar(null, SECRETO, AHORA))
                    .isInstanceOf(AtestacionInvalida.class);
        }

        /**
         * Sin secreto no se firma ni se verifica.
         *
         * <p>Lo peligroso sería lo contrario: firmar con una clave vacía cuando
         * falta la variable de entorno. Las dos partes «funcionarían» y la firma
         * no protegería de nada, porque cualquiera podría reproducirla.
         */
        @Test
        void sin_secreto_no_se_firma() {
            assertThatThrownBy(() -> Atestacion.emitir(datos(), EXPIRA, new byte[0]))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> Atestacion.emitir(datos(), EXPIRA, null))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
