package com.ondexia.infrastructure.seguridad;

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
 * }</pre>
 *
 * <p>Spring Security sustituye <code>{modulo}</code> y <code>{accion}</code> por
 * los valores de cada uso. Requiere el bean
 * {@code AnnotationTemplateExpressionDefaults}; sin él la expresión llega
 * literal, el evaluador busca un permiso llamado <code>{modulo}:{accion}</code>
 * y <strong>deniega siempre, sin ningún error</strong>. Lo cubre una prueba.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@permisos.puede('{modulo}', '{accion}')")
public @interface RequierePermiso {

    String modulo();

    String accion();
}
