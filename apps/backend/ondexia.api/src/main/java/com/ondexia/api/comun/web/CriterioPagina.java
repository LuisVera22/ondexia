package com.ondexia.api.comun.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Parámetros de paginación y orden de un listado.
 *
 * <p>Se declara una vez y se recibe en los controladores con
 * {@code @ParameterObject}, de modo que en el contrato aparecen como parámetros
 * de consulta sueltos y no como un objeto anidado.
 *
 * @param ordenarPor nombre de la propiedad. Se valida contra una lista blanca
 *                   en {@link #aPageable}, ver el motivo allí
 */
public record CriterioPagina(
        @Schema(description = "Índice de página, empezando en 0", defaultValue = "0")
        @Min(0) Integer pagina,

        @Schema(description = "Elementos por página", defaultValue = "25")
        @Min(1) @Max(TAMANO_MAXIMO) Integer tamano,

        @Schema(description = "Propiedad por la que ordenar")
        String ordenarPor,

        @Schema(defaultValue = "ASC")
        Direccion direccion) {

    /**
     * Tope duro del tamaño de página.
     *
     * <p>Sin él, {@code ?tamano=1000000} es una denegación de servicio de un
     * solo carácter: la consulta materializa la tabla entera en memoria de la
     * Lambda, que tiene 1 GB.
     */
    public static final int TAMANO_MAXIMO = 200;

    private static final int TAMANO_POR_DEFECTO = 25;

    public enum Direccion {
        ASC,
        DESC
    }

    /** Rellena los ausentes: los parámetros de consulta llegan nulos si no se envían. */
    public CriterioPagina {
        pagina = pagina == null ? 0 : pagina;
        tamano = tamano == null ? TAMANO_POR_DEFECTO : tamano;
        direccion = direccion == null ? Direccion.ASC : direccion;
    }

    /**
     * Traduce a {@code Pageable}, validando la propiedad de orden contra una
     * lista blanca.
     *
     * <p><strong>La lista blanca no es celo.</strong> {@code ordenarPor} llega
     * del cliente y termina dentro de un {@code ORDER BY} generado. Sin
     * validar, un nombre de propiedad inexistente produce un 500 en el mejor
     * caso; y permite además ordenar por columnas que el listado no expone
     * —{@code secretArnCertificado}, por ejemplo— lo que filtra información por
     * comparación: ordenando por un campo oculto se deduce su valor relativo
     * fila a fila.
     *
     * @param permitidas propiedades que este listado admite. Nunca vacía
     * @param porDefecto la que se usa si el cliente no pide ninguna
     */
    public Pageable aPageable(Set<String> permitidas, String porDefecto) {
        String propiedad = (ordenarPor == null || ordenarPor.isBlank()) ? porDefecto : ordenarPor;

        if (!permitidas.contains(propiedad)) {
            throw new com.ondexia.api.comun.error.SolicitudInvalidaException(
                    "orden_invalido",
                    "No se puede ordenar por '" + propiedad + "'. Admitidos: "
                            + String.join(", ", permitidas.stream().sorted().toList()));
        }

        Sort.Direction sentido = direccion == Direccion.DESC
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        return PageRequest.of(pagina, tamano, Sort.by(sentido, propiedad));
    }
}
