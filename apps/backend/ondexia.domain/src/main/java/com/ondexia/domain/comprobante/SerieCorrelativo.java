package com.ondexia.domain.comprobante;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.util.Objects;
import java.util.UUID;

/**
 * Una serie de comprobantes y el último número que emitió.
 *
 * <p>Es la pieza delicada del módulo. SUNAT no tolera correlativos duplicados y
 * penaliza los saltos, así que esta clase es la <strong>fuente única</strong> del
 * número de cada comprobante (DTE §5, F-02).
 *
 * <h2>Por qué el número vive aquí y no en una SEQUENCE</h2>
 *
 * <p>Una secuencia de PostgreSQL no es transaccional: entrega el número fuera de
 * la transacción y no lo devuelve si esta se deshace. Cualquier reversión —una
 * validación que falla después de pedir el número, un error de red— dejaría un
 * hueco permanente en la numeración. Un campo con {@code SELECT … FOR UPDATE} sí
 * participa de la transacción.
 *
 * <p>El precio es que los concurrentes se serializan en esa fila, y es
 * exactamente lo que se quiere: emitir dos comprobantes con el mismo número es
 * mucho peor que emitir el segundo unos milisegundos más tarde.
 */
public class SerieCorrelativo {

    /**
     * Tope de SUNAT para el correlativo: ocho dígitos.
     *
     * <p>Se comprueba aquí y no solo en la base porque al desbordar hay que dar
     * una instrucción —crear otra serie—, y una violación de restricción no la
     * da.
     */
    private static final long MAXIMO = 99_999_999L;

    private final UUID id;
    private final UUID empresaId;
    private final UUID sucursalId;
    private final TipoDocumento tipoDocumento;
    private final String serie;

    private long ultimoNumero;
    private boolean activa;

    public SerieCorrelativo(UUID id, UUID empresaId, UUID sucursalId,
            TipoDocumento tipoDocumento, String serie) {
        this.id = Objects.requireNonNull(id, "id");
        this.empresaId = Objects.requireNonNull(empresaId, "empresaId");
        // No es opcional como en almacén: SUNAT relaciona la serie con el anexo
        // que emite, y una serie sin establecimiento no se puede declarar.
        this.sucursalId = Objects.requireNonNull(sucursalId, "sucursalId");
        this.tipoDocumento = Objects.requireNonNull(tipoDocumento, "tipoDocumento");
        this.serie = tipoDocumento.validarSerie(serie);
        this.ultimoNumero = 0;
        this.activa = true;
    }

    /** Reconstrucción desde persistencia. No revalida: ver {@code MapeadoresIdentidad}. */
    public SerieCorrelativo(UUID id, UUID empresaId, UUID sucursalId,
            TipoDocumento tipoDocumento, String serie, long ultimoNumero, boolean activa) {
        this.id = id;
        this.empresaId = empresaId;
        this.sucursalId = sucursalId;
        this.tipoDocumento = tipoDocumento;
        this.serie = serie;
        this.ultimoNumero = ultimoNumero;
        this.activa = activa;
    }

    public UUID id() {
        return id;
    }

    public UUID empresaId() {
        return empresaId;
    }

    public UUID sucursalId() {
        return sucursalId;
    }

    public TipoDocumento tipoDocumento() {
        return tipoDocumento;
    }

    public String serie() {
        return serie;
    }

    /** Último número <strong>emitido</strong>. Una serie nueva vale 0. */
    public long ultimoNumero() {
        return ultimoNumero;
    }

    public boolean estaActiva() {
        return activa;
    }

    /**
     * Avanza el correlativo y devuelve el número recién asignado.
     *
     * <p><strong>Se llama al confirmar el documento, jamás al abrir un
     * borrador.</strong> Un borrador abandonado con número asignado es un hueco
     * permanente en la numeración, y los huecos hay que justificarlos ante SUNAT.
     *
     * <p>Este método por sí solo no impide duplicados: quien lo invoque tiene que
     * haber cargado la fila con bloqueo de escritura. Ver
     * {@link SerieCorrelativoRepositorio#bloquearParaEmitir}.
     */
    public long asignarSiguienteNumero() {
        if (!activa) {
            throw new ReglaDeNegocioViolada(
                    "serie_inactiva",
                    "La serie " + serie + " está desactivada y no puede emitir comprobantes.");
        }

        if (ultimoNumero >= MAXIMO) {
            throw new ReglaDeNegocioViolada(
                    "serie_agotada",
                    "La serie " + serie + " llegó a " + MAXIMO
                            + ", que es el máximo que admite SUNAT. Crea otra serie para "
                            + "seguir emitiendo.");
        }

        return ++ultimoNumero;
    }

    /**
     * Desactivar no borra ni reinicia. La serie sigue nombrando a todos los
     * comprobantes que emitió, y su último número queda como estaba: reactivarla
     * debe continuar donde se quedó, no volver a empezar.
     */
    /**
     * Coloca el punto de partida al dar de alta, de un salto.
     *
     * <p>Tabla de bajas de la auditoría 2026-09-01: el alta llamaba a
     * {@link #asignarSiguienteNumero()} en un bucle de hasta cien millones de
     * vueltas para «avanzar el agregado» hasta el número inicial. El argumento
     * —que hubiera una sola puerta para mover el correlativo— era bueno; el
     * precio era una petición que podía tardar segundos con un número alto, y
     * que cualquiera con permiso de crear series podía provocar a voluntad.
     *
     * <p>La única puerta sigue siendo esta clase. Lo que se reparte es el
     * momento: {@code iniciarEn} solo vale sobre una serie recién creada que
     * todavía no ha emitido nada; después, solo se avanza de uno en uno.
     */
    public void iniciarEn(long numeroInicial) {
        if (ultimoNumero != 0) {
            throw new ReglaDeNegocioViolada(
                    "serie_ya_iniciada",
                    "La serie " + serie + " ya emitió comprobantes y no admite un número inicial.");
        }
        if (numeroInicial < 0 || numeroInicial > MAXIMO) {
            throw new ReglaDeNegocioViolada(
                    "numero_inicial_invalido",
                    "El número inicial tiene que estar entre 0 y " + MAXIMO + ".");
        }
        this.ultimoNumero = numeroInicial;
    }

    public void desactivar() {
        this.activa = false;
    }

    public void activar() {
        this.activa = true;
    }

    /** {@code F001-00001234}, tal como se imprime. */
    public String numeroCompleto(long numero) {
        return serie + "-" + String.format("%08d", numero);
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof SerieCorrelativo otra && id.equals(otra.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
