package com.ondexia.domain.identidad;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * El conjunto de permisos de un rol, y la decisión de si algo se puede.
 *
 * <p>Existe para que <strong>la decisión de autorización sea del dominio</strong>
 * y no de un bean de Spring Security. El evaluador de SpEL que hay en
 * infraestructura resuelve el contexto y delega aquí; la regla —qué significa
 * «puede»— vive en este archivo, se lee en diez líneas y se prueba sin
 * levantar nada.
 *
 * <p><strong>Deniega por defecto.</strong> Un conjunto vacío no puede nada. Un
 * fallo de configuración debe cerrar la puerta, no abrirla.
 */
public record Permisos(Set<String> codigos) {

    private static final Permisos NINGUNO = new Permisos(Set.of());

    public Permisos {
        codigos = codigos == null ? Set.of() : Set.copyOf(codigos);
    }

    public static Permisos ninguno() {
        return NINGUNO;
    }

    /**
     * Autorización conjuntiva sobre los tres niveles del catálogo.
     *
     * <p>Para consultar productos hacen falta {@code almacen:acceder},
     * {@code almacen.producto:acceder} y {@code almacen.producto:consultar}. Los
     * tres. Tener la función suelta no basta.
     *
     * <p>Esa es la propiedad que sostiene el interruptor de módulo: retirar
     * {@code almacen:acceder} de un rol deja fuera sus treinta y tantas casillas
     * de almacén de una vez.
     *
     * <p>Y al guardar se van de verdad: {@code Roles.cambiarPermisos} poda lo que
     * se queda sin padre, de modo que apagar un módulo <strong>borra</strong> lo
     * que hubiera marcado dentro, no lo deja dormido. Volver a encenderlo lo
     * devuelve vacío. Es la lectura menos sorprendente de las dos —lo guardado
     * coincide siempre con lo que está en vigor— pero conviene tenerla presente
     * antes de apagar un módulo con treinta casillas puestas.
     *
     * <p>El precio es un modo de fallo que hay que tener presente: un rol con la
     * función marcada y sin el eslabón de arriba recibe un «no» que no se explica
     * mirando esa casilla. Se cierra por dos lados —la pantalla pinta en gris lo
     * que cuelga de un módulo apagado, y {@code Roles.cambiarPermisos} poda al
     * guardar lo que no tenga a sus padres— de modo que ese estado no llega a
     * existir en la base.
     *
     * @param submodulo código con punto, {@code almacen.producto}
     */
    public boolean puede(String submodulo, String accion) {
        if (submodulo == null || accion == null) {
            return false;
        }

        String modulo = Permiso.moduloDe(submodulo);
        if (modulo.isEmpty()) {
            // Un código sin punto no identifica ninguna capacidad concreta.
            // Denegar es lo correcto: la alternativa sería adivinar.
            return false;
        }

        return codigos.contains(Permiso.componerCodigo(modulo, Permiso.ACCEDER))
                && codigos.contains(Permiso.componerCodigo(submodulo, Permiso.ACCEDER))
                && codigos.contains(Permiso.componerCodigo(submodulo, accion));
    }

    /** Si el rol alcanza el módulo. Lo usa el menú para no pintar áreas vacías. */
    public boolean alcanzaModulo(String modulo) {
        return codigos.contains(Permiso.componerCodigo(modulo, Permiso.ACCEDER));
    }

