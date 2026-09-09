package com.ondexia.infrastructure.entrada.web.ventas;

import com.ondexia.application.ventas.Clientes;
import com.ondexia.domain.ventas.Cliente;
import com.ondexia.domain.ventas.TipoDocumentoIdentidad;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ventas/clientes")
@Tag(name = "Clientes", description = "Adquirentes identificados: a quién se le emite")
public class ClienteController {

    private final Clientes clientes;

    public ClienteController(Clientes clientes) {
        this.clientes = clientes;
    }

    @Operation(summary = "Lista o busca clientes", description = "Sin `q`, todos. Con `q`, por nombre o documento, hasta 50.")
    @RequierePermiso(modulo = "ventas.cliente", accion = "consultar")
    @GetMapping
    public List<RespuestaCliente> listar(@RequestParam(required = false) String q) {
        return clientes.buscar(q).stream().map(RespuestaCliente::desde).toList();
    }

    @Operation(summary = "Tipos de documento admitidos (catálogo 06 de SUNAT)")
    @RequierePermiso(modulo = "ventas.cliente", accion = "consultar")
    @GetMapping("/tipos-documento")
    public List<Opcion> tiposDeDocumento() {
        return Arrays.stream(TipoDocumentoIdentidad.values())
                .map(t -> new Opcion(t.name(), t.codigo(), t.nombre())).toList();
    }

    @Operation(summary = "Ficha de un cliente")
    @RequierePermiso(modulo = "ventas.cliente", accion = "consultar")
    @GetMapping("/{id}")
    public RespuestaCliente obtener(@PathVariable UUID id) {
        return RespuestaCliente.desde(clientes.obtener(id));
    }

    @Operation(
            summary = "Registra un cliente",
            description = "Con RUC puede traer la atestación de la consulta al padrón: la razón social y el domicilio salen de ella.")
    @RequierePermiso(modulo = "ventas.cliente", accion = "registrar")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaCliente registrar(@Valid @RequestBody PeticionNuevo peticion) {
        return RespuestaCliente.desde(clientes.registrar(peticion.tipoDocumento(),
                peticion.numeroDocumento(), peticion.datos(), peticion.atestacion()));
    }

    @Operation(summary = "Actualiza contacto y nombre. El documento no cambia")
    @RequierePermiso(modulo = "ventas.cliente", accion = "editar")
    @PutMapping("/{id}")
    public RespuestaCliente actualizar(@PathVariable UUID id, @Valid @RequestBody PeticionEdicion peticion) {
        return RespuestaCliente.desde(clientes.actualizar(id, peticion.datos()));
    }

    @Operation(summary = "Vuelve a verificar el RUC contra el padrón con una atestación nueva")
    @RequierePermiso(modulo = "ventas.cliente", accion = "editar")
    @PostMapping("/{id}/verificacion")
    public RespuestaCliente verificar(@PathVariable UUID id, @Valid @RequestBody PeticionVerificacion peticion) {
        return RespuestaCliente.desde(clientes.verificar(id, peticion.atestacion()));
    }

    @Operation(summary = "Activa o desactiva. Nunca se borra: los comprobantes lo referencian")
    @RequierePermiso(modulo = "ventas.cliente", accion = "desactivar")
    @PutMapping("/{id}/estado")
    public RespuestaCliente cambiarEstado(@PathVariable UUID id, @Valid @RequestBody PeticionEstado peticion) {
        return RespuestaCliente.desde(clientes.cambiarEstado(id, peticion.activo()));
    }

    public record PeticionNuevo(
            @NotNull(message = "Indica el tipo de documento.") TipoDocumentoIdentidad tipoDocumento,
            @NotBlank(message = "El número de documento es obligatorio.") @Size(max = 15) String numeroDocumento,
            @Size(max = 300) String nombre,
            @Size(max = 300) String direccion,
            @Size(max = 200) String correo,
            @Size(max = 30) String telefono,
            String atestacion) {

        Clientes.Datos datos() {
            return new Clientes.Datos(nombre, direccion, correo, telefono);
        }
    }

    public record PeticionEdicion(
            @NotBlank(message = "El nombre es obligatorio.") @Size(max = 300) String nombre,
            @Size(max = 300) String direccion,
            @Size(max = 200) String correo,
            @Size(max = 30) String telefono) {

        Clientes.Datos datos() {
            return new Clientes.Datos(nombre, direccion, correo, telefono);
        }
    }

    public record PeticionVerificacion(@NotBlank(message = "Falta la verificación del RUC.") String atestacion) {
    }

    public record PeticionEstado(@NotNull(message = "Indica si el cliente queda activo.") Boolean activo) {
    }

    public record Opcion(String codigo, String codigoSunat, String nombre) {
    }

    public record RespuestaCliente(UUID id, String tipoDocumento, String tipoDocumentoNombre,
            String numeroDocumento, String nombre, String direccion, String correo, String telefono,
            Instant verificadoEn, boolean admiteFactura, boolean activo) {

        static RespuestaCliente desde(Cliente c) {
            return new RespuestaCliente(c.id(), c.tipoDocumento().name(), c.tipoDocumento().nombre(),
                    c.numeroDocumento(), c.nombre(), c.direccion(), c.correo(), c.telefono(),
                    c.verificadoEn(), c.admiteFactura(), c.estaActivo());
        }
    }
}
