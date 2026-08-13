package com.ondexia.infrastructure.seguridad;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Exige ser administrador de la cuenta: gestionar la suscripción, dar de alta
 * empresas, asignar usuarios.
 *
 * <p>No pasa por la matriz de permisos porque la matriz se evalúa sobre el par
 * (usuario, empresa), y estas acciones existen antes de que haya empresa alguna.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@permisos.esAdministradorDeCuenta()")
public @interface ExigeAdministradorDeCuenta {
}
