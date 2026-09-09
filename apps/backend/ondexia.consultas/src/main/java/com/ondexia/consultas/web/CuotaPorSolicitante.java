package com.ondexia.consultas.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cuántas consultas puede hacer una misma identidad por hora.
 *
 * <h2>Por qué existe</h2>
 *
 * <p>La cadena crítica 2 de la auditoría del 2026-09-01: con autorregistro
 * abierto, cualquiera con una cuenta de Cognito podía usar esta función como
 * proxy gratuito hacia los proveedores de pago del padrón. Se cerró apagando el
 * autorregistro. El primer producto lo necesita encendido —nadie va a esperar a
 * que un operador le cree la cuenta—, y esta es una de las puertas que la
 * auditoría pedía antes de reabrirlo (doc 12 §6.2). El alta de una empresa
 * consulta el RUC una vez; cinco por hora sobran para equivocarse de número y
 * volver, y no sirven para recorrer el padrón.
 *
 * <h2>Lo que es y lo que no es</h2>
 *
 * <p>Es un contador <strong>en memoria de la instancia</strong>. Esta función no
 * tiene base de datos —vive fuera de la VPC a propósito, DT-19— y una tabla
 * externa para contar costaría más de lo que protege. Con la concurrencia
 * reservada de la función acotada a dos instancias ({@code consultas.tf}), un
 * abuso puede como mucho duplicar la cuota, y una instancia reciclada la
 * reinicia; el techo duro lo pone el throttling de la ruta en la pasarela y la
 * alarma de invocaciones. Es una defensa por capas, y esta capa se declara
 * como lo que es. Si algún día hace falta una cuota exacta entre instancias, el
 * sitio es una tabla de DynamoDB de nivel gratuito, y esta clase cambia por
 * dentro sin tocar el controlador.
 *
 * <p>Lo verifica {@code CuotaPorSolicitanteTest} y, de punta a punta,
 * {@code CuotaDeConsultasTest}.
 */
public final class CuotaPorSolicitante {

    static final Duration VENTANA = Duration.ofHours(1);

    /** Por encima de esto se barren las identidades que ya no cuentan. */
    private static final int IDENTIDADES_ANTES_DE_BARRER = 10_000;

    private final int maximoPorVentana;
    private final Clock reloj;
    private final Map<String, Deque<Instant>> consultasPorSolicitante = new ConcurrentHashMap<>();

    public CuotaPorSolicitante(int maximoPorVentana, Clock reloj) {
        if (maximoPorVentana < 1) {
            throw new IllegalArgumentException("La cuota por hora tiene que ser al menos 1");
        }
        this.maximoPorVentana = maximoPorVentana;
        this.reloj = reloj;
    }

    /**
     * Anota una consulta de {@code solicitante}, o la rechaza si ya agotó la hora.
     *
     * @throws CuotaAgotada con los segundos que faltan para la siguiente
     */
    public void registrar(String solicitante) {
        Instant ahora = reloj.instant();
        if (consultasPorSolicitante.size() > IDENTIDADES_ANTES_DE_BARRER) {
            barrer(ahora);
        }
        Deque<Instant> suyas = consultasPorSolicitante.computeIfAbsent(
                solicitante, s -> new ArrayDeque<>());
        synchronized (suyas) {
            descartarVencidas(suyas, ahora);
            if (suyas.size() >= maximoPorVentana) {
                long segundos = Duration.between(ahora, suyas.peekFirst().plus(VENTANA))
                        .toSeconds();
                throw new CuotaAgotada(maximoPorVentana, Math.max(1, segundos));
            }
            suyas.addLast(ahora);
        }
    }

    private static void descartarVencidas(Deque<Instant> suyas, Instant ahora) {
        Instant limite = ahora.minus(VENTANA);
        while (!suyas.isEmpty() && suyas.peekFirst().isBefore(limite)) {
            suyas.pollFirst();
        }
    }

    private void barrer(Instant ahora) {
        Instant limite = ahora.minus(VENTANA);
        consultasPorSolicitante.entrySet().removeIf(entrada -> {
            synchronized (entrada.getValue()) {
                Instant ultima = entrada.getValue().peekLast();
                return ultima == null || ultima.isBefore(limite);
            }
        });
    }

    /** La identidad ya consultó todo lo que podía en esta hora. */
    public static final class CuotaAgotada extends RuntimeException {

        private final long segundosParaReintentar;

        CuotaAgotada(int maximo, long segundosParaReintentar) {
            super("Se alcanzó el máximo de " + maximo + " consultas de RUC por hora. "
                    + "Vuelva a intentarlo más tarde.");
            this.segundosParaReintentar = segundosParaReintentar;
        }

        public long segundosParaReintentar() {
            return segundosParaReintentar;
        }
    }
}
