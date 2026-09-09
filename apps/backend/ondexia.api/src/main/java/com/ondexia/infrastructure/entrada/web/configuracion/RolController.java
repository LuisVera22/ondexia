package com.ondexia.infrastructure.entrada.web.configuracion;

import com.ondexia.application.configuracion.Roles;
import com.ondexia.domain.identidad.Permiso;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * Roles y permisos.
 *
 * <p>Los predefinidos se listan y se duplican; no se editan ni se borran. Los
 * endpoints de modificación responden 409 sobre ellos en vez de 403: no es un
 * problema de quién eres, es que ese rol no se modifica por nadie.
 */
@RestController
@RequestMapping("/api/v1/configuracion/roles")
@Tag(name = "Roles", description = "Roles de la cuenta y su matriz de permisos")
public class RolController {

    private final Roles roles;

    public RolController(Roles roles) {
        this.roles = roles;
    }

    @Operation(summary = "Lista los roles: los predefinidos y los de la cuenta")
    @RequierePermiso(modulo = "configuracion.rol", accion = "consultar")
    @GetMapping
    public List<RespuestaRol> listar() {
        return roles.listar().stream().map(RespuestaRol::desde).toList();
    }

    @Operation(
            summary = "Catálogo de permisos, como árbol de tres niveles",
            description = """
                    Módulo → submódulo → función. Lo define el sistema: es la lista de lo que \
                    Ondexia sabe hacer.

                    Viene ya como árbol y con los nombres para mostrar, de modo que la pantalla \
                    no tiene que saber que `almacen.producto` cuelga de `almacen` ni traducir \
                    códigos. Un módulo nuevo aparece solo, sin tocar el frontend.

                    La autorización es conjuntiva: hacen falta los tres niveles.""")
    @RequierePermiso(modulo = "configuracion.rol", accion = "consultar")
    @GetMapping("/permisos")
    public List<RespuestaModulo> catalogo() {
        return roles.catalogoDePermisos().stream().map(RespuestaModulo::desde).toList();
    }

    @Operation(summary = "Los permisos que tiene un rol")
    @RequierePermiso(modulo = "configuracion.rol", accion = "consultar")
    @GetMapping("/{rolId}/permisos")
    public Set<UUID> permisosDe(@PathVariable UUID rolId) {
        return roles.permisosDe(rolId);
    }

    @Operation(
            summary = "Duplica un rol",
            description = """
                    La copia arrastra los permisos del original y ya es editable. Es la única \
                    forma de partir de un rol predefinido sin modificarlo.""")
    @RequierePermiso(modulo = "configuracion.rol", accion = "registrar")
    @PostMapping("/{rolOrigenId}/duplicado")
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaRol duplicar(
            @PathVariable UUID rolOrigenId, @Valid @RequestBody PeticionDuplicado peticion) {
        var creado = roles.duplicar(rolOrigenId, peticion.nombre());
        return roles.listar().stream()
                .filter(detalle -> detalle.rol().id().equals(creado.id()))
                .map(RespuestaRol::desde)
                .findFirst()
                .orElseThrow();
    }

    @Operation(summary = "Renombra un rol propio")
    @RequierePermiso(modulo = "configuracion.rol", accion = "editar")
    @PutMapping("/{rolId}")
    public RespuestaRol renombrar(
            @PathVariable UUID rolId, @Valid @RequestBody PeticionRenombrado peticion) {
        roles.renombrar(rolId, peticion.nombre(), peticion.descripcion());
        return roles.listar().stream()
                .filter(detalle -> detalle.rol().id().equals(rolId))
                .map(RespuestaRol::desde)
                .findFirst()
                .orElseThrow();
    }

    @Operation(
            summary = "Reemplaza los permisos de un rol",
            description = """
                    El cuerpo es el estado final de la matriz, no un incremento. Surte efecto \
                    en la petición siguiente, sin esperar a que se recicle nada.""")
    @RequierePermiso(modulo = "configuracion.rol", accion = "editar")
    @PutMapping("/{rolId}/permisos")
    public Set<UUID> cambiarPermisos(
            @PathVariable UUID rolId, @Valid @RequestBody PeticionPermisos peticion) {
        roles.cambiarPermisos(rolId, peticion.permisoIds());
        return roles.permisosDe(rolId);
    }

    @Operation(
            summary = "Elimina un rol propio",
            description = "Falla con 409 si algún usuario lo tiene asignado.")
    @RequierePermiso(modulo = "configuracion.rol", accion = "eliminar")
    @DeleteMapping("/{rolId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID rolId) {
        roles.eliminar(rolId);
    }

    public record PeticionDuplicado(
            @NotBlank(message = "El rol nuevo necesita un nombre.")
            @Size(max = 120)
            String nombre) {
    }

    public record PeticionRenombrado(
            @NotBlank(message = "El rol necesita un nombre.")
            @Size(max = 120)
            String nombre,

            @Size(max = 300)
            String descripcion) {
    }

    /**
     * @param permisoIds vacío es válido: un rol sin permisos no puede nada, y es
     *                   una configuración legítima mientras se compone
     */
    public record PeticionPermisos(
            @NotNull(message = "Manda la lista de permisos, aunque esté vacía.")
            Set<UUID> permisoIds) {
    }

    public record RespuestaRol(
            UUID id,
            String codigo,
            String nombre,
            String descripcion,
            boolean delSistema,
            int cantidadPermisos,
            boolean enUso) {

        static RespuestaRol desde(Roles.RolConDetalle detalle) {
            var rol = detalle.rol();
            return new RespuestaRol(
                    rol.id(), rol.codigo(), rol.nombre(), rol.descripcion(),
                    rol.esDelSistema(), detalle.cantidadPermisos(), detalle.enUso());
        }
    }

    /**
     * @param id el permiso del propio módulo — el interruptor de área. Marcarlo
     *           o desmarcarlo es lo que abre o cierra todo lo que cuelga
     */
    public record RespuestaModulo(
            UUID id,
            String codigo,
            String nombre,
            String descripcion,
            List<RespuestaSubmodulo> submodulos) {

        static RespuestaModulo desde(Roles.ModuloDelCatalogo modulo) {
            return new RespuestaModulo(
                    modulo.modulo().id(),
                    modulo.modulo().modulo(),
                    modulo.modulo().nombre(),
                    modulo.modulo().descripcion(),
                    modulo.submodulos().stream().map(RespuestaSubmodulo::desde).toList());
        }
    }

    public record RespuestaSubmodulo(
            UUID id, String codigo, String nombre, List<RespuestaFuncion> funciones) {

        static RespuestaSubmodulo desde(Roles.SubmoduloDelCatalogo submodulo) {
            return new RespuestaSubmodulo(
                    submodulo.submodulo().id(),
                    submodulo.submodulo().modulo(),
                    submodulo.submodulo().nombre(),
                    submodulo.funciones().stream().map(RespuestaFuncion::desde).toList());
        }
    }

    public record RespuestaFuncion(UUID id, String accion, String nombre, String codigo) {

        static RespuestaFuncion desde(Permiso permiso) {
            return new RespuestaFuncion(
                    permiso.id(), permiso.accion(), permiso.nombre(), permiso.codigo());
        }
    }
}
