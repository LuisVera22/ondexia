package com.ondexia.facturacion.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.ondexia.domain.comprobante.ClavesDelBus;
import com.ondexia.facturacion.FacturacionApplication;
import com.ondexia.facturacion.ProcesadorDeOrdenes;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * La entrada en AWS: S3 invoca esta función por cada objeto que aparece en
 * {@code pendientes/} (doc 14 §2).
 *
 * <p>El evento se lee como un mapa y no con {@code aws-lambda-java-events}: de
 * todo el evento hace falta una cosa, la clave del objeto, y la biblioteca
 * traería sus propias versiones de Jackson para eso.
 *
 * <p>Spring arranca en el bloque estático, como en los otros dos desplegables,
 * para que SnapStart tome la instantánea con el contexto construido. Sin web:
 * aquí no hay HTTP que servir; la ruta de {@code /emision/ordenes} es solo del
 * perfil local.
 */
public class ManejadorDeOrdenes implements RequestHandler<Map<String, Object>, String> {

    private static final ConfigurableApplicationContext CONTEXTO;

    static {
        CONTEXTO = new SpringApplicationBuilder(FacturacionApplication.class)
                .web(WebApplicationType.NONE)
                .run();
    }

    @Override
    public String handleRequest(Map<String, Object> evento, Context contexto) {
        var procesador = CONTEXTO.getBean(ProcesadorDeOrdenes.class);
        var procesadas = new ArrayList<String>();
        for (String clave : clavesDe(evento)) {
            if (!clave.startsWith(ClavesDelBus.PENDIENTES)) {
                contexto.getLogger().log("Se ignora " + clave + ": no es una orden pendiente.");
                continue;
            }
            var resultado = procesador.procesarPendiente(clave);
            procesadas.add(clave + " → " + resultado.estado() + " " + resultado.codigo());
        }
        String resumen = procesadas.isEmpty() ? "Sin órdenes en el evento."
                : String.join("; ", procesadas);
        contexto.getLogger().log(resumen);
        return resumen;
    }

    /** Las claves de {@code Records[].s3.object.key}, ya sin la codificación de URL que S3 aplica. */
    @SuppressWarnings("unchecked")
    static List<String> clavesDe(Map<String, Object> evento) {
        var claves = new ArrayList<String>();
        if (evento == null || !(evento.get("Records") instanceof List<?> registros)) {
            return claves;
        }
        for (Object registro : registros) {
            if (registro instanceof Map<?, ?> r && r.get("s3") instanceof Map<?, ?> s3
                    && s3.get("object") instanceof Map<?, ?> objeto
                    && objeto.get("key") instanceof String clave) {
                claves.add(URLDecoder.decode(clave.replace("+", "%2B"), StandardCharsets.UTF_8));
            }
        }
        return claves;
    }
}
