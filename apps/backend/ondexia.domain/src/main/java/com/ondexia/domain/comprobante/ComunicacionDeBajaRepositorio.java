package com.ondexia.domain.comprobante;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComunicacionDeBajaRepositorio {

    Optional<ComunicacionDeBaja> buscarPorId(UUID id);

    /** Las últimas, de la más reciente a la más antigua. */
    List<ComunicacionDeBaja> listarRecientes(int maximo);

    /**
     * Las que siguen en camino: en cola o esperando que se consulte su ticket.
     * Es lo que el planificador recorre.
     */
    List<ComunicacionDeBaja> enCurso();

    /**
     * Las que incluyen ese documento, en cualquier estado. Sirve para no darlo
     * de baja dos veces.
     */
    List<ComunicacionDeBaja> queIncluyen(UUID documentoId);

    /**
     * El correlativo siguiente para ese día. Lo calcula la base porque dos
     * comunicaciones del mismo día con el mismo número serían, para SUNAT, el
     * mismo documento.
     */
    int siguienteNumeroDelDia(LocalDate fechaDeGeneracion);

    ComunicacionDeBaja guardar(ComunicacionDeBaja comunicacion);
}
