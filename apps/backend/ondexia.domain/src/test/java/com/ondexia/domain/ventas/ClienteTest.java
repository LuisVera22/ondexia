package com.ondexia.domain.ventas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El documento del adquirente se valida al entrar (doc 12 §3.2 y §4.3). */
class ClienteTest {

    private static Cliente nuevo(TipoDocumentoIdentidad tipo, String numero) {
        return new Cliente(UUID.randomUUID(), UUID.randomUUID(), tipo, numero, "Rosa Quispe",
                null, null, null);
    }

    @Test
    @DisplayName("El DNI son ocho dígitos, y el error señala al campo")
    void dni() {
        assertThat(nuevo(TipoDocumentoIdentidad.DNI, " 45678912 ").numeroDocumento())
                .isEqualTo("45678912");

        assertThatThrownBy(() -> nuevo(TipoDocumentoIdentidad.DNI, "4567891"))
                .isInstanceOfSatisfying(ReglaDeNegocioViolada.class,
                        error -> assertThat(error.getCampo()).isEqualTo("numeroDocumento"));
    }

    @Test
    @DisplayName("Un RUC se valida con dígito verificador antes de cualquier consulta")
    void ruc() {
        assertThat(nuevo(TipoDocumentoIdentidad.RUC, "20601030013").admiteFactura()).isTrue();
        assertThatThrownBy(() -> nuevo(TipoDocumentoIdentidad.RUC, "20601030014"))
                .isInstanceOf(ReglaDeNegocioViolada.class);
        assertThat(nuevo(TipoDocumentoIdentidad.DNI, "45678912").admiteFactura()).isFalse();
    }

    @Test
    @DisplayName("El catálogo 06 se resuelve por código de SUNAT o por nombre")
    void catalogo() {
        assertThat(TipoDocumentoIdentidad.porCodigo("6")).isEqualTo(TipoDocumentoIdentidad.RUC);
        assertThat(TipoDocumentoIdentidad.porCodigo("dni")).isEqualTo(TipoDocumentoIdentidad.DNI);
        assertThatThrownBy(() -> TipoDocumentoIdentidad.porCodigo("0"))
                .as("el cliente sin documento no es una fila")
                .isInstanceOf(ReglaDeNegocioViolada.class);
    }

    @Test
    @DisplayName("El padrón manda sobre lo tecleado, y solo para un RUC")
    void verificacion() {
        var cliente = nuevo(TipoDocumentoIdentidad.RUC, "20601030013");
        var ahora = Instant.parse("2026-09-07T12:00:00Z");

        cliente.verificarConPadron("EMPRESA VERIFICADA S.A.C.", "AV. AREQUIPA 100", ahora);

        assertThat(cliente.nombre()).isEqualTo("EMPRESA VERIFICADA S.A.C.");
        assertThat(cliente.direccion()).isEqualTo("AV. AREQUIPA 100");
        assertThat(cliente.verificadoEn()).isEqualTo(ahora);

        assertThatThrownBy(() -> nuevo(TipoDocumentoIdentidad.DNI, "45678912")
                .verificarConPadron("X", null, ahora))
                .isInstanceOf(ReglaDeNegocioViolada.class);
    }

    @Test
    @DisplayName("El correo se normaliza y se valida; lo vacío queda a null")
    void correo() {
        var cliente = nuevo(TipoDocumentoIdentidad.DNI, "45678912");
        cliente.actualizar("Rosa Quispe", "  ", " Rosa@Correo.PE ", null);

        assertThat(cliente.direccion()).isNull();
        assertThat(cliente.correo()).isEqualTo("rosa@correo.pe");
        assertThatThrownBy(() -> cliente.actualizar("Rosa", null, "sin-arroba", null))
                .isInstanceOfSatisfying(ReglaDeNegocioViolada.class,
                        error -> assertThat(error.getCampo()).isEqualTo("correo"));
    }
}
