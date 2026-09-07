package com.ondexia.domain.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.Ubigeo;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.consultas.CondicionDomicilio;
import com.ondexia.domain.consultas.DatosDeRuc;
import com.ondexia.domain.consultas.EstadoContribuyente;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que el régimen decide sobre la empresa (doc 12 §3.1): quién se registra,
 * quién puede estar en el Nuevo RUS y quién emite facturas.
 */
class EmpresaRegimenTest {

    private static DatosDeRuc padron(String ruc) {
        return new DatosDeRuc(new Ruc(ruc), "PRUEBA", EstadoContribuyente.ACTIVO,
                CondicionDomicilio.HABIDO, "Av. Prueba 1", new Ubigeo("150101"), "LIMA", "LIMA",
                "LIMA", false, false, null, Instant.now());
    }

    @Test
    @DisplayName("Sin declarar nada, la empresa emite facturas")
    void porOmisionEmiteFacturas() {
        var empresa = Empresa.registrar(UUID.randomUUID(), UUID.randomUUID(),
                padron("20123456786"), null);

        assertThat(empresa.regimen()).isEqualTo(RegimenTributario.OTRO);
        assertThat(empresa.emiteFacturas()).isTrue();
    }

    @Test
    @DisplayName("Una persona natural en el Nuevo RUS no emite facturas, y puede salir de él")
    void nuevoRus() {
        var empresa = Empresa.registrar(UUID.randomUUID(), UUID.randomUUID(),
                padron("10123456781"), RegimenTributario.NUEVO_RUS);

        assertThat(empresa.emiteFacturas()).isFalse();

        empresa.cambiarRegimen(RegimenTributario.OTRO);
        assertThat(empresa.emiteFacturas()).isTrue();
    }

    @Test
    @DisplayName("Una persona jurídica no puede estar en el Nuevo RUS")
    void juridicaNoEstaEnElRus() {
        assertThatThrownBy(() -> Empresa.registrar(UUID.randomUUID(), UUID.randomUUID(),
                padron("20123456786"), RegimenTributario.NUEVO_RUS))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("solo para personas naturales");
    }

    @Test
    @DisplayName("Un RUC 15 no se registra, aunque esté activo y habido")
    void otrosDocumentosNoSeRegistran() {
        assertThatThrownBy(() -> Empresa.registrar(UUID.randomUUID(), UUID.randomUUID(),
                padron("15123456782"), RegimenTributario.OTRO))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .hasMessageContaining("Ondexia admite por ahora");
    }
}
