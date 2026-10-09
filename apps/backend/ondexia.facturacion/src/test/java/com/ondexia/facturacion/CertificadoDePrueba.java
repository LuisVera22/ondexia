package com.ondexia.facturacion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * El PKCS#12 autofirmado con el que se ejercita la firma. Se <b>genera</b> en
 * cada compilación en vez de venir versionado, y eso no es un capricho de
 * estilo: {@code .gitignore} excluye {@code *.pfx} sin excepciones porque un
 * certificado que entra al historial obliga a reescribirlo o a revocarlo. Un
 * archivo de prueba con ese nombre solo puede existir fuera del repositorio, y
 * mientras estuvo en el disco de quien lo creó las nueve pruebas que lo cargan
 * pasaban en local y fallaban en el CI sin que el archivo apareciera en ningún
 * diff. La única forma de que la prueba diga lo mismo en las dos máquinas es
 * que el material lo produzca la propia compilación.
 *
 * <p>Se usa {@code keytool} del JDK que está ejecutando las pruebas —no openssl,
 * que no está garantizado en un runner— así que el PKCS#12 sale con los
 * algoritmos por omisión de ese JDK y no con los heredados que openssl elegía.
 * No hay biblioteca en el árbol que construya un X.509 desde Java: bcpkix no
 * es dependencia de nadie y añadirla solo para esto sería peor.
 *
 * <p>El sujeto imita al de un certificado tributario real —{@code OU} lleva el
 * RUC, que es de donde el Emisor lo lee— porque
 * {@code FirmadorDeComprobanteTest} comprueba precisamente esa lectura.
 */
public final class CertificadoDePrueba {

    /** La contraseña es pública a propósito: el certificado no protege nada. */
    public static final String CLAVE = "prueba";

    public static final String SUJETO =
            "CN=CERTIFICADO DE PRUEBA ONDEXIA, OU=" + Ordenes.RUC + ", O=COMERCIAL DEMO S.A.C., C=PE";

    private static volatile byte[] contenido;

    private CertificadoDePrueba() {
    }

    /** Los bytes del {@code .pfx}. Se genera una vez por ejecución de la JVM. */
    public static byte[] bytes() {
        byte[] cache = contenido;
        if (cache == null) {
            synchronized (CertificadoDePrueba.class) {
                cache = contenido;
                if (cache == null) {
                    cache = generar();
                    contenido = cache;
                }
            }
        }
        return cache.clone();
    }

    private static byte[] generar() {
        try {
            // target/ y no un temporal del sistema: si una prueba falla, el
            // certificado con el que falló sigue ahí para poder mirarlo.
            Path destino = Path.of("target", "certificado-prueba.pfx");
            Files.createDirectories(destino.getParent());
            Files.deleteIfExists(destino);

            Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
            var comando = List.of(keytool.toString(),
                    "-genkeypair",
                    "-alias", "ondexia",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-sigalg", "SHA256withRSA",
                    // Diez años: FirmadorDeComprobanteTest comprueba que la
                    // fecha de vencimiento se lee, y la compara con 2030.
                    "-validity", "3650",
                    "-dname", SUJETO,
                    "-storetype", "PKCS12",
                    "-keystore", destino.toString(),
                    "-storepass", CLAVE,
                    "-keypass", CLAVE);

            var proceso = new ProcessBuilder(comando).redirectErrorStream(true).start();
            String salida = new String(proceso.getInputStream().readAllBytes());
            if (!proceso.waitFor(60, TimeUnit.SECONDS)) {
                proceso.destroyForcibly();
                throw new IllegalStateException("keytool no terminó al generar el certificado de prueba.");
            }
            if (proceso.exitValue() != 0) {
                throw new IllegalStateException(
                        "keytool falló al generar el certificado de prueba (código " + proceso.exitValue()
                                + "):\n" + salida);
            }
            return Files.readAllBytes(destino);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el certificado de prueba.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido al generar el certificado de prueba.", e);
        }
    }
}