    /**
     * Recorta estos permisos a lo que la cuenta tiene contratado.
     *
     * <h2>Máscara en vez de una cuarta comprobación</h2>
     *
     * <p>El doc 09 §5 describe la autorización como cuatro condiciones a la vez, y
     * la cuarta —«la cuenta tiene contratado el módulo»— podría haberse añadido a
     * {@link #puede}. Se implementa recortando el conjunto, que es equivalente y
     * tiene dos ventajas.
     *
     * <p>La primera es que <strong>no hay ningún consumidor que pueda olvidarla</strong>.
     * {@code puede}, {@link #alcanzaModulo} y el menú que el frontend recibe leen
     * el mismo conjunto; si el recorte estuviera dentro de {@code puede}, el menú
     * seguiría pintando un módulo no contratado y el usuario descubriría el límite
     * al recibir un 403 tras hacer clic.
     *
     * <p>La segunda es que deja la regla en un solo sitio. Un módulo no contratado
     * desaparece, y con él sus submódulos y sus funciones, porque el código de
     * cada uno empieza por el del módulo.
     *
     * <p><strong>Deniega por defecto</strong>: sin nada contratado no queda nada.
     *
     * @param contratados códigos de módulo y submódulo contratados, sin acción:
     *                    {@code almacen}, {@code almacen.producto}
     */
    public Permisos limitadoA(Set<String> contratados) {
        if (contratados == null || contratados.isEmpty()) {
            return NINGUNO;
        }

        return new Permisos(codigos.stream()
                .filter(codigo -> contratados.contains(moduloDelCodigo(codigo)))
                .collect(Collectors.toUnmodifiableSet()));
    }

    /** Lo que se puede hacer con la suscripción caída: mirar y llevarse. */
    private static final Set<String> ACCIONES_DE_LECTURA =
            Set.of(Permiso.ACCEDER, "consultar", "exportar");

    /**
     * Deja solo lo que no modifica nada.
     *
     * <p>Es la otra máscara, y responde a la decisión del doc 09 §5.1: una cuenta
     * suspendida <strong>entra</strong>. Sus comprobantes tienen obligación de
     * conservación de cinco años y es el cliente quien responde por ellos ante
     * SUNAT; dejarle fuera de sus propios documentos por una factura impaga sería
     * convertirle un problema comercial en uno tributario.
     *
     * <p>Se conserva {@code exportar} a propósito, y no por generosidad: el aviso
     * de cuenta cancelada le dice al cliente cómo llevarse sus datos, y ese aviso
     * sería una burla si la acción estuviera cortada.
     *
     * <p>Lo que se corta es lo que <em>genera obligaciones nuevas</em> —emitir,
     * anular, registrar, editar— que es justo lo que no debe seguir ocurriendo
     * mientras el contrato no esté vigente.
     */
    public Permisos soloLectura() {
        return new Permisos(codigos.stream()
                .filter(codigo -> ACCIONES_DE_LECTURA.contains(accionDelCodigo(codigo)))
                .collect(Collectors.toUnmodifiableSet()));
    }

    private static String accionDelCodigo(String codigo) {
        int separador = codigo.indexOf(':');
        return separador < 0 ? "" : codigo.substring(separador + 1);
    }

    /**
     * La parte de antes de los dos puntos. Un código sin ellos no identifica nada,
     * y devolver la cadena entera hace que no case con ningún contratado — es
     * decir, se deniega, que es lo correcto.
     */
    private static String moduloDelCodigo(String codigo) {
        int separador = codigo.indexOf(':');
        return separador < 0 ? codigo : codigo.substring(0, separador);
    }

    public boolean vacio() {
        return codigos.isEmpty();
    }

    /**
     * Si estos permisos incluyen todos los de {@code otros}.
     *
     * <p>Es la regla de no elevación del plan del primer producto (doc 12 §6.3):
     * <strong>un rol solo puede conceder permisos que su portador tiene.</strong>
     * Quien invita, reasigna o edita un rol no puede darle más de lo que él mismo
     * puede; el administrador de la cuenta está fuera de la matriz y no pasa por
     * aquí. Con dos roles por defecto la regla es corta de enunciar y de probar,
     * que es lo que la versión anterior temía de una comparación de subconjuntos
     * (ver el comentario de {@code Usuarios.impedirQueSeAscienda}).
     */
    public boolean cubre(Permisos otros) {
        return otros == null || codigos.containsAll(otros.codigos());
    }
}
