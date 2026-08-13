package com.ondexia.infrastructure.entrada.web.registro;

import com.ondexia.application.registro.RegistrarCuenta;
import com.ondexia.domain.comun.error.NoAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alta de un cliente nuevo.
 *
 * <p>El único endpoint del sistema que atiende a alguien con token válido y sin
 * fila en la base. Está excluido del {@code ContextoInterceptor} (ver
 * {@code WebConfig}) porque el contexto es precisamente lo que crea.
 *
 * <p>Sin {@code @RequierePermiso}: los permisos son por empresa y aquí todavía
 * no hay ninguna. Lo que protege este endpoint es el autorizador de la pasarela
 * —hace falta un token de Cognito verificado— y la unicidad del RUC.
 */
@RestController
@RequestMapping("/api/v1/registro")
@Tag(name = "Registro", description = "Alta de cuenta, empresa y primer administrador")
public class RegistroController {

    private final RegistrarCuenta registrar;

    public RegistroController(RegistrarCuenta registrar) {
        this.registrar = registrar;
    }

    @Operation(
            summary = "Da de alta la cuenta del usuario autenticado",
            description = """
                    Crea cuenta, usuario administrador, empresa y el establecimiento de casa \
                    matriz (0000) en una sola transacción. La identidad sale del token, nunca \
                    del cuerpo: aceptarla como parámetro permitiría darse de alta suplantando \
                    a otra persona.""")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaRegistro registrar(
            @Valid @RequestBody PeticionRegistro peticion, Authentication autenticacion) {

        if (!(autenticacion instanceof JwtAuthenticationToken token)) {
            throw new NoAutenticado("El registro necesita un token válido.");
        }

        var jwt = token.getToken();

        /*
         * El correo sale del token, con dos posibles nombres.
         *
         * El token de identidad de Cognito lleva `email`; el de acceso, según la
         * configuración, puede traer `username`. Se prefiere `email` y se cae a
         * lo que llegue: quedarse sin correo aquí dejaría un usuario que no se
         * puede identificar en la pantalla de usuarios.
         */
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("username");
        }
        if (email == null || email.isBlank()) {
            throw new NoAutenticado(
                    "El token no trae el correo. Revisa que el acceso pida el ámbito 'email'.");
        }

        UUID cuentaId = registrar.ejecutar(
                jwt.getSubject(),
                email,
                new RegistrarCuenta.DatosDeRegistro(
                        peticion.ruc(),
                        peticion.razonSocial(),
                        peticion.domicilioFiscal(),
                        peticion.ubigeo(),
                        peticion.nombreTitular()));

        return new RespuestaRegistro(cuentaId);
    }

    /**
     * @param ruc se valida aquí solo en formato; el dígito verificador lo
     *            comprueba el value object {@code Ruc}, que es donde vive esa
     *            regla y donde no se puede olvidar
     */
    public record PeticionRegistro(
            @NotBlank(message = "El RUC es obligatorio.")
            @Pattern(regexp = "\\d{11}", message = "El RUC son once dígitos.")
            String ruc,

            @NotBlank(message = "La razón social es obligatoria.")
            @Size(max = 300)
            String razonSocial,

            @NotBlank(message = "El domicilio fiscal es obligatorio.")
            @Size(max = 400)
            String domicilioFiscal,

            @Pattern(regexp = "^$|^\\d{6}$", message = "El ubigeo son seis dígitos.")
            String ubigeo,

            @NotBlank(message = "Falta tu nombre.")
            @Size(max = 150)
            String nombreTitular) {
    }

    public record RespuestaRegistro(UUID cuentaId) {
    }
}
