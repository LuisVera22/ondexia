package com.ondexia.infrastructure.seguridad;

import com.ondexia.application.identidad.PermisosEfectivos;
import org.springframework.stereotype.Component;

/**
 * Puente entre Spring Security y la decisión de la aplicación.
 *
 * <p>Se registra con el nombre {@code permisos} para poder escribirlo corto en
 * las anotaciones: {@code @permisos.puede('almacen.producto', 'registrar')}.
 *
 * <p><strong>No decide nada.</strong> Delega en {@link PermisosEfectivos}, que
 * vive en la capa de aplicación porque también lo necesita
 * {@code ConsultarContexto} para decirle al frontend qué menús pintar. Esta
 * clase existe solo porque SpEL necesita un bean con un nombre corto, y ese es
 * un detalle de Spring Security — es decir, de infraestructura.
 */
@Component("permisos")
public class EvaluadorPermisos {

    private final PermisosEfectivos permisos;

    public EvaluadorPermisos(PermisosEfectivos permisos) {
        this.permisos = permisos;
    }

    public boolean puede(String modulo, String accion) {
        return permisos.actuales().puede(modulo, accion);
    }

    public boolean esAdministradorDeCuenta() {
        return permisos.esAdministradorDeCuenta();
    }
}
