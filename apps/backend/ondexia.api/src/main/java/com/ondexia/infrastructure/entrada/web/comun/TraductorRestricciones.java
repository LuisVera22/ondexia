package com.ondexia.infrastructure.entrada.web.comun;

import java.util.Map;
import java.util.Optional;
import com.ondexia.domain.comun.error.Conflicto;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Traduce una restricción violada en la base a un error que el cliente entiende.
 *
 * <h2>Por qué hace falta</h2>
 *
 * Sin esto, registrar un RUC que ya existe produce un <strong>500</strong>: la
 * excepción de integridad no la maneja nadie y cae en el saco de los errores no
 * previstos. Para quien usa la aplicación, «error inesperado» y «ese RUC ya
 * está registrado» son cosas muy distintas.
 *
 * <h2>Por qué no se comprueba antes con un SELECT</h2>
 *
 * Se podría consultar si el RUC existe antes de insertar, y a veces conviene
 * hacerlo para dar un mensaje más temprano. Pero <strong>no sustituye a
 * esto</strong>: entre la consulta y la inserción hay una ventana en la que
 * otra petición puede insertar el mismo valor. La restricción de la base es la
 * única comprobación que no tiene esa ventana, y por tanto la única que de
 * verdad garantiza la unicidad. Traducirla es aceptar que es ella quien manda.
 *
 * <h2>Mantener este mapa</h2>
 *
 * Cada restricción con nombre propio en una migración debería tener su entrada.
 * Las que no la tengan salen como conflicto genérico — correcto en código HTTP,
 * pobre en mensaje. La prueba {@code ArquitecturaTest} no puede comprobarlo
 * (los nombres viven en SQL), así que es disciplina al escribir la migración.
 */
public final class TraductorRestricciones {

    /**
     * Lo que se le cuenta al cliente cuando salta una restricción concreta.
     *
     * @param campo el campo de la petición al que pertenece el choque, o
     *     {@code null} si no es de ninguno. El cliente coloca el mensaje junto
     *     a ese campo en vez de en un aviso de la esquina, que es donde acababa
     *     un «ya existe ese código» sin decir cuál de los cuatro cuadros de
     *     texto lo tiene.
     */
    private record Traduccion(String codigo, String mensaje, String campo) {
        Traduccion(String codigo, String mensaje) {
            this(codigo, mensaje, null);
        }
    }

    private static final Map<String, Traduccion> CONOCIDAS = Map.ofEntries(
            Map.entry("empresa_ruc_key", new Traduccion(
                    "ruc_duplicado",
                    "Ese RUC ya está registrado en Ondexia. Si crees que es un error, "
                            + "escríbenos: puede pertenecer a otra cuenta.",
                    "ruc")),
            Map.entry("empresa_ruc_formato", new Traduccion(
                    "ruc_invalido", "El RUC debe tener exactamente 11 dígitos.",
                    "ruc")),
            Map.entry("empresa_ubigeo_formato", new Traduccion(
                    "ubigeo_invalido", "El ubigeo debe tener exactamente 6 dígitos.",
                    "ubigeo")),
            Map.entry("sucursal_ubigeo_formato", new Traduccion(
                    "ubigeo_invalido", "El ubigeo debe tener exactamente 6 dígitos.",
                    "ubigeo")),
            Map.entry("sucursal_codigo_unico", new Traduccion(
                    "codigo_duplicado",
                    "Ya existe un establecimiento con ese código en esta empresa.",
                    "codigo")),
            Map.entry("producto_codigo_unico", new Traduccion(
                    "codigo_duplicado",
                    "Ya existe un producto con ese código en esta empresa.",
                    "codigo")),
            Map.entry("cliente_documento_unico", new Traduccion(
                    "documento_duplicado",
                    "Ya existe un cliente con ese documento en esta empresa.",
                    "numeroDocumento")),
            Map.entry("documento_venta_numero_unico", new Traduccion(
                    "numero_duplicado",
                    "Ese número ya se emitió. Vuelve a intentar: se asignará el siguiente.")),
            Map.entry("caja_codigo_unico", new Traduccion(
                    "codigo_duplicado",
                    "Ya existe una caja con ese código en ese establecimiento.",
                    "codigo")),
            Map.entry("usuario_email_unico", new Traduccion(
                    "email_duplicado", "Ese correo ya está registrado.", "email")),
            Map.entry("usuario_cognito_sub_key", new Traduccion(
                    "identidad_duplicada",
                    "Esa identidad ya está vinculada a otro usuario.")),
            Map.entry("usuario_empresa_unica", new Traduccion(
                    "asignacion_duplicada",
                    "Ese usuario ya está asignado a esta empresa con ese alcance.")),
            Map.entry("cuenta_administrador_unico", new Traduccion(
                    "propietario_duplicado",
                    "Esta suscripción ya tiene Propietario. Para cambiarlo hay que "
                            + "transferir la propiedad, no añadir otro.")),
            Map.entry("rol_codigo_por_cuenta", new Traduccion(
                    "codigo_duplicado", "Ya existe un rol con ese código en esta cuenta.",
                    "codigo")),
            Map.entry("permiso_codigo_key", new Traduccion(
                    "permiso_duplicado", "Ese permiso ya existe en el catálogo.")));

