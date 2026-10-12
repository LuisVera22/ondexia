package com.ondexia.infrastructure.salida.marca;

import com.ondexia.domain.marca.AlmacenDeMarca;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Almacén de marca para desarrollo y pruebas.
 *
 * <h2>Por qué existe, y por qué no es una simulación cualquiera</h2>
 *
 * <p>La Fase 0 del DTE §10.3 exige poder desarrollar sin nada desplegado en AWS,
 * y las pruebas de integración no pueden depender de un bucket real: serían
 * lentas, necesitarían credenciales y fallarían por motivos ajenos al código.
 *
 * <p>Lo importante es <strong>qué imita y qué no</strong>. Imita la mecánica que
 * el caso de uso necesita —firmar, describir, borrar— pero <em>no</em> imita que
 * la subida ocurra sola: hay que llamar a {@link #simularSubida} explícitamente,
 * igual que en producción hay que subir de verdad. Así una prueba que confirme
 * sin subir falla, que es exactamente lo que pasaría contra S3.
 *
 * <p>Un doble que diera por subido todo lo firmado escondería el caso más
 * probable en producción: la subida que no llegó a completarse.
 */
@Component
@Profile("!aws")
public class AlmacenDeMarcaEnMemoria implements AlmacenDeMarca {

    private final Map<String, ArchivoDeMarca> objetos = new ConcurrentHashMap<>();
    private record ArchivoDeMarca(ObjetoDeMarca datos, byte[] contenido) { }

    @Override
    public AutorizacionDeSubida autorizarSubida(String clave, String tipoContenido, long bytes) {
        // No se registra nada: firmar no sube. Registrar aquí sería el atajo que
        // convierte esta clase en un doble que miente.
        return new AutorizacionDeSubida(
                "http://almacen-de-desarrollo.local/" + clave, clave, Duration.ofMinutes(5));
    }

    /** Lo que en producción hace el navegador. Las pruebas lo llaman a mano. */
    public void simularSubida(String clave, String tipoContenido, long bytes) {
        crear(clave, new ArchivoDeMarca(new ObjetoDeMarca(bytes, tipoContenido), null));
    }

    /** Carga bytes explícitos; no deduce contenido válido a partir del MIME. */
    public void simularSubida(String clave, String tipoContenido, byte[] contenido) {
        crear(clave, new ArchivoDeMarca(new ObjetoDeMarca(contenido.length, tipoContenido), contenido.clone()));
    }

    private void crear(String clave, ArchivoDeMarca archivo) {
        if (objetos.putIfAbsent(clave, archivo) != null) {
            throw new ReglaDeNegocioViolada("archivo_ya_existe",
                    "La subida no puede reemplazar un archivo existente. Solicita una nueva autorización.");
        }
    }

    @Override
    public Optional<ObjetoDeMarca> describir(String clave) {
        return Optional.ofNullable(objetos.get(clave)).map(ArchivoDeMarca::datos);
    }

    @Override
    public void validarImagen(String clave, String tipoContenido, long maximoBytes) {
        var archivo = objetos.get(clave);
        var contenido = archivo == null ? null : archivo.contenido();
        ValidadorDeImagen.validar(contenido == null ? new byte[0] : contenido, tipoContenido, maximoBytes);
    }

    @Override
    public void eliminar(String clave) {
        objetos.remove(clave);
    }

    @Override
    public String urlPublica(String clave) {
        return "http://cdn-de-desarrollo.local/" + clave;
    }
}
