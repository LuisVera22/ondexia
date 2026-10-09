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
    private final com.ondexia.domain.comun.ProveedorDeContexto contexto;

    public EvaluadorPermisos(PermisosEfectivos permisos,
            com.ondexia.domain.comun.ProveedorDeContexto contexto) {
        this.permisos = permisos;
        this.contexto = contexto;
    }

    /**
     * @throws com.ondexia.domain.comun.error.ReglaDeNegocioViolada si no hay
     *     empresa activa
     */
    public boolean puede(String modulo, String accion) {
        /*
         * «No has elegido empresa» no es «no tienes permiso».
         *
         * Todos los permisos son POR EMPRESA, así que sin una activa el
         * conjunto está vacío y la evaluación diría que no a todo — un 403.
         * Para el usuario con dos empresas que acaba de entrar y todavía no ha
         * elegido, eso significa que el SPA lo manda a «sin permisos» en vez de
         * a elegir empresa, y el mensaje no se parece en nada al problema.
         *
         * Falla aquí con un código propio para que el frontend pueda
         * distinguirlos. Lo detectan las pruebas de la Entrega 1.
         */
        contexto.obligatorio().empresaActivaObligatoria();

        return permisos.actuales().puede(modulo, accion);
    }

    public boolean esAdministradorDeCuenta() {
        return permisos.esAdministradorDeCuenta();
    }
}