    /**
     * Mensajes de disparadores.
     *
     * <p>Los disparadores no violan una restricción con nombre: lanzan una
     * excepción con un texto. Se reconocen por un fragmento estable de ese
     * texto, que es frágil — de ahí que el fragmento se elija corto y se defina
     * junto al disparador en la migración V1.
     */
    private static final Map<String, Traduccion> POR_MENSAJE = Map.of(
            "quedaria sin ningun administrador", new Traduccion(
                    "ultimo_administrador",
                    "No puedes quitar al último administrador: la cuenta se quedaría sin "
                            + "nadie que pueda gestionarla."),
            "bitacora es de solo insercion", new Traduccion(
                    "bitacora_inmutable",
                    "La bitácora no se puede modificar ni borrar."));

    private TraductorRestricciones() {
    }

    /**
     * @return el conflicto correspondiente, o vacío si no se reconoce la causa
     *         — en cuyo caso conviene dejarlo pasar como error no previsto en
     *         lugar de inventar un mensaje
     */
    public static Optional<Conflicto> traducir(DataIntegrityViolationException error) {
        String restriccion = nombreDeRestriccion(error);

        if (restriccion != null) {
            Traduccion conocida = CONOCIDAS.get(restriccion);
            if (conocida != null) {
                return Optional.of(new Conflicto(
                        conocida.codigo(), conocida.mensaje(), conocida.campo()));
            }
        }

        String texto = textoCompleto(error).toLowerCase(java.util.Locale.ROOT);
        for (var entrada : POR_MENSAJE.entrySet()) {
            if (texto.contains(entrada.getKey())) {
                return Optional.of(new Conflicto(entrada.getValue().codigo(),
                        entrada.getValue().mensaje(), entrada.getValue().campo()));
            }
        }

        // Restricción con nombre pero sin traducción: es un conflicto de verdad,
        // así que 409 es el código correcto aunque el mensaje sea genérico.
        // Devolver 500 aquí sería mentir sobre de quién es el problema.
        if (restriccion != null) {
            return Optional.of(new Conflicto(
                    "conflicto",
                    "La operación choca con un dato existente. Revisa que no estés "
                            + "repitiendo un valor que debe ser único."));
        }

        return Optional.empty();
    }

    /**
     * Nombre de la restricción, tal como lo reporta Hibernate.
     *
     * <p>Se consulta solo a Hibernate y no al driver de PostgreSQL, aunque
     * {@code PSQLException} expone el nombre de forma más directa: el driver
     * está declarado en ámbito {@code runtime} a propósito, para que ninguna
     * clase de la aplicación compile contra él. Debilitar ese ámbito por un
     * nombre de restricción sería cambiar una frontera real por una comodidad.
     *
     * <p>Lo que Hibernate no clasifica —los disparadores, que lanzan un texto en
     * lugar de violar una restricción con nombre— lo recoge el reconocimiento
     * por mensaje.
     */
    private static String nombreDeRestriccion(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof org.hibernate.exception.ConstraintViolationException hibernate) {
                return hibernate.getConstraintName();
            }
        }
        return null;
    }

    private static String textoCompleto(Throwable error) {
        StringBuilder texto = new StringBuilder();
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa.getMessage() != null) {
                texto.append(causa.getMessage()).append(' ');
            }
        }
        return texto.toString();
    }
}
