package com.ondexia.infrastructure.entrada.web.configuracion;

import com.ondexia.application.comprobante.Series;
import com.ondexia.domain.comprobante.SerieCorrelativo;
import com.ondexia.domain.comprobante.TipoDocumento;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Series de comprobante por establecimiento y tipo de documento.
 *
 * <p>No hay {@code DELETE} y no hay forma de escribir el correlativo. Ambas
 * ausencias son deliberadas y están explicadas en {@link Series}: una serie
 * nombra a todos los comprobantes que emitió, y un endpoint para «corregir» el
 * número acabaría produciendo duplicados.
 */
@RestController
@RequestMapping("/api/v1/configuracion/series")
@Tag(name = "Series", description = "Series de comprobante y su correlativo")
public class SerieController {

    private final Series series;

    public SerieController(Series series) {
        this.series = series;
    }

    @Operation(
            summary = "Catálogo 01 de SUNAT",
            description = """
                    Los tipos de documento que se pueden numerar, con la letra que SUNAT \
                    exige al principio de la serie. Alimenta el desplegable del alta.""")
    @RequierePermiso(modulo = "configuracion.serie", accion = "consultar")
    @GetMapping("/tipos-documento")
    public List<RespuestaTipoDocumento> tiposDocumento() {
        return Arrays.stream(TipoDocumento.values())
                .map(RespuestaTipoDocumento::desde)
                .toList();
    }

    @Operation(summary = "Lista las series, activas e inactivas")
    @RequierePermiso(modulo = "configuracion.serie", accion = "consultar")
    @GetMapping
    public List<RespuestaSerie> listar() {
        return series.listar().stream().map(RespuestaSerie::desde).toList();
    }

    @Operation(
            summary = "Da de alta una serie",
            description = """
                    La primera letra la fija SUNAT según el tipo: F para factura, B para \
                    boleta, T para guía. El `numeroInicial` es para quien migra desde otro \
                    sistema y no puede volver a numerar desde el 1; se fija aquí y no se \
                    vuelve a tocar.""")
    @RequierePermiso(modulo = "configuracion.serie", accion = "registrar")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaSerie registrar(@Valid @RequestBody PeticionNueva peticion) {
        return RespuestaSerie.desde(series.registrar(
                peticion.sucursalId(),
                peticion.tipoDocumento(),
                peticion.serie(),
                peticion.numeroInicial() == null ? 0L : peticion.numeroInicial()));
    }

    @Operation(
            summary = "Activa o desactiva una serie",
            description = """
                    Lo único editable. Desactivar no borra ni reinicia: el último número \
                    queda como estaba, de modo que reactivarla continúa donde se quedó.""")
    @RequierePermiso(modulo = "configuracion.serie", accion = "editar")
    @PutMapping("/{id}/estado")
    public RespuestaSerie cambiarEstado(
            @PathVariable UUID id, @Valid @RequestBody PeticionEstado peticion) {
        return RespuestaSerie.desde(series.cambiarEstado(id, peticion.activa()));
    }

    /**
     * @param numeroInicial opcional. Ausente o {@code 0} significa que la serie
     *                      empieza de cero y el primer comprobante será el 1
     */
    public record PeticionNueva(
            @NotNull(message = "El establecimiento es obligatorio.")
            UUID sucursalId,

            @NotBlank(message = "El tipo de documento es obligatorio.")
            @Pattern(regexp = "0[13789]|NV",
                    message = "Tipo de documento fuera del catálogo 01 de SUNAT (o NV, nota de venta).")
            String tipoDocumento,

            @NotBlank(message = "La serie es obligatoria.")
            @Pattern(regexp = "[A-Za-z][A-Za-z0-9]{3}",
                    message = "La serie son cuatro caracteres: una letra y tres alfanuméricos.")
            String serie,

            @Min(value = 0, message = "El número inicial no puede ser negativo.")
            Long numeroInicial) {
    }

    public record PeticionEstado(
            @NotNull(message = "Indica si la serie queda activa.")
            Boolean activa) {
    }

    /**
     * @param siguienteNumero lo que de verdad quiere ver quien mira esta
     *                        pantalla. Se calcula aquí y no en el cliente para
     *                        que no haya dos sitios donde equivocarse en el ±1
     */
    public record RespuestaSerie(
            UUID id,
            UUID sucursalId,
            String tipoDocumento,
            String tipoDocumentoNombre,
            String serie,
            long ultimoNumero,
            String siguienteNumero,
            boolean activa) {

        static RespuestaSerie desde(SerieCorrelativo serie) {
            return new RespuestaSerie(
                    serie.id(),
                    serie.sucursalId(),
                    serie.tipoDocumento().codigo(),
                    serie.tipoDocumento().nombre(),
                    serie.serie(),
                    serie.ultimoNumero(),
                    serie.numeroCompleto(serie.ultimoNumero() + 1),
                    serie.estaActiva());
        }
    }

    public record RespuestaTipoDocumento(String codigo, String nombre) {

        static RespuestaTipoDocumento desde(TipoDocumento tipo) {
            return new RespuestaTipoDocumento(tipo.codigo(), tipo.nombre());
        }
    }
}
