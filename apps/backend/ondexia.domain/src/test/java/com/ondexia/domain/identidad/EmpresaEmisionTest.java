package com.ondexia.domain.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Qué hace falta para emitir y para pasar a producción (doc 14 §4). */
class EmpresaEmisionTest {

    private static final Instant AHORA = Instant.parse("2026-09-08T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 9, 8);

    private static Empresa empresa() {
        return new Empresa(UUID.randomUUID(), UUID.randomUUID(), new Ruc("20100000009"),
                "COMERCIAL DEMO S.A.C.", "Av. Siempre Viva 742");
    }

    @Test
    @DisplayName("Sin certificado ni usuario SOL no hay emisión electrónica")
    void sinCredencialesNoEmite() {
        var e = empresa();
        assertThat(e.puedeEmitirElectronicamente()).isFalse();
        assertThat(e.certificado()).isNull();
    }

    @Test
    @DisplayName("Cargar el certificado habilita la emisión aunque falte verificarlo")
    void cargarHabilita() {
        var e = empresa();
        e.cargarCertificado("MODDATOS", AHORA);
        assertThat(e.puedeEmitirElectronicamente()).isTrue();
        assertThat(e.certificado().cargadoEn()).isEqualTo(AHORA);
        assertThat(e.certificado().verificadoEn()).isNull();
    }

    @Test
    @DisplayName("Producción exige certificado verificado y vigente")
    void produccionExigeCertificadoVigente() {
        var e = empresa();
        assertThatThrownBy(() -> e.habilitarProduccion(HOY))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting("codigo").isEqualTo("sin_certificado_vigente");

        e.cargarCertificado("MODDATOS", AHORA);
        assertThatThrownBy(() -> e.habilitarProduccion(HOY))
                .as("cargado pero sin verificar")
                .isInstanceOf(ReglaDeNegocioViolada.class);

        e.anotarFalloDeCertificado(AHORA, "Contraseña incorrecta");
        assertThatThrownBy(() -> e.habilitarProduccion(HOY))
                .as("verificado con error")
                .isInstanceOf(ReglaDeNegocioViolada.class);

        e.anotarVerificacionDeCertificado(AHORA, "CN=DEMO", HOY.minusDays(1));
        assertThatThrownBy(() -> e.habilitarProduccion(HOY))
                .as("caducado ayer")
                .isInstanceOf(ReglaDeNegocioViolada.class);

        e.anotarVerificacionDeCertificado(AHORA, "CN=DEMO", HOY.plusYears(1));
        e.habilitarProduccion(HOY);
        assertThat(e.modoSunat()).isEqualTo(ModoSunat.PRODUCCION);

        e.volverABeta();
        assertThat(e.modoSunat()).isEqualTo(ModoSunat.BETA);
    }

    @Test
    @DisplayName("Volver a cargar reinicia lo que se sabía del certificado anterior")
    void recargarReinicia() {
        var e = empresa();
        e.cargarCertificado("MODDATOS", AHORA);
        e.anotarVerificacionDeCertificado(AHORA, "CN=VIEJO", HOY.plusYears(1));
        e.cargarCertificado("MODDATOS", AHORA.plusSeconds(5));
        assertThat(e.certificado().sujeto()).isNull();
        assertThat(e.certificado().verificadoEn()).isNull();
    }
}
