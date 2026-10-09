package com.ondexia.domain.consultas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.Ubigeo;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * La regla que decide si un RUC puede registrarse.
 *
 * <p>Se prueba con insistencia porque es la única puerta del alta y falla en la
 * dirección peligrosa: un fallo que rechaza a alguien apto se descubre el mismo
 * día —hay una persona quejándose—, pero uno que <strong>acepta</strong> a un no
 * habido no se nota hasta que sus clientes pierden el crédito fiscal.
 */
class DatosDeRucTest {

    private static final Ruc RUC = new Ruc("20601030013");

    private static DatosDeRuc con(EstadoContribuyente estado, CondicionDomicilio condicion) {
        return new DatosDeRuc(RUC, "ONDEXIA S.A.C.", estado, condicion,
                "AV. AREQUIPA 100", new Ubigeo("150101"), "LIMA", "LIMA", "LIMA",
                false, false, "SOCIEDAD ANONIMA CERRADA", Instant.parse("2026-08-23T10:00:00Z"));
    }

    @Nested
    @DisplayName("Aptitud para el registro")
    class Aptitud {

        @Test
        void activo_y_habido_pasa() {
            assertThat(con(EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO)
                    .aptaParaRegistro()).isTrue();
        }

        /**
         * El caso que motiva que haya dos puertas y no una.
         *
         * <p>Un RUC activo y no habido es corriente, y solo mirar el estado —que
         * es el error natural— lo dejaría entrar.
         */
        @Test
        void activo_pero_no_habido_no_pasa() {
            assertThat(con(EstadoContribuyente.ACTIVO, CondicionDomicilio.NO_HABIDO)
                    .aptaParaRegistro()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = EstadoContribuyente.class, names = "ACTIVO", mode =
                EnumSource.Mode.EXCLUDE)
        void ningun_estado_distinto_de_activo_pasa(EstadoContribuyente estado) {
            assertThat(con(estado, CondicionDomicilio.HABIDO).aptaParaRegistro()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = CondicionDomicilio.class, names = "HABIDO", mode =
                EnumSource.Mode.EXCLUDE)
        void ninguna_condicion_distinta_de_habido_pasa(CondicionDomicilio condicion) {
            assertThat(con(EstadoContribuyente.ACTIVO, condicion).aptaParaRegistro()).isFalse();
        }

        /**
         * Que la lista de aptos sea cerrada, no una lista de excepciones.
         *
         * <p>Si mañana SUNAT añade un estado y el criterio fuera «todo menos las
         * bajas», ese valor nuevo permitiría emitir sin que nadie lo decidiera.
         * Esta prueba obliga a que el valor nuevo llegue rechazado por defecto.
         */
        @Test
        void solo_un_estado_y_una_condicion_permiten_emitir() {
            assertThat(EstadoContribuyente.values())
                    .filteredOn(EstadoContribuyente::permiteEmitir)
                    .containsExactly(EstadoContribuyente.ACTIVO);
            assertThat(CondicionDomicilio.values())
                    .filteredOn(CondicionDomicilio::esHabido)
                    .containsExactly(CondicionDomicilio.HABIDO);
        }
    }

    @Nested
    @DisplayName("Motivo del rechazo")
    class Motivo {

        @Test
        void apta_no_tiene_motivo() {
            assertThat(con(EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO)
                    .motivoDeRechazo()).isNull();
        }

        /** Todo rechazo tiene que poder explicarse, o el mensaje queda vacío. */
        @ParameterizedTest
        @EnumSource(EstadoContribuyente.class)
        void todo_estado_no_apto_explica_por_que(EstadoContribuyente estado) {
            String motivo = con(estado, CondicionDomicilio.HABIDO).motivoDeRechazo();
            if (estado.permiteEmitir()) {
                assertThat(motivo).isNull();
            } else {
                assertThat(motivo).isNotBlank();
            }
        }

        @ParameterizedTest
        @EnumSource(CondicionDomicilio.class)
        void toda_condicion_no_apta_explica_por_que(CondicionDomicilio condicion) {
            String motivo = con(EstadoContribuyente.ACTIVO, condicion).motivoDeRechazo();
            if (condicion.esHabido()) {
                assertThat(motivo).isNull();
            } else {
                assertThat(motivo).isNotBlank();
            }
        }

        /**
         * Cuando fallan las dos cosas, se nombra la que hay que resolver primero.
         *
         * <p>Decirle a alguien con el RUC de baja que además no es habido es
         * ruido: no puede arreglar lo segundo sin arreglar lo primero.
         */
        @Test
        void con_dos_impedimentos_se_nombra_el_estado() {
            String motivo = con(EstadoContribuyente.BAJA_DEFINITIVA,
                    CondicionDomicilio.NO_HABIDO).motivoDeRechazo();

            assertThat(motivo).contains("baja definitiva").doesNotContain("HABIDO");
        }
    }

    @Nested
    @DisplayName("Lo que no puede faltar")
    class Obligatorios {

        @Test
        void sin_razon_social_no_se_construye() {
            assertThatThrownBy(() -> new DatosDeRuc(RUC, "  ", EstadoContribuyente.ACTIVO,
                    CondicionDomicilio.HABIDO, "AV. AREQUIPA 100", null, null, null, null,
                    false, false, null, Instant.now()))
                    .isInstanceOf(ReglaDeNegocioViolada.class)
                    .hasMessageContaining("razón social");
        }

        /**
         * Sin fecha no se construye, y no es un requisito ceremonial: es lo que
         * impide que la base guarde «ACTIVO» sin decir de cuándo.
         */
        @Test
        void sin_fecha_de_consulta_no_se_construye() {
            assertThatThrownBy(() -> new DatosDeRuc(RUC, "ONDEXIA S.A.C.",
                    EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO, "AV. AREQUIPA 100",
                    null, null, null, null, false, false, null, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("El hueco del respaldo")
    class Ubigeo_ausente {

        /**
         * apiperu.dev no devuelve ubigeo, y el ubigeo va en el comprobante. El
         * objeto tiene que poder existir sin él —o el relevo sería imposible— y
         * a la vez decir que falta, para que alguien lo complete desde el padrón.
         */
        @Test
        void sin_ubigeo_se_construye_pero_lo_avisa() {
            DatosDeRuc datos = new DatosDeRuc(RUC, "ONDEXIA S.A.C.",
                    EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO, "AV. AREQUIPA 100",
                    null, null, null, "LIMA", false, false, null, Instant.now());

            assertThat(datos.faltaUbigeo()).isTrue();
            assertThat(datos.aptaParaRegistro())
                    .withFailMessage("la falta del ubigeo no es motivo de rechazo fiscal")
                    .isTrue();
        }

        @Test
        void con_ubigeo_no_avisa_de_nada() {
            assertThat(con(EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO)
                    .faltaUbigeo()).isFalse();
        }
    }
}
