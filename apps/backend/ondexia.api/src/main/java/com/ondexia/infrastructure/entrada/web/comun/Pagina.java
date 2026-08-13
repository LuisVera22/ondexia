package com.ondexia.infrastructure.entrada.web.comun;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Una página de resultados, en el formato que sale por HTTP.
 *
 * <p><strong>Por qué no se devuelve el {@code Page} de Spring Data.</strong> Su
 * serialización arrastra la estructura interna del framework —{@code pageable},
 * {@code sort}, {@code numberOfElements}, {@code first}, {@code empty}— y todo
 * eso acaba en el contrato OpenAPI y, de ahí, en el cliente Angular generado.
 * El resultado es un cliente que expone conceptos de Spring Data a un frontend
 * que no sabe qué son, y un contrato que cambia si algún día se cambia de
 * biblioteca de persistencia.
 *
 * <p>Spring Boot avisa de esto en el log al serializar un {@code Page}
 * directamente, precisamente porque es un error común.
 *
 * @param pagina         índice de la página, empezando en 0
 * @param totalElementos total de elementos, no de la página actual
 */
public record Pagina<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {

    /**
     * Convierte una página de entidades en una de DTO.
     *
     * <p>El mapeador es obligatorio y no hay sobrecarga sin él: devolver
     * entidades JPA por HTTP expone el modelo interno y, con asociaciones
     * perezosas, dispara consultas durante la serialización — fuera ya de la
     * transacción, donde fallan.
     */
    public static <E, D> Pagina<D> de(Page<E> pagina, Function<E, D> mapeador) {
        return new Pagina<>(
                pagina.getContent().stream().map(mapeador).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }

    /** Para listados que no pasan por la base, como catálogos en memoria. */
    public static <T> Pagina<T> unica(List<T> contenido) {
        return new Pagina<>(contenido, 0, contenido.size(), contenido.size(), 1);
    }
}
