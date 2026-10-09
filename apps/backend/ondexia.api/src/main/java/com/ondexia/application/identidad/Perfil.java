package com.ondexia.application.identidad;

import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.identidad.Usuario;
import com.ondexia.domain.identidad.UsuarioRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los datos que cada persona edita de sí misma.
 *
 * <h2>Qué NO se edita aquí, y por qué</h2>
 *
 * <p><strong>El correo.</strong> Es la credencial con la que se entra a Cognito.
 * Cambiarlo en nuestra fila dejaría al usuario apuntando a un buzón con el que
 * ya no puede iniciar sesión — se vería el cambio guardado y el acceso roto, en
 * ese orden. Cambiar el correo de verdad es cambiarlo en Cognito, que exige
 * verificar el nuevo, y eso no lo puede hacer una Lambda sin salida a internet.
 *
 * <p><strong>La contraseña.</strong> No pasa por aquí y no debe: no la
 * guardamos, no la vemos y no la queremos. El cambio vive en el flujo alojado de
 * Cognito.
 *
 * <p><strong>El rol y las empresas.</strong> Los define el administrador de la
 * cuenta. Dejar que alguien se los edite sería dejar que se dé permisos.
 *
 * <h2>No escribe en la bitácora</h2>
 *
 * <p>{@code auditoria} tiene RLS por empresa y este caso de uso corre también
 * <em>sin empresa activa</em> —quien tiene varias entra al escritorio y puede ir
 * a Mi perfil antes de elegir ninguna—. La fila se rechazaría, y el fallo
 * aparecería solo para ese subconjunto de usuarios: el peor reparto posible.
 * Lo que se guarda es el nombre y el teléfono de uno mismo, no una decisión
 * sobre nadie más.
 */
@Service
public class Perfil {

    /** Lo mismo que admite la columna. Más largo se truncaría en la base. */
    private static final int MAXIMO_NOMBRE = 150;
    private static final int MAXIMO_TELEFONO = 30;

    private final UsuarioRepositorio usuarios;
    private final ProveedorDeContexto contexto;

    public Perfil(UsuarioRepositorio usuarios, ProveedorDeContexto contexto) {
        this.usuarios = usuarios;
        this.contexto = contexto;
    }

    @Transactional(readOnly = true)
    public DatosDePerfil ver() {
        return DatosDePerfil.de(actual());
    }

    @Transactional
    public DatosDePerfil actualizar(String nombre, String apellido, String telefono) {
        /*
         * El apellido es obligatorio aquí aunque la columna admita nulo.
         *
         * Las filas anteriores a la V11 lo tienen vacío con el nombre completo
         * metido en `nombre`, y esta pantalla es justo donde se arregla: la
         * persona lo parte una vez y ya queda bien. Aceptarlo en blanco haría
         * que esas filas se quedaran a medias para siempre, porque nada más las
         * vuelve a tocar.
         */
        String nombreLimpio = exigir(nombre, "nombre_requerido", "El nombre es obligatorio.",
                MAXIMO_NOMBRE, "El nombre");
        String apellidoLimpio = exigir(apellido, "apellido_requerido",
                "El apellido es obligatorio.", MAXIMO_NOMBRE, "El apellido");

        if (telefono != null && telefono.trim().length() > MAXIMO_TELEFONO) {
            throw new ReglaDeNegocioViolada(
                    "telefono_muy_largo",
                    "El teléfono no puede pasar de " + MAXIMO_TELEFONO + " caracteres.");
        }

        var usuario = actual();
        usuario.actualizarPerfil(nombreLimpio, apellidoLimpio, telefono);
        return DatosDePerfil.de(usuarios.guardar(usuario));
    }

    private static String exigir(String valor, String codigo, String mensaje, int maximo,
            String etiqueta) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) {
            throw new ReglaDeNegocioViolada(codigo, mensaje);
        }
        if (limpio.length() > maximo) {
            throw new ReglaDeNegocioViolada(
                    codigo.replace("_requerido", "_muy_largo"),
                    etiqueta + " no puede pasar de " + maximo + " caracteres.");
        }
        return limpio;
    }

    /**
     * El usuario de la petición, del contexto y nunca de un parámetro. Aceptar
     * un id abriría la puerta a editar el perfil de otro.
     */
    private Usuario actual() {
        var id = contexto.obligatorio().usuarioId();
        return usuarios.buscarPorId(id)
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "usuario_no_encontrado",
                        "El usuario de la sesión ya no existe."));
    }

    /**
     * @param apellido nulo en las filas anteriores a la V11. El formulario lo
     *                 exige, así que se queda así solo hasta el primer guardado
     * @param email    se devuelve para pintarlo, no para editarlo
     */
    public record DatosDePerfil(String nombre, String apellido, String email, String telefono) {

        static DatosDePerfil de(Usuario usuario) {
            return new DatosDePerfil(usuario.nombre(), usuario.apellido(), usuario.email(),
                    usuario.telefono());
        }
    }
}
