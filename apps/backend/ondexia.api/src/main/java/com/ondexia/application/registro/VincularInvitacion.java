package com.ondexia.application.registro;

import com.ondexia.domain.comun.error.Conflicto;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.identidad.Usuario;
import com.ondexia.domain.identidad.UsuarioRepositorio;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Engancha a quien fue invitado con la identidad que acaba de crearse.
 *
 * <h2>El agujero que esto tapa</h2>
 *
 * <p>Dar de alta a alguien crea su fila {@code usuario} con {@code cognito_sub}
 * nulo: es una invitación, no un acceso. La persona se registra por su cuenta en
 * Cognito y obtiene un {@code sub} nuevo, que nadie relaciona con esa fila. El
 * resultado, hasta que existió esta clase, era que {@code /contexto} respondía
 * {@code usuario_no_registrado} y el SPA la llevaba al formulario de empresa
 * nueva: la invitada terminaba creando una segunda cuenta con su propio RUC, y
 * la invitación se quedaba colgada para siempre.
 *
 * <h2>Por qué el correo tiene que venir del token de identidad</h2>
 *
 * <p>Vincular es responder «¿de quién es este correo?», y esa respuesta decide
 * en qué empresa entra alguien. Aceptar el correo del cuerpo de la petición
 * —como sí hace {@link RegistrarCuenta}, donde es inofensivo porque la identidad
 * con la que se opera es el {@code sub}— aquí sería una toma de cuenta completa:
 * cualquiera se registra con un correo suyo, envía el ajeno y entra en la
 * empresa de otro con el rol que tuviera esa invitación.
 *
 * <p>El token de ACCESO de Cognito no lleva {@code email}, así que este caso de
 * uso se alimenta del de IDENTIDAD, que sí lo lleva junto a
 * {@code email_verified} y firmado por Cognito. La comprobación de que el token
 * es realmente de identidad y de que el correo está verificado vive en el
 * controlador, que es quien lee el token; aquí llega un correo del que ya se
 * respondió esa pregunta.
 *
 * <h2>Ante la duda, no se adivina</h2>
 *
 * <p>El correo es único dentro de una cuenta, no en toda la instalación: dos
 * clientes pueden haber invitado a la misma persona. Elegir por ella —la más
 * antigua, la primera que devuelva la base— la metería en la empresa equivocada
 * sin que nadie se entere. Se responde con un conflicto y se deja que lo resuelva
 * un humano.
 */
@Service
public class VincularInvitacion {

    private final UsuarioRepositorio usuarios;

    public VincularInvitacion(UsuarioRepositorio usuarios) {
        this.usuarios = usuarios;
    }

    /**
     * @param cognitoSub del token, nunca del cuerpo
     * @param email      del token de identidad, ya comprobado como verificado
     * @return la cuenta a la que quedó vinculado
     */
    @Transactional
    public UUID ejecutar(String cognitoSub, String email) {
        /*
         * Idempotente a propósito. El SPA llama aquí cada vez que aterriza en la
         * pantalla de registro, y quien recarga esa pantalla después de haberse
         * vinculado tiene que volver a entrar, no encontrarse un error.
         */
        var yaVinculado = usuarios.buscarPorCognitoSub(cognitoSub);
        if (yaVinculado.isPresent()) {
            return yaVinculado.get().cuentaId();
        }

        List<Usuario> pendientes = usuarios.buscarInvitacionesPendientes(email);

        if (pendientes.isEmpty()) {
            throw RecursoNoEncontrado.con(
                    "sin_invitacion",
                    "No hay ninguna invitación pendiente para " + email + ".");
        }

        if (pendientes.size() > 1) {
            throw new Conflicto(
                    "varias_invitaciones",
                    "Hay más de una empresa que te ha invitado con este correo. "
                            + "Pide a una de ellas que retire su invitación para poder entrar.");
        }

        Usuario invitado = pendientes.getFirst();

        /*
         * Una invitación desactivada no vincula.
         *
         * Es el camino que queda si alguien es dado de alta, desactivado antes de
         * llegar a registrarse, y se registra igualmente. Sin esta comprobación
         * entraría, porque `ResolverContexto` mira `activo` en cada petición pero
         * ya sería tarde: la fila estaría vinculada a su `sub` para siempre y
         * reactivarla le daría acceso sin que nadie decidiera dárselo.
         */
        if (!invitado.estaActivo()) {
            throw new Conflicto(
                    "invitacion_desactivada",
                    "Tu acceso está desactivado. Pide al administrador que lo reactive.");
        }

        invitado.vincularIdentidad(cognitoSub);
        usuarios.guardar(invitado);

        // Sin bitácora, por lo mismo que el alta: `auditoria` tiene RLS por
        // empresa y aquí todavía no hay contexto que fijar. La fila se rechazaría.
        return invitado.cuentaId();
    }
}
