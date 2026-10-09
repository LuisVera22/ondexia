package com.ondexia.domain.ventas;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Un turno de caja: desde que alguien la abre con un monto inicial hasta que
 * alguien la cierra declarando lo que contó.
 *
 * <h2>Lo que el arqueo hace y lo que no hace</h2>
 *
 * <p>Al cerrar, el sistema calcula lo que debería haber por cada forma de pago
 * —el monto inicial más lo cobrado en efectivo, y lo cobrado en las demás— y la
 * persona declara lo que hay. La diferencia se guarda; <strong>no se
 * corrige</strong>. Un arqueo que cuadra a la fuerza no sirve para nada, y uno
 * que deja la diferencia escrita es el único que permite ver un patrón
 * (doc 12 §3.4).
 *
 * <p>Una sesión cerrada es inmutable: lo garantiza un disparador en la base
 * además de esta clase, porque los privilegios se pueden volver a conceder y el
 * código se puede saltar.
 */
public class SesionCaja {

    public enum Estado { ABIERTA, CERRADA }

    private final UUID id;
    private final UUID cajaId;
    private final UUID abiertaPor;
    private final Instant abiertaEn;
    private final BigDecimal montoInicial;
    private UUID cerradaPor;
    private Instant cerradaEn;
    private Estado estado;
    private Map<FormaDePago, BigDecimal> declarado;
    private Map<FormaDePago, BigDecimal> calculado;

    private SesionCaja(UUID id, UUID cajaId, UUID abiertaPor, Instant abiertaEn,
            BigDecimal montoInicial) {
        this.id = Objects.requireNonNull(id, "id");
        this.cajaId = Objects.requireNonNull(cajaId, "cajaId");
        this.abiertaPor = Objects.requireNonNull(abiertaPor, "abiertaPor");
        this.abiertaEn = Objects.requireNonNull(abiertaEn, "abiertaEn");
        this.montoInicial = montoInicial;
        this.estado = Estado.ABIERTA;
        this.declarado = Map.of();
        this.calculado = Map.of();
    }

    /** Abre la caja. El monto inicial es lo que hay en el cajón antes de vender. */
    public static SesionCaja abrir(UUID id, UUID cajaId, UUID usuarioId, BigDecimal montoInicial,
            Instant ahora) {
        return new SesionCaja(id, cajaId, usuarioId, ahora, exigirImporte(montoInicial,
                "monto_inicial_invalido", "El monto inicial no puede ser negativo."));
    }

    /** Reconstrucción desde persistencia. Solo lo usa el adaptador. */
    public SesionCaja(UUID id, UUID cajaId, UUID abiertaPor, Instant abiertaEn,
            BigDecimal montoInicial, UUID cerradaPor, Instant cerradaEn, Estado estado,
            Map<FormaDePago, BigDecimal> declarado, Map<FormaDePago, BigDecimal> calculado) {
        this.id = id;
        this.cajaId = cajaId;
        this.abiertaPor = abiertaPor;
        this.abiertaEn = abiertaEn;
        this.montoInicial = montoInicial;
        this.cerradaPor = cerradaPor;
        this.cerradaEn = cerradaEn;
        this.estado = estado;
        this.declarado = copiar(declarado);
        this.calculado = copiar(calculado);
    }

    /**
     * Cierra el turno.
     *
     * @param cobrado   lo cobrado durante la sesión por forma de pago, según las
     *                  ventas registradas. Lo aporta quien conoce las ventas; la
     *                  sesión no las consulta
     * @param declarado lo que la persona contó, por forma de pago. Las formas que
     *                  no declare se toman como cero: no declarar es declarar que
     *                  no hay
     */
    public void cerrar(UUID usuarioId, Map<FormaDePago, BigDecimal> cobrado,
            Map<FormaDePago, BigDecimal> declarado, Instant ahora) {
        if (estado == Estado.CERRADA) {
            throw new ReglaDeNegocioViolada("sesion_ya_cerrada", "Esta sesión de caja ya está cerrada.");
        }
        Objects.requireNonNull(usuarioId, "usuarioId");

        var calculadoFinal = new EnumMap<FormaDePago, BigDecimal>(FormaDePago.class);
        var declaradoFinal = new EnumMap<FormaDePago, BigDecimal>(FormaDePago.class);
        for (FormaDePago forma : FormaDePago.values()) {
            BigDecimal cobradoEn = valorDe(cobrado, forma);
            // El efectivo arranca con lo que habia en el cajon; lo demas, en cero.
            calculadoFinal.put(forma, forma == FormaDePago.EFECTIVO
                    ? montoInicial.add(cobradoEn) : cobradoEn);
            declaradoFinal.put(forma, exigirImporte(valorDe(declarado, forma),
                    "declarado_invalido", "Lo declarado en " + forma.nombre().toLowerCase()
                            + " no puede ser negativo."));
        }

        this.calculado = Map.copyOf(calculadoFinal);
        this.declarado = Map.copyOf(declaradoFinal);
        this.cerradaPor = usuarioId;
        this.cerradaEn = Objects.requireNonNull(ahora, "ahora");
        this.estado = Estado.CERRADA;
    }

    /** Declarado menos calculado, por forma de pago. Positivo: sobra; negativo: falta. */
    public Map<FormaDePago, BigDecimal> diferencia() {
        var diferencia = new EnumMap<FormaDePago, BigDecimal>(FormaDePago.class);
        for (FormaDePago forma : FormaDePago.values()) {
            diferencia.put(forma, valorDe(declarado, forma).subtract(valorDe(calculado, forma)));
        }
        return Map.copyOf(diferencia);
    }

    public UUID id() {
        return id;
    }

    public UUID cajaId() {
        return cajaId;
    }

    public UUID abiertaPor() {
        return abiertaPor;
    }

    public Instant abiertaEn() {
        return abiertaEn;
    }

    public BigDecimal montoInicial() {
        return montoInicial;
    }

    public UUID cerradaPor() {
        return cerradaPor;
    }

    public Instant cerradaEn() {
        return cerradaEn;
    }

    public Estado estado() {
        return estado;
    }

    public boolean estaAbierta() {
        return estado == Estado.ABIERTA;
    }

    public Map<FormaDePago, BigDecimal> declarado() {
        return declarado;
    }

    public Map<FormaDePago, BigDecimal> calculado() {
        return calculado;
    }

    private static BigDecimal valorDe(Map<FormaDePago, BigDecimal> mapa, FormaDePago forma) {
        if (mapa == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal valor = mapa.get(forma);
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private static Map<FormaDePago, BigDecimal> copiar(Map<FormaDePago, BigDecimal> mapa) {
        return mapa == null ? Map.of() : Map.copyOf(mapa);
    }

    private static BigDecimal exigirImporte(BigDecimal importe, String codigo, String mensaje) {
        BigDecimal valor = importe == null ? BigDecimal.ZERO : importe;
        if (valor.signum() < 0) {
            throw new ReglaDeNegocioViolada(codigo, mensaje);
        }
        return valor;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof SesionCaja sesion && id.equals(sesion.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
