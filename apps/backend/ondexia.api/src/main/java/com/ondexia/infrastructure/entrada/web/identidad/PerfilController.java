package com.ondexia.infrastructure.entrada.web.identidad;

import com.ondexia.application.identidad.Perfil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mis datos.
 *
 * <p>Sin {@code @RequierePermiso}, igual que {@code ContextoController}: no hay
 * permiso que exigir para editar lo propio, y crear uno —{@code perfil:editar}—
 * significaría que un administrador puede quitártelo y dejarte sin poder
 * corregir tu nombre. Lo que protege este endpoint es que el usuario sale del
 * contexto y no de un parámetro: no hay forma de nombrar a otro.
 *
 * <p>Tampoco depende de la empresa activa. Quien tiene varias y aún no ha
 * elegido puede entrar a Mi perfil desde el escritorio, y una comprobación de
 * empresa aquí lo dejaría fuera justo a él.
 */
@RestController
@RequestMapping("/api/v1/perfil")
@Tag(name = "Perfil", description = "Los datos que cada persona edita de sí misma")
public class PerfilController {

    private final Perfil perfil;

    public PerfilController(Perfil perfil) {
        this.perfil = perfil;
    }

    @Operation(summary = "Mis datos")
    @GetMapping
    public Perfil.DatosDePerfil ver() {
        return perfil.ver();
    }

    @Operation(
            summary = "Actualiza mi nombre y mi teléfono",
            description = """
                    El correo no se puede cambiar: es la credencial de Cognito, y cambiarlo \
                    aquí dejaría la fila apuntando a un buzón con el que ya no se puede \
                    entrar. La contraseña tampoco pasa por la API — vive en Cognito.""")
    @PutMapping
    public Perfil.DatosDePerfil actualizar(@Valid @RequestBody PeticionPerfil peticion) {
        return perfil.actualizar(peticion.nombre(), peticion.telefono());
    }

    /**
     * @param telefono opcional. En blanco borra el que hubiera
     */
    public record PeticionPerfil(
            @NotBlank(message = "El nombre es obligatorio.")
            @Size(max = 150)
            String nombre,

            @Size(max = 30, message = "El teléfono no puede pasar de 30 caracteres.")
            String telefono) {
    }
}
