package com.ondexia.api.comun.seguridad;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Exige un permiso {@code (modulo, accion)} sobre la empresa activa.
 *
 * <pre>{@code
 * @RequierePermiso(modulo = "configuracion.empresa", accion = "editar")
 * @PutMapping
 * public EmpresaRespuesta actualizar(...) { ... }
 * }</pre>
 *
 * <p>Es una meta-anotación sobre {@code @PreAuthorize}: Spring Security
 * sustituye <code>{modulo}</code> y <code>{accion}</code> por los valores de
 * cada uso. Requiere el bean {@code AnnotationTemplateExpressionDefaults}, que
 * se declara en {@link com.ondexia.api.comun.configuracion.SeguridadConfig}.
 *
 * <h2>Por qué existe, si {@code @PreAuthorize} ya funcionaba</h2>
 *
 * Escribir <code>@PreAuthorize("@permisos.puede('x','y')")</code> en cincuenta
 * controladores tiene dos problemas. El primero es que la expresión es una
 * cadena: una errata en el nombre del bean o en el del método **no falla al
 * compilar y no falla al arrancar** — falla en tiempo de ejecución, la primera
 * vez que alguien llama a ese endpoint, y como falla denegando el acceso puede
 * pasar por comportamiento correcto. El segundo es que cambiar la mecánica de
 * autorización obligaría a editar esas cincuenta cadenas.
 *
 * <p>Con la anotación, {@code modulo} y {@code accion} son atributos, y la
 * expresión vive en un único sitio.
 *
 * <h2>Lo que sigue sin garantizar</h2>
 *
 * Que los valores existan en el catálogo de permisos. {@code modulo = "almacn"}
 * compila igual y deniega siempre. Lo cubre la prueba de arquitectura
 * {@code ArquitecturaTest}, que compara todos los usos contra la migración V2.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@permisos.puede('{modulo}', '{accion}')")
public @interface RequierePermiso {

    /** Por ejemplo {@code configuracion.empresa} o {@code almacen.producto}. */
    String modulo();

    /** Por ejemplo {@code consultar}, {@code registrar}, {@code anular}. */
    String accion();
}
