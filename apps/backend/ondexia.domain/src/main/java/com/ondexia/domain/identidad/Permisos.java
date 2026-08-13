package com.ondexia.domain.identidad;

import java.util.Set;

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

    public boolean vacio() {
        return codigos.isEmpty();
    }
}
