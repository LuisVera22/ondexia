package com.ondexia.infrastructure.salida.emision;

import com.ondexia.domain.comprobante.BusDeEmision;
import com.ondexia.domain.comprobante.ClavesDelBus;
import com.ondexia.domain.comprobante.OrdenDeEmision;
import com.ondexia.domain.comprobante.ResultadoDeEmision;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * El bus para desarrollo y pruebas: un mapa en memoria en lugar del bucket.
 *
 * <p>Hace lo mismo que {@link BusDeEmisionS3} sin S3, y además deja mirar y
 * fingir: las pruebas leen las órdenes publicadas y depositan resultados como si
 * el Emisor hubiera respondido; el controlador de desarrollo hace lo mismo para
 * ensayar contra un Emisor local (doc 14 §6).
 *
 * <p>Las URL que devuelve no funcionan: apuntan a un host que no existe. Es a
 * propósito. En local no hay bucket al que subir el certificado, y una URL que
 * pareciera funcionar haría creer que sí.
 */
@Component
@Profile("!aws")
public class BusDeEmisionEnMemoria implements BusDeEmision {

    private final List<OrdenDeEmision> ordenes = new CopyOnWriteArrayList<>();
    private final Map<String, ResultadoDeEmision> resultados = new ConcurrentHashMap<>();
    private final Set<String> objetos = ConcurrentHashMap.newKeySet();

    @Override
    public void publicar(OrdenDeEmision orden) {
        ordenes.add(orden);
        objetos.add(ClavesDelBus.pendiente(orden.empresaId(), orden.id()));
    }

    @Override
    public Optional<ResultadoDeEmision> resultadoDe(UUID empresaId, UUID ordenId) {
        return Optional.ofNullable(resultados.get(ClavesDelBus.resultado(empresaId, ordenId)));
    }

    @Override
    public boolean existe(String clave) {
        return objetos.contains(clave);
    }

    @Override
    public String urlDeDescarga(String clave, Duration validez) {
        return "https://bus.ondexia.local/" + clave + "?descarga&validez=" + validez.toSeconds();
    }

    @Override
    public String urlDeSubida(String clave, String tipoContenido, Duration validez) {
        return "https://bus.ondexia.local/" + clave + "?subida&tipo=" + tipoContenido;
    }

    // ── Lo que solo existe para fingir al Emisor ─────────────────────────────

    /** Las órdenes publicadas desde el arranque, en orden. */
    public List<OrdenDeEmision> ordenes() {
        return List.copyOf(ordenes);
    }

    /** Lo que el Emisor habría dejado en {@code resultados/}. */
    public void depositarResultado(ResultadoDeEmision resultado) {
        resultados.put(ClavesDelBus.resultado(resultado.empresaId(), resultado.ordenId()), resultado);
        if (resultado.claveXml() != null) {
            objetos.add(resultado.claveXml());
        }
        if (resultado.claveCdr() != null) {
            objetos.add(resultado.claveCdr());
        }
    }

    /** Como si el navegador hubiera subido el objeto por la URL prefirmada. */
    public void fingirSubida(String clave) {
        objetos.add(clave);
    }

    /** Lo contrario: el objeto ya no está. Para que una prueba parta de cero. */
    public void olvidarObjeto(String clave) {
        objetos.remove(clave);
    }
}
