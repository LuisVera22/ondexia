package com.ondexia.admin.configuracion;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * El paquete de autoridades certificadoras de RDS, puesto donde el controlador
 * de PostgreSQL puede leerlo.
 *
 * <p>Copia de la clase homónima de {@code ondexia.api}: el panel no depende de
 * la API en tiempo de ejecución (doc 09 §6.1) y no hay un módulo de
 * infraestructura compartido. Si se toca una, se toca la otra.
 *
 * <h2>Por qué se copia a disco</h2>
 *
 * <p>{@code sslrootcert} del controlador pgjdbc abre un <em>archivo</em>. El
 * prefijo {@code classpath:} solo lo entiende {@code sslfactoryarg} con
 * {@code SingleCertValidatingFactory}, y ese nombre no es casual: carga un
 * certificado, y el paquete de RDS trae más de cien —una raíz por región y por
 * generación—. La validación de la auditoría del 2026-09-07 encontró que la
 * versión anterior apuntaba con {@code classpath:} a {@code sslrootcert}: el
 * controlador habría buscado un archivo llamado literalmente así y toda conexión
 * en AWS habría fallado al arrancar.
 *
 * <p>Se copia una vez por proceso al directorio temporal —en Lambda solo
 * {@code /tmp} es escribible, y {@code java.io.tmpdir} apunta ahí— y se entrega
 * la ruta. La copia es atómica: se escribe en un nombre provisional y se
 * renombra, para que dos hilos que arranquen a la vez no lean un archivo a medio
 * escribir.
 *
 * <h2>De dónde sale el archivo</h2>
 *
 * <p>{@code apps/backend/certificados/rds-global-bundle.pem}, descargado de
 * {@code https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem}. Lo
 * empaquetan la API y el panel desde ese único sitio (ver el {@code <resources>}
 * de cada pom) para que no haya dos copias que se desactualicen por separado.
 * AWS avisa con meses cuando rota una raíz; actualizarlo es reemplazar el
 * archivo.
 *
 * <p>Lo verifica {@code CertificadoRaizRdsTest}: que el recurso existe, que se
 * extrae, que contiene certificados X.509 válidos y que extraerlo dos veces da la
 * misma ruta.
 */
public final class CertificadoRaizRds {

    static final String RECURSO = "certificados/rds-global-bundle.pem";
    private static final String NOMBRE_EN_DISCO = "ondexia-rds-global-bundle.pem";

    private CertificadoRaizRds() {
    }

    /** Ruta del paquete en disco, extrayéndolo del artefacto la primera vez. */
    public static Path ruta() {
        return extraerEn(Path.of(System.getProperty("java.io.tmpdir")));
    }

    static Path extraerEn(Path directorio) {
        Path destino = directorio.resolve(NOMBRE_EN_DISCO);
        if (Files.exists(destino)) {
            return destino;
        }
        try (InputStream recurso = CertificadoRaizRds.class.getClassLoader()
                .getResourceAsStream(RECURSO)) {
            if (recurso == null) {
                throw new IllegalStateException("El artefacto no trae " + RECURSO
                        + ": sin el paquete de CA de RDS no se puede verificar la base de datos");
            }
            Path provisional = Files.createTempFile(directorio, NOMBRE_EN_DISCO, ".parcial");
            Files.copy(recurso, provisional, StandardCopyOption.REPLACE_EXISTING);
            Files.move(provisional, destino, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            return destino;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo extraer el paquete de CA de RDS", e);
        }
    }
}
