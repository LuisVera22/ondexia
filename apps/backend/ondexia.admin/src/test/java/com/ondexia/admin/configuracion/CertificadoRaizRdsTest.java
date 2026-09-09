package com.ondexia.admin.configuracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * El control «la conexión a RDS verifica el certificado del servidor» depende
 * de que este archivo llegue al artefacto y sea legible por el controlador.
 * Estas pruebas son las que hacen que un {@code .gitignore} demasiado ancho o
 * un {@code <resources>} mal apuntado se vean en el CI y no en el arranque en
 * la nube.
 */
class CertificadoRaizRdsTest {

    @Test
    @DisplayName("el paquete de CA de RDS viaja en el artefacto y tiene más de cien raíces")
    void elPaqueteViajaEnElArtefacto() throws Exception {
        try (InputStream recurso = getClass().getClassLoader()
                .getResourceAsStream(CertificadoRaizRds.RECURSO)) {
            assertThat(recurso).as("recurso " + CertificadoRaizRds.RECURSO).isNotNull();

            Collection<? extends Certificate> raices =
                    CertificateFactory.getInstance("X.509").generateCertificates(recurso);

            // AWS publica una raíz por región y generación; hoy son más de cien.
            // Si baja de cincuenta, alguien reemplazó el archivo por uno regional.
            assertThat(raices).hasSizeGreaterThan(50);
            assertThat(raices)
                    .allSatisfy(c -> assertThat(((X509Certificate) c).getSubjectX500Principal().getName())
                            .contains("Amazon RDS"));
        }
    }

    @Test
    @DisplayName("se extrae a disco una sola vez y la ruta es estable")
    void seExtraeUnaVez(@TempDir Path directorio) throws Exception {
        Path primera = CertificadoRaizRds.extraerEn(directorio);
        Path segunda = CertificadoRaizRds.extraerEn(directorio);

        assertThat(primera).isEqualTo(segunda).exists();
        assertThat(Files.size(primera)).isGreaterThan(100_000);
        // Sin archivos provisionales olvidados: la copia terminó con el renombrado.
        try (var contenido = Files.list(directorio)) {
            assertThat(contenido).containsExactly(primera);
        }
    }
}
