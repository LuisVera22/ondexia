package com.ondexia.infrastructure.salida.consultas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.Ubigeo;
import com.ondexia.domain.consultas.CondicionDomicilio;
import com.ondexia.domain.consultas.ConsultaDeRuc;
import com.ondexia.domain.consultas.ConsultaNoDisponible;
import com.ondexia.domain.consultas.DatosDeRuc;
import com.ondexia.domain.consultas.EstadoContribuyente;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El relevo entre proveedores.
 *
 * <h2>Por qué con dobles y no contra la red</h2>
 *
 * <p>Porque lo que hay que comprobar son las decisiones —cuándo se pasa al
 * siguiente, cuándo no, y qué se propaga si nadie responde—, y ninguna depende
 * de un proveedor concreto. Una prueba contra Decolecta mediría además su
 * disponibilidad, fallaría los días que ellos tengan un mal rato y gastaría cupo
 * de un plan de pago en cada ejecución.
 *
 * <h2>Por qué se prueba</h2>
 *
 * <p>Porque estas decisiones fallan en silencio. Si la cascada dejara de
 * relevar, todo seguiría funcionando mientras el principal esté en pie — que es
 * siempre, hasta el día que importa. No hay forma de notarlo mirando.
 */
class ConsultaDeRucEnCascadaTest {

    private static final Ruc RUC = new Ruc("20601030013");

    private static ConsultaDeRuc responde(String razonSocial) {
        return ruc -> Optional.of(new DatosDeRuc(ruc, razonSocial,
                EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO, "AV. AREQUIPA 100",
                new Ubigeo("150101"), "LIMA", "LIMA", "LIMA", false, false, null,
                Instant.parse("2026-08-23T10:00:00Z")));
    }

    /** El padrón no conoce el RUC. Es una respuesta, no un fallo. */
    private static ConsultaDeRuc noLoConoce() {
        return ruc -> Optional.empty();
    }

    private static ConsultaDeRuc falla(boolean reintentable) {
        return ruc -> {
            throw new ConsultaNoDisponible("prueba", "caído", reintentable);
        };
    }

    /** Un proveedor que anota que lo llamaron, para probar que no se llama. */
    private static ConsultaDeRuc espia(List<String> registro) {
        return ruc -> {
            registro.add("llamado");
            return Optional.empty();
        };
    }

    @Test
    @DisplayName("con el principal en pie, no se molesta al relevo")
    void el_primero_que_responde_gana() {
        List<String> llamados = new ArrayList<>();

        Optional<DatosDeRuc> resultado = new ConsultaDeRucEnCascada(
                List.of(responde("ONDEXIA S.A.C."), espia(llamados))).consultar(RUC);

        assertThat(resultado).get().extracting(DatosDeRuc::razonSocial)
                .isEqualTo("ONDEXIA S.A.C.");
        assertThat(llamados)
                .withFailMessage("cada llamada de más cuesta cupo y espera")
                .isEmpty();
    }

    @Test
    @DisplayName("un fallo del principal pasa al relevo")
    void tras_un_fallo_se_pregunta_al_siguiente() {
        Optional<DatosDeRuc> resultado = new ConsultaDeRucEnCascada(
                List.of(falla(true), responde("ONDEXIA S.A.C."))).consultar(RUC);

        assertThat(resultado).get().extracting(DatosDeRuc::razonSocial)
                .isEqualTo("ONDEXIA S.A.C.");
    }

    /**
     * Una clave caducada en el principal es exactamente para lo que existe el
     * relevo. Si «no reintentable» detuviera la cascada, el respaldo no serviría
     * en el caso más probable de todos.
     */
    @Test
    @DisplayName("un fallo no reintentable también pasa al relevo")
    void no_reintentable_habla_del_proveedor_no_de_la_cascada() {
        assertThat(new ConsultaDeRucEnCascada(
                List.of(falla(false), responde("ONDEXIA S.A.C."))).consultar(RUC))
                .isPresent();
    }

    /**
     * La decisión que ahorra la mitad de la espera en el caso más frecuente: una
     * errata de tecleo. Los tres proveedores leen el mismo padrón, así que
     * preguntar otra vez daría la misma respuesta más despacio.
     */
    @Test
    @DisplayName("«no existe» es una respuesta y detiene la cascada")
    void un_no_existe_no_se_reintenta_con_otro() {
        List<String> llamados = new ArrayList<>();

        assertThat(new ConsultaDeRucEnCascada(List.of(noLoConoce(), espia(llamados)))
                .consultar(RUC)).isEmpty();
        assertThat(llamados)
                .withFailMessage("un RUC mal escrito no mejora preguntando dos veces")
                .isEmpty();
    }

    @Test
    @DisplayName("si nadie responde, se propaga el fallo")
    void sin_nadie_en_pie_falla() {
        assertThatThrownBy(() -> new ConsultaDeRucEnCascada(List.of(falla(true), falla(true)))
                .consultar(RUC))
                .isInstanceOf(ConsultaNoDisponible.class)
                .hasMessageContaining("No pudimos verificar el RUC");
    }

    /**
     * Que el «reintentable» final sea un OR y no el del último.
     *
     * <p>Quedándose con el último, un principal con caída pasajera seguido de un
     * relevo con la clave mal puesta daría «no reintentable»: se le diría a
     * alguien que no hay nada que hacer cuando bastaba esperar un minuto.
     */
    @Test
    @DisplayName("basta que uno fuera pasajero para que el conjunto lo sea")
    void reintentable_se_acumula() {
        assertThatThrownBy(() -> new ConsultaDeRucEnCascada(List.of(falla(true), falla(false)))
                .consultar(RUC))
                .isInstanceOfSatisfying(ConsultaNoDisponible.class,
                        fallo -> assertThat(fallo.esReintentable()).isTrue());
    }

    @Test
    @DisplayName("si ninguno era pasajero, no se invita a esperar")
    void sin_ninguno_pasajero_no_es_reintentable() {
        assertThatThrownBy(() -> new ConsultaDeRucEnCascada(List.of(falla(false), falla(false)))
                .consultar(RUC))
                .isInstanceOfSatisfying(ConsultaNoDisponible.class,
                        fallo -> assertThat(fallo.esReintentable()).isFalse());
    }

    /** Una cascada vacía no consulta nada, y callarlo sería peor. */
    @Test
    void sin_proveedores_no_se_construye() {
        assertThatThrownBy(() -> new ConsultaDeRucEnCascada(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
