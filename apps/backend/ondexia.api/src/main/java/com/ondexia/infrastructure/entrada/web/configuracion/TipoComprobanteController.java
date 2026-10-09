package com.ondexia.infrastructure.entrada.web.configuracion;

import com.ondexia.application.comprobante.TiposDeComprobante;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Qué tipos de comprobante emite la empresa.
 *
 * <p>Es la parte de «Comprobantes» que entra en Configuración. Lo demás —emitir,
 * anular, ver el estado ante SUNAT— pertenece al módulo de ventas y llegará con
 * C1; esta pantalla no lo insinúa siquiera, para no prometer botones que no
 * existen.
 */
@RestController
@RequestMapping("/api/v1/configuracion/comprobantes")
@Tag(name = "Comprobantes", description = "Tipos de comprobante que emite la empresa")
public class TipoComprobanteController {

    private final TiposDeComprobante tipos;

    public TipoComprobanteController(TiposDeComprobante tipos) {
        this.tipos = tipos;
    }

    @Operation(
            summary = "Los cinco tipos del catálogo 01 con su estado",
            description = """
                    Vienen todos, habilitados y deshabilitados: la pantalla es un panel de \
                    interruptores, no una lista de los activos.""")
    @RequierePermiso(modulo = "configuracion.comprobante", accion = "consultar")
    @GetMapping
    public List<RespuestaTipo> listar() {
        return tipos.listar().stream().map(RespuestaTipo::desde).toList();
    }

    @Operation(
            summary = "Habilita o deshabilita un tipo",
            description = """
                    No se puede deshabilitar un tipo con series activas: la pantalla diría una \
                    cosa y el sistema haría otra. Responde 400 con `tipo_con_series_activas`.""")
    @RequierePermiso(modulo = "configuracion.comprobante", accion = "editar")
    @PutMapping("/{codigo}")
    public RespuestaTipo cambiarEstado(
            @PathVariable String codigo, @Valid @RequestBody PeticionEstado peticion) {
        return RespuestaTipo.desde(tipos.cambiarEstado(codigo, peticion.emite()));
    }

    public record PeticionEstado(
            @NotNull(message = "Indica si la empresa emite este tipo.")
            Boolean emite) {
    }

    /**
     * @param seriesActivas cuántas series vivas cuelgan del tipo. Es lo que
     *                      explica por qué un interruptor no se deja apagar
     */
    public record RespuestaTipo(
            String codigo, String nombre, boolean emite, long seriesActivas) {

        static RespuestaTipo desde(TiposDeComprobante.EstadoDeTipo estado) {
            return new RespuestaTipo(
                    estado.tipo().codigo(),
                    estado.tipo().nombre(),
                    estado.emite(),
                    estado.seriesActivas());
        }
    }
}
