package com.ondexia.api.comun.seguridad;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Exige ser administrador de la cuenta.
 *
 * <p>Para las acciones que **no pasan por la matriz de permisos**: gestionar la
 * suscripción, dar de alta empresas, asignar usuarios a empresas.
 *
 * <p>No es un permiso más porque la matriz se evalúa sobre el par
 * (usuario, empresa), y estas acciones existen antes de que haya ninguna
 * empresa contra la que evaluar. Ver {@code CuentaAdministrador} para las tres
 * razones completas.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@permisos.esAdministradorDeCuenta()")
public @interface ExigeAdministradorDeCuenta {
}
