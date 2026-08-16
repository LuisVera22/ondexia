package com.ondexia.infrastructure.entrada.web.configuracion;

import com.ondexia.application.configuracion.Usuarios;
import com.ondexia.domain.identidad.MiembroEmpresa;
import com.ondexia.domain.identidad.Rol;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
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
 * Usuarios con acceso a la empresa activa.
 *
 * <p>El identificador que manejan estos endpoints es el de la
 * <strong>asignación</strong>, no el de la persona. Es a propósito: la misma
 * persona puede estar en varias empresas con roles distintos, y usar su
 * identificador obligaría a mandar además la empresa en cada llamada —un
 * parámetro que el contexto ya sabe y que, si se pudiera mandar, se podría
 * mandar mal.
 */
@RestController
@RequestMapping("/api/v1/configuracion/usuarios")
@Tag(name = "Usuarios", description = "Quién entra en la empresa, con qué rol y hasta dónde")
public class UsuarioController {

    private final Usuarios usuarios;

    public UsuarioController(Usuarios usuarios) {
        this.usuarios = usuarios;
    }

    @Operation(summary = "Lista los usuarios de la empresa, activos e inactivos")
    @RequierePermiso(modulo = "configuracion.usuario", accion = "consultar")
    @GetMapping
    public List<RespuestaUsuario> listar() {
        return usuarios.listar().stream().map(RespuestaUsuario::desde).toList();
    }

    @Operation(
            summary = "Roles que se pueden asignar",
            description = "Los predefinidos del sistema más los que esta cuenta haya creado.")
    @RequierePermiso(modulo = "configuracion.usuario", accion = "consultar")
    @GetMapping("/roles-asignables")
    public List<RespuestaRolAsignable> rolesAsignables() {
        return usuarios.rolesAsignables().stream().map(RespuestaRolAsignable::desde).toList();
    }

    @Operation(
            summary = "Da de alta a alguien en la empresa",
            description = """
                    No crea su cuenta de acceso: eso lo hace la propia persona al registrarse, \
                    y se vincula sola en su primer ingreso. Hasta entonces figura como invitada.""")
    @RequierePermiso(modulo = "configuracion.usuario", accion = "registrar")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaUsuario invitar(@Valid @RequestBody PeticionNuevo peticion) {
        return RespuestaUsuario.desde(usuarios.invitar(
                peticion.email(), peticion.nombre(), peticion.apellido(), peticion.rolId(),
                peticion.sucursalId()));
    }

    @Operation(
            summary = "Cambia el rol o el alcance",
            description = "Surte efecto en la petición siguiente: el contexto se resuelve en cada una.")
    @RequierePermiso(modulo = "configuracion.usuario", accion = "editar")
    @PutMapping("/{asignacionId}")
    public RespuestaUsuario reasignar(
            @PathVariable UUID asignacionId, @Valid @RequestBody PeticionReasignacion peticion) {
        return RespuestaUsuario.desde(
                usuarios.reasignar(asignacionId, peticion.rolId(), peticion.sucursalId()));
    }

    @Operation(
            summary = "Activa o desactiva a la persona",
            description = """
                    Afecta a toda la cuenta, no solo a esta empresa: es la única forma de \
                    revocar el acceso antes de que caduque su token.""")
    @RequierePermiso(modulo = "configuracion.usuario", accion = "desactivar")
    @PutMapping("/{asignacionId}/estado")
    public RespuestaUsuario cambiarEstado(
            @PathVariable UUID asignacionId, @Valid @RequestBody PeticionEstado peticion) {
        return RespuestaUsuario.desde(usuarios.cambiarEstado(asignacionId, peticion.activo()));
    }

    @Operation(
            summary = "Retira el acceso a esta empresa",
            description = """
                    La persona sigue existiendo en la cuenta y conserva las demás empresas \
                    que tuviera.""")
    @RequierePermiso(modulo = "configuracion.usuario", accion = "desactivar")
    @DeleteMapping("/{asignacionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirar(@PathVariable UUID asignacionId) {
        usuarios.retirar(asignacionId);
    }

    /**
     * @param sucursalId opcional: sin él, la persona alcanza todos los
     *                   establecimientos
     */
    public record PeticionNuevo(
            @NotBlank(message = "El correo es obligatorio.")
            @Email(message = "El correo no tiene un formato válido.")
            @Size(max = 254)
            String email,

            @NotBlank(message = "El nombre es obligatorio.")
            @Size(max = 150)
            String nombre,

            /**
             * Solo se usa si la persona todavía no existe en la cuenta. Añadir a
             * alguien que ya está a otra empresa no le cambia el nombre.
             */
            @NotBlank(message = "El apellido es obligatorio.")
            @Size(max = 150)
            String apellido,

            @NotNull(message = "Hay que indicar un rol.")
            UUID rolId,

            UUID sucursalId) {
    }

    public record PeticionReasignacion(
            @NotNull(message = "Hay que indicar un rol.")
            UUID rolId,

            UUID sucursalId) {
    }

    public record PeticionEstado(
            @NotNull(message = "Indica si la persona queda activa.")
            Boolean activo) {
    }

    /**
     * @param invitado la persona existe en nuestra base pero todavía no completó
     *                 su registro. Es lo que explica que alguien «no pueda
     *                 entrar» estando activo
     */
    public record RespuestaUsuario(
            UUID asignacionId,
            UUID usuarioId,
            String email,
            String nombre,
            boolean activo,
            boolean invitado,
            UUID rolId,
            String rolNombre,
            UUID sucursalId,
            String sucursalNombre,
            boolean todosLosEstablecimientos) {

        static RespuestaUsuario desde(MiembroEmpresa miembro) {
            return new RespuestaUsuario(
                    miembro.asignacionId(),
                    miembro.usuarioId(),
                    miembro.email(),
                    // El listado enseña el nombre completo; partirlo en dos
                    // columnas solo añadiría ruido a una tabla que ya tiene seis.
                    miembro.nombreCompleto(),
                    miembro.activo(),
                    !miembro.cognitoVinculado(),
                    miembro.rolId(),
                    miembro.rolNombre(),
                    miembro.sucursalId(),
                    miembro.sucursalNombre(),
                    miembro.alcanzaTodosLosEstablecimientos());
        }
    }

    /**
     * @param delSistema los predefinidos no se pueden editar. La pantalla lo usa
     *                   para no ofrecer un lápiz que llevaría a un 409
     */
    public record RespuestaRolAsignable(
            UUID id, String codigo, String nombre, String descripcion, boolean delSistema) {

        static RespuestaRolAsignable desde(Rol rol) {
            return new RespuestaRolAsignable(
                    rol.id(), rol.codigo(), rol.nombre(), rol.descripcion(), rol.esDelSistema());
        }
    }
}
