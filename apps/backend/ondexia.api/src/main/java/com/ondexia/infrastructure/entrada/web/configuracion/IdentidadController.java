package com.ondexia.infrastructure.entrada.web.configuracion;

import com.ondexia.application.configuracion.Identidad;
import com.ondexia.domain.marca.AlmacenDeMarca;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Identidad visual: los logos de la empresa.
 *
 * <p>La subida va en dos llamadas —autorizar y confirmar— porque el archivo
 * <strong>no pasa por esta API</strong>: el navegador lo sube directo al almacén
 * con una URL firmada. Eso esquiva el límite de 10 MB de la pasarela, no consume
 * tiempo de función, y funciona sin salida a internet desde la Lambda, porque
 * firmar es un cálculo local.
 *
 * <p>El precio de ese diseño es que hay que confirmar: entre la firma y la
 * subida el cliente puede mandar otra cosa, y el servidor comprueba con el
 * almacén qué llegó antes de guardar nada.
 */
@RestController
@RequestMapping("/api/v1/configuracion/identidad")
@Tag(name = "Identidad visual", description = "Logos de la empresa")
public class IdentidadController {

    private final Identidad identidad;

    public IdentidadController(Identidad identidad) {
        this.identidad = identidad;
    }

    @Operation(summary = "Los tres logos con su URL pública, si la tienen")
    @RequierePermiso(modulo = "configuracion.identidad", accion = "consultar")
    @GetMapping
    public List<RespuestaLogo> consultar() {
        return identidad.consultar().stream().map(RespuestaLogo::desde).toList();
    }

    @Operation(
            summary = "Autoriza una subida",
            description = """
                    Devuelve una URL firmada a la que hay que hacer `PUT` con el archivo como \
                    cuerpo y el mismo `Content-Type` que se declaró aquí — va dentro de la firma, \
                    así que subir otra cosa da 403.

                    No cambia nada todavía: hasta confirmar, es como si no hubiera pasado.""")
    @RequierePermiso(modulo = "configuracion.identidad", accion = "editar")
    @PostMapping("/{logo}/subida")
    public RespuestaAutorizacion autorizar(
            @PathVariable String logo, @Valid @RequestBody PeticionSubida peticion) {
        var autorizacion = identidad.autorizarSubida(
                logo, peticion.tipoContenido(), peticion.bytes());

        return RespuestaAutorizacion.desde(autorizacion);
    }

    @Operation(
            summary = "Confirma la subida",
            description = """
                    Comprueba contra el almacén qué llegó de verdad —tipo y tamaño— y solo \
                    entonces guarda la referencia. Responde 400 con `subida_no_completada` si \
                    el archivo no está.

                    El logo anterior no se borra: los comprobantes ya emitidos lo referencian.""")
    @RequierePermiso(modulo = "configuracion.identidad", accion = "editar")
    @PutMapping("/{logo}")
    public List<RespuestaLogo> confirmar(
            @PathVariable String logo, @Valid @RequestBody PeticionConfirmacion peticion) {
        return identidad.confirmarSubida(logo, peticion.clave()).stream()
                .map(RespuestaLogo::desde)
                .toList();
    }

    @Operation(
            summary = "Quita el logo",
            description = """
                    Aquí sí se borra el archivo: significa «no quiero logo», no «cambié de \
                    logo». Reemplazar conserva el anterior; quitar lo retira.""")
    @RequierePermiso(modulo = "configuracion.identidad", accion = "editar")
    @DeleteMapping("/{logo}")
    @ResponseStatus(HttpStatus.OK)
    public List<RespuestaLogo> quitar(@PathVariable String logo) {
        return identidad.quitar(logo).stream().map(RespuestaLogo::desde).toList();
    }

    /**
     * @param bytes tamaño declarado. Se valida antes de firmar para no autorizar
     *              una subida que se va a rechazar, y otra vez al confirmar
     *              contra lo que el almacén recibió
     */
    public record PeticionSubida(
            @NotBlank(message = "Indica el tipo de archivo.")
            String tipoContenido,

            @NotNull(message = "Indica el tamaño del archivo.")
            @Min(value = 1, message = "El archivo está vacío.")
            Long bytes) {
    }

    public record PeticionConfirmacion(
            @NotBlank(message = "Falta la referencia del archivo subido.")
            String clave) {
    }

    /**
     * @param url         {@code null} si no hay archivo cargado
     * @param maximoBytes lo que la pantalla usa para avisar antes de subir, en
     *                    vez de dejar que el usuario espere y falle
     */
    public record RespuestaLogo(String logo, String nombre, String url, long maximoBytes) {

        static RespuestaLogo desde(Identidad.EstadoDeLogo estado) {
            return new RespuestaLogo(
                    estado.logo().columna(),
                    estado.logo().nombre(),
                    estado.url(),
                    estado.maximoBytes());
        }
    }

    public record RespuestaAutorizacion(String url, String clave, long validaSegundos) {

        static RespuestaAutorizacion desde(AlmacenDeMarca.AutorizacionDeSubida autorizacion) {
            return new RespuestaAutorizacion(
                    autorizacion.url(),
                    autorizacion.clave(),
                    autorizacion.validaPor().toSeconds());
        }
    }
}
