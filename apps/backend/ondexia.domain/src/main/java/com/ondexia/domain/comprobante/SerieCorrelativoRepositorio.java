package com.ondexia.domain.comprobante;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida de series.
 *
 * <p>Ninguna operación recibe la empresa: la tabla está protegida por Row Level
 * Security, igual que {@code almacen}. Lo que sí distingue a este puerto es que
 * tiene <strong>dos formas de leer la misma fila</strong>, y confundirlas produce
 * correlativos duplicados.
 */
public interface SerieCorrelativoRepositorio {

    Optional<SerieCorrelativo> buscarPorId(UUID id);

    /** Para validar duplicados al dar de alta. Lectura sin bloqueo. */
    Optional<SerieCorrelativo> buscarPorSerie(TipoDocumento tipoDocumento, String serie);

    List<SerieCorrelativo> listar();

    /**
     * Carga la serie <strong>bloqueando la fila para escritura</strong>.
     *
     * <p>Este es el método que hay que usar para emitir, y el único. Traduce a un
     * {@code SELECT … FOR UPDATE}: la primera transacción que lo ejecuta retiene
     * la fila y las demás esperan ahí hasta que confirme o se deshaga. Sin ese
     * bloqueo, dos peticiones simultáneas leen el mismo {@code ultimo_numero},
     * ambas suman uno y emiten el mismo comprobante dos veces.
     *
     * <p>El fallo no se reproduce a mano ni aparece en pruebas secuenciales —solo
     * bajo concurrencia real, que en producción es la hora punta del mostrador—.
     * Por eso el bloqueo es explícito en la firma del puerto y no un detalle
     * escondido en el adaptador: quien lea esta interfaz tiene que ver que hay
     * dos formas de leer y que no son intercambiables.
     *
     * @return vacío si la serie no existe en la empresa activa
     */
    Optional<SerieCorrelativo> bloquearParaEmitir(UUID id);

    SerieCorrelativo guardar(SerieCorrelativo serie);
}
