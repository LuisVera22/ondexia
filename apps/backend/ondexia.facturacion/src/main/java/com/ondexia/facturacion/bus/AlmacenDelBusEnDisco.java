package com.ondexia.facturacion.bus;

import com.ondexia.facturacion.PropiedadesEmision;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * El bus como un directorio, para el ensayo local contra la beta (doc 14 §6):
 * se copia el {@code .pfx} a {@code certificados/<ruc>.pfx}, se escribe
 * {@code credenciales/<ruc>.json} a mano, y las órdenes llegan por
 * {@code POST /emision/ordenes}. Lo que el Emisor deja se puede abrir con
 * cualquier editor.
 */
@Component
@Profile("!aws")
public class AlmacenDelBusEnDisco implements AlmacenDelBus {

    private static final Logger LOG = LoggerFactory.getLogger(AlmacenDelBusEnDisco.class);

    private final Path raiz;

    public AlmacenDelBusEnDisco(PropiedadesEmision propiedades) {
        this.raiz = Path.of(propiedades.directorioLocal()).toAbsolutePath().normalize();
        LOG.info("Bus de emisión en disco: {}", raiz);
    }

    @Override
    public Optional<byte[]> leer(String clave) {
        Path archivo = ruta(clave);
        if (!Files.exists(archivo)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(archivo));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + archivo, e);
        }
    }

    @Override
    public void escribir(String clave, byte[] contenido, String tipoContenido) {
        Path archivo = ruta(clave);
        try {
            Files.createDirectories(archivo.getParent());
            Files.write(archivo, contenido);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir " + archivo, e);
        }
    }

    @Override
    public void borrar(String clave) {
        try {
            Files.deleteIfExists(ruta(clave));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo borrar " + clave, e);
        }
    }

    /** Dentro de la raíz siempre: una clave con {@code ..} no sale del directorio. */
    private Path ruta(String clave) {
        Path destino = raiz.resolve(clave).normalize();
        if (!destino.startsWith(raiz)) {
            throw new IllegalArgumentException("Clave fuera del bus: " + clave);
        }
        return destino;
    }
}
