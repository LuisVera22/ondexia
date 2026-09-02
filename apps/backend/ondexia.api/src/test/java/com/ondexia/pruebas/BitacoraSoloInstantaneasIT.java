package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.consultas.CondicionDomicilio;
import com.ondexia.domain.consultas.DatosDeRuc;
import com.ondexia.domain.consultas.EstadoContribuyente;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.infrastructure.seguridad.ContextoDePrueba;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * La bitácora no acepta agregados del dominio, solo instantáneas.
 *
 * <p>Hallazgo M8 de la auditoría 2026-09-01. {@code RegistroDeAuditoria} acepta
 * {@code Object}, y eso invita a pasarle la entidad entera «para que quede
 * todo». {@code Empresa} lleva {@code usuarioSol} y el ARN del certificado de
 * SUNAT; serializarla dejaría esos campos en {@code datos_despues} para siempre,
 * en una tabla que se lee desde soporte.
 *
 * <p>Hoy todos los casos de uso pasan un {@code record} escrito a mano. Esta
 * prueba es lo que convierte esa costumbre en una condición.
 */
class BitacoraSoloInstantaneasIT extends PruebaIntegracion {

    private static final UUID EMPRESA = UUID.fromString(EMPRESA_ADMINISTRADA);

    @Autowired
    private RegistroDeAuditoria auditoria;

    @AfterEach
    void limpiar() {
        ContextoDePrueba.limpiar();
    }

    private record Instantanea(String ruc, String razonSocial) {
    }

    @Test
    @Transactional
    @DisplayName("Una instantánea (record) se registra")
    void unaInstantaneaPasa() {
        ContextoDePrueba.comoUsuarioDe(UUID.fromString(USUARIO_DEMO),
                UUID.fromString("00000000-0000-4000-8000-000000000001"), EMPRESA);

        assertThatCode(() -> auditoria.registrarCreacion("prueba_m8", UUID.randomUUID(),
                new Instantanea("20601030013", "ONDEXIA S.A.C.")))
                .doesNotThrowAnyException();
    }

    /**
     * El caso que motiva la prueba: la entidad entera, con lo que lleva dentro.
     *
     * <p>Se construye una {@code Empresa} real desde el padrón, como haría el
     * alta, y se intenta guardarla tal cual. Tiene que rebotar ANTES de
     * serializar: si llegara a la base, {@code usuarioSol} iría con ella.
     */
    @Test
    @Transactional
    @DisplayName("Un agregado del dominio se rechaza antes de serializarse")
    void unAgregadoNoPasa() {
        ContextoDePrueba.comoUsuarioDe(UUID.fromString(USUARIO_DEMO),
                UUID.fromString("00000000-0000-4000-8000-000000000001"), EMPRESA);

        Empresa empresa = Empresa.registrar(UUID.randomUUID(), UUID.randomUUID(),
                new DatosDeRuc(new Ruc("20601030013"), "ONDEXIA S.A.C.",
                        EstadoContribuyente.ACTIVO, CondicionDomicilio.HABIDO,
                        "AV. AREQUIPA 100", null, null, null, "LIMA",
                        false, false, null, Instant.now()));

        assertThatThrownBy(() -> auditoria.registrarCreacion("empresa", empresa.id(), empresa))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instantáneas");
    }
}
