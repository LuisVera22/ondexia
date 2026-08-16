package com.ondexia.domain.identidad;

import java.util.UUID;

/**
 * Una persona con acceso a una empresa, vista desde la empresa.
 *
 * <p>Es {@link AsignacionEmpresa} mirada del otro lado: aquella responde «¿a qué
 * empresas llega este usuario?» y esta «¿quién entra en esta empresa?». Comparten
 * tabla y no proyección, porque las columnas que interesan son distintas —a nadie
 * que administre usuarios le importa el RUC de su propia empresa— y una
 * proyección que sirva para las dos preguntas acaba trayendo el doble de columnas
 * para descartar la mitad en cada uso.
 *
 * @param cognitoVinculado si la persona ya completó su registro. Mientras sea
 *                         {@code false} la fila existe y el acceso no: el
 *                         administrador da de alta, y la vinculación ocurre en el
 *                         primer ingreso. Sin este dato la pantalla no podría
 *                         distinguir «invitado» de «activo», que es justo lo que
 *                         se pregunta al ver que alguien no entra
 * @param sucursalId       {@code null} = alcanza todos los establecimientos
 */
public record MiembroEmpresa(
        UUID asignacionId,
        UUID usuarioId,
        String email,
        String nombre,
        String apellido,
        boolean activo,
        boolean cognitoVinculado,
        UUID rolId,
        String rolCodigo,
        String rolNombre,
        UUID sucursalId,
        String sucursalNombre) {

    /**
     * Constructor para la consulta de proyección, que trae el {@code sub} en vez
     * del booleano.
     *
     * <p>Existe para no escribir un {@code case when … then true else false end}
     * en JPQL: se lee peor que esta línea y su tipado depende del dialecto.
     */
    public MiembroEmpresa(UUID asignacionId, UUID usuarioId, String email, String nombre,
            String apellido, boolean activo, String cognitoSub, UUID rolId, String rolCodigo,
            String rolNombre, UUID sucursalId, String sucursalNombre) {
        this(asignacionId, usuarioId, email, nombre, apellido, activo, cognitoSub != null,
                rolId, rolCodigo, rolNombre, sucursalId, sucursalNombre);
    }

    /**
     * Como se muestra en el listado. Une las dos columnas igual que
     * {@code Usuario.nombreCompleto()}, y por el mismo motivo devuelve solo el
     * nombre cuando el apellido está en nulo: las filas anteriores a la V11 se
     * siguen viendo como siempre.
     */
    public String nombreCompleto() {
        return apellido == null || apellido.isBlank() ? nombre : nombre + " " + apellido;
    }

    public boolean alcanzaTodosLosEstablecimientos() {
        return sucursalId == null;
    }
}
