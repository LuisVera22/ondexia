package com.ondexia.facturacion.sunat;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Recepción SOAP: contar bytes reales antes de entregarlos al acumulador del JDK. */
final class ReceptorDeSoapAcotado implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> acumulador = HttpResponse.BodySubscribers.ofByteArray();
    private Flow.Subscription suscripcion;
    private int recibidos;
    private boolean terminado;

    @Override
    public CompletionStage<byte[]> getBody() {
        return acumulador.getBody();
    }

    @Override
    public void onSubscribe(Flow.Subscription nueva) {
        suscripcion = nueva;
        acumulador.onSubscribe(nueva);
    }

    @Override
    public void onNext(List<ByteBuffer> bloques) {
        if (terminado) return;
        // ClienteSunatHttpTest: incluye bloques sin Content-Length y el valor
        // exacto. Restar evita desbordamiento; no copiar el bloque que excede.
        for (ByteBuffer bloque : bloques) {
            if (bloque.remaining() > LectorDeRespuestaSunat.LIMITE_SOAP - recibidos) {
                terminado = true;
                suscripcion.cancel();
                acumulador.onError(new RespuestaExcesiva());
                return;
            }
            recibidos += bloque.remaining();
        }
        acumulador.onNext(bloques);
    }

    @Override
    public void onError(Throwable fallo) {
        if (!terminado) {
            terminado = true;
            acumulador.onError(fallo);
        }
    }

    @Override
    public void onComplete() {
        if (!terminado) {
            terminado = true;
            acumulador.onComplete();
        }
    }

    static final class RespuestaExcesiva extends IOException {
        private static final long serialVersionUID = 1L;

        RespuestaExcesiva() {
            super("La respuesta supera el límite de recepción.");
        }
    }
}
