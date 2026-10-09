package com.ondexia.infrastructure.salida.marca;

import com.ondexia.domain.marca.AlmacenDeMarca;
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

    private final Map<String, ObjetoDeMarca> objetos = new ConcurrentHashMap<>();

    @Override
    public AutorizacionDeSubida autorizarSubida(String clave, String tipoContenido, long bytes) {
        // No se registra nada: firmar no sube. Registrar aquí sería el atajo que
        // convierte esta clase en un doble que miente.
        return new AutorizacionDeSubida(
                "http://almacen-de-desarrollo.local/" + clave, clave, Duration.ofMinutes(5));
    }

    /** Lo que en producción hace el navegador. Las pruebas lo llaman a mano. */
    public void simularSubida(String clave, String tipoContenido, long bytes) {
        objetos.put(clave, new ObjetoDeMarca(bytes, tipoContenido));
    }

    @Override
    public Optional<ObjetoDeMarca> describir(String clave) {
        return Optional.ofNullable(objetos.get(clave));
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
