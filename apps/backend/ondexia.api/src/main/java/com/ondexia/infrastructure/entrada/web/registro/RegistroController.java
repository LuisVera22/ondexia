package com.ondexia.infrastructure.entrada.web.registro;

import com.ondexia.application.registro.RegistrarCuenta;
import com.ondexia.application.registro.VincularInvitacion;
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
    private final VincularInvitacion vincular;

    public RegistroController(RegistrarCuenta registrar, VincularInvitacion vincular) {
        this.registrar = registrar;
        this.vincular = vincular;
    }

    /**
     * Engancha a quien fue invitado con la identidad que acaba de crear.
     *
     * <h2>Este endpoint exige el token de IDENTIDAD, y es lo único que lo exige</h2>
     *
     * <p>Todo lo demás en la API se atiende con el token de acceso. Aquí no
     * sirve: el de acceso de Cognito no lleva {@code email}, y este endpoint
     * decide en qué empresa entra alguien a partir de su correo. Un correo que no
     * venga firmado por Cognito convierte esto en una toma de cuenta — quien
     * envíe el correo ajeno entra en la empresa ajena.
     *
     * <p>La pasarela valida los dos tipos por igual: el autorizador comprueba la
     * firma, el emisor y que la audiencia sea nuestro cliente, y el
     * {@code aud} de un token de identidad es justamente el identificador del
     * cliente. Lo que la pasarela <em>no</em> distingue es cuál de los dos es, y
     * por eso se comprueba aquí.
     *
     * <p>No lleva cuerpo. Todo sale del token, que es lo que lo hace seguro.
     */
    @Operation(
            summary = "Vincula la identidad recién creada con una invitación pendiente",
            description = """
                    Exige el token de IDENTIDAD de Cognito (token_use=id) con el correo \
                    verificado: el correo decide en qué empresa entra la persona, así que \
                    tiene que venir firmado por Cognito y no del cuerpo de la petición. \
                    Responde 404 si no hay ninguna invitación para ese correo.""")
    @PostMapping("/vinculo")
    public RespuestaRegistro vincular(Authentication autenticacion) {

        if (!(autenticacion instanceof JwtAuthenticationToken token)) {
            throw new NoAutenticado("La vinculación necesita un token válido.");
        }

        var jwt = token.getToken();

        if (!"id".equals(jwt.getClaimAsString("token_use"))) {
            throw new NoAutenticado(
                    "Esta operación necesita el token de identidad, no el de acceso.");
        }

        /*
         * `email_verified` en falso significa que Cognito emitió el token pero
         * nadie ha probado que ese buzón sea de quien se registró. Sin esta
         * comprobación, darse de alta con el correo de otra persona bastaría para
         * reclamar su invitación.
         *
         * Se lee como texto y no con getClaimAsBoolean porque Cognito lo emite
         * unas veces como booleano y otras como cadena, segun el camino de alta.
         *
         * La variable intermedia es obligatoria, no estilo: `getClaim` devuelve
         * `<T> T`, y pasado directo a String.valueOf el compilador infiere
         * `char[]` y elige esa sobrecarga. Compila sin una queja y revienta en
         * ejecucion con «Boolean cannot be cast to [C» — un 500 en el sitio
         * donde lo que tocaba era un 401.
         */
        Object correoVerificado = jwt.getClaim("email_verified");
        if (!"true".equalsIgnoreCase(String.valueOf(correoVerificado))) {
            throw new NoAutenticado(
                    "El correo de la cuenta todavía no está verificado.");
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new NoAutenticado("El token de identidad no trae el correo.");
        }

        return new RespuestaRegistro(vincular.ejecutar(jwt.getSubject(), email));
    }

    @Operation(
            summary = "Da de alta la cuenta del usuario autenticado",
            description = """
                    Crea cuenta, usuario administrador, empresa y el establecimiento de casa \
                    matriz (0000) en una sola transacción. La identidad sale del token, nunca \
                    del cuerpo: aceptarla como parámetro permitiría darse de alta suplantando \
                    a otra persona. Los datos de la empresa salen de la atestación firmada que \
                    devuelve GET /consultas/ruc/{ruc}, así que no hay alta posible con un RUC \
                    que no esté ACTIVO y HABIDO en SUNAT. Responde 400 si la atestación no es \
                    válida o caducó, y 409 si el RUC ya está registrado.""")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaRegistro registrar(
            @Valid @RequestBody PeticionRegistro peticion, Authentication autenticacion) {

        if (!(autenticacion instanceof JwtAuthenticationToken token)) {
            throw new NoAutenticado("El registro necesita un token válido.");
        }

        var jwt = token.getToken();

        /*
         * El correo sale del TOKEN, y solo del token (tabla de bajas de la
         * auditoria 2026-09-01).
         *
         * Aqui hubo un respaldo: «si el token no trae `email`, se toma del
         * cuerpo». Existia porque el token de ACCESO de Cognito no lleva el
         * correo, y el argumento era que mentir en ese campo no abria nada. Era
         * casi cierto —`invitar` busca por correo DENTRO de la cuenta, asi que
         * no se podia reclamar una invitacion ajena— y aun asi dejaba que la
         * fila `usuario` naciera con un correo que nadie habia verificado, en un
         * sistema donde el correo decide quien entra a que empresa.
         *
         * El SPA manda ahora el token de IDENTIDAD para esta llamada, igual que
         * para la vinculacion: ese si trae el correo, y verificado por Cognito.
         */
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new NoAutenticado(
                    "El alta necesita el token de identidad, que es el que trae el correo.");
        }

        UUID cuentaId = registrar.ejecutar(
                jwt.getSubject(),
                email,
                new RegistrarCuenta.DatosDeRegistro(
                        peticion.atestacion(),
                        peticion.nombreTitular(),
                        peticion.apellidoTitular()));

        return new RespuestaRegistro(cuentaId);
    }

    /**
     * Lo que pide el alta: la verificación del RUC y quién eres.
     *
     * <h2>Ya no hay RUC, razón social, domicilio ni ubigeo</h2>
     *
     * <p>Los cuatro salen de la atestación. El RUC llegaba antes como cadena y
     * se validaba el formato aquí y el dígito verificador en el value object,
     * pero <strong>nada comprobaba que existiera</strong>: se podía crear una
     * cuenta con un RUC bien formado e inventado, y con la razón social que a
     * uno le pareciera.
     *
     * <p>De cinco campos de empresa a uno. El formulario pide el RUC, consulta,
     * y muestra el resto ya relleno y en solo lectura — que además es menos
     * trabajo para quien se registra.
     */
    public record PeticionRegistro(
            @NotBlank(message = "Falta la verificación del RUC.")
            String atestacion,

            @NotBlank(message = "Falta tu nombre.")
            @Size(max = 150)
            String nombreTitular,

            @NotBlank(message = "Falta tu apellido.")
            @Size(max = 150)
            String apellidoTitular) {
    }

    public record RespuestaRegistro(UUID cuentaId) {
    }
}
