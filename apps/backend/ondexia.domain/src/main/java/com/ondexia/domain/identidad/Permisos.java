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

    public boolean puede(String modulo, String accion) {
        return codigos.contains(Permiso.componerCodigo(modulo, accion));
    }

    public boolean vacio() {
        return codigos.isEmpty();
    }
}
