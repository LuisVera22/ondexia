package com.ondexia.application.comprobante;

import com.ondexia.domain.comprobante.SerieCorrelativo;
import com.ondexia.domain.comprobante.SerieCorrelativoRepositorio;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Da el siguiente número de una serie. El flujo F-02 del DTE, en veinte líneas.
 *
 * <h2>Por qué la propagación es MANDATORY y no la habitual</h2>
 *
 * <p>Con {@code REQUIRED} —lo normal— llamar a este servicio sin transacción
 * abierta crearía una, confirmaría el número y volvería. Después, si la creación
 * del comprobante fallara en su propia transacción, el número ya estaría
 * consumido: <strong>un hueco permanente en la numeración</strong>, que es
 * precisamente lo que este diseño existe para impedir.
 *
 * <p>{@code MANDATORY} convierte ese error en una excepción inmediata y ruidosa
 * en vez de en un hueco silencioso que nadie descubre hasta que SUNAT pregunta
 * por él. Quien pida un correlativo tiene que estar ya dentro de la transacción
 * que va a crear el documento; si esa transacción se deshace, el número vuelve a
 * estar disponible porque nunca llegó a confirmarse.
 *
 * <p>Dicho de otro modo: la firma del método impide el mal uso, en lugar de
 * confiar en que un comentario lo advierta.
 *
 * <h2>El otro requisito, que no se ve aquí</h2>
 *
 * <p>El número se asigna <strong>al confirmar el documento, jamás al abrir un
 * borrador</strong>. Un borrador abandonado con número asignado es el mismo
 * hueco por otra vía, y esa parte no la puede imponer esta clase: la impone quien
 * decide en qué momento la llama.
 */
@Service
public class AsignadorDeCorrelativo {

    private final SerieCorrelativoRepositorio series;

    public AsignadorDeCorrelativo(SerieCorrelativoRepositorio series) {
        this.series = series;
    }

    /**
     * Reserva el siguiente número de la serie y lo devuelve.
     *
     * @return el número asignado, ya sumado y guardado dentro de la transacción
     *         en curso
     * @throws org.springframework.transaction.IllegalTransactionStateException si
     *         no hay una transacción abierta
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long siguienteNumero(UUID serieId) {
        // bloquearParaEmitir es un SELECT ... FOR UPDATE: la primera transacción
        // que llega retiene la fila y las demás esperan aquí. Es lo que hace que
        // dos cajas emitiendo a la vez no puedan leer el mismo último número.
        var serie = series.bloquearParaEmitir(serieId)
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "serie_no_encontrada", "La serie no existe."));

        long numero = serie.asignarSiguienteNumero();
        series.guardar(serie);
        return numero;
    }

    /** {@code F001-00001234}, tal como se imprime en el comprobante. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String siguienteNumeroCompleto(UUID serieId) {
        var serie = series.bloquearParaEmitir(serieId)
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "serie_no_encontrada", "La serie no existe."));

        long numero = serie.asignarSiguienteNumero();
        series.guardar(serie);
        return serie.numeroCompleto(numero);
    }

    /** Solo lectura, para mostrar en pantalla cuál sería el siguiente. */
    public long ultimoNumeroDe(SerieCorrelativo serie) {
        return serie.ultimoNumero();
    }
}
