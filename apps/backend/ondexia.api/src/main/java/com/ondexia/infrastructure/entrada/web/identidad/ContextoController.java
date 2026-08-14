package com.ondexia.infrastructure.entrada.web.identidad;

import com.ondexia.application.identidad.ContextoResuelto;
import com.ondexia.application.identidad.ConsultarContexto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La primera llamada que hace el SPA despues de iniciar sesion.
 *
 * <p>No lleva {@code @PreAuthorize}: no hay permiso que exigir para preguntar
 * quien eres. Cualquiera con un token valido de un usuario activo puede
 * consultarlo, y lo que obtiene es solo lo suyo — el contexto sale del token,
 * nunca de un parametro.
 */
@RestController
@RequestMapping("/api/v1/contexto")
@Tag(name = "Contexto", description = "Identidad, empresas accesibles y permisos efectivos")
public class ContextoController {

    private final ConsultarContexto servicio;

    public ContextoController(ConsultarContexto servicio) {
        this.servicio = servicio;
    }

    @Operation(
            summary = "Contexto del usuario autenticado",
            description = """
                    Devuelve la identidad, las empresas a las que tiene acceso y los permisos \
                    del rol en la empresa activa.

                    La empresa activa se indica con la cabecera X-Empresa-Id. Si se omite y el \
                    usuario solo tiene una, se toma esa. Si tiene varias y no la indica, la \
                    respuesta trae la lista para que elija y el conjunto de permisos vacio.""")
    @GetMapping
    public RespuestaContexto consultar() {
        return RespuestaContexto.desde(servicio.ejecutar());
    }

    /**
     * DTO de salida.
     *
     * <p>Es un tipo aparte del modelo de aplicacion y no se reutiliza aquel. La
     * razon no es ceremonia: este record <strong>es el contrato</strong>, y de
     * el se genera el cliente Angular. Si el DTO fuera el modelo interno,
     * renombrar un campo por claridad interna romperia el frontend, y el miedo a
     * romperlo acabaria congelando los nombres del dominio.
     */
    public record RespuestaContexto(
            UsuarioResumen usuario,
            CuentaResumen cuenta,
            EmpresaResumen empresaActiva,
            List<EmpresaResumen> empresas,
            List<String> permisos) {

        static RespuestaContexto desde(ContextoResuelto resuelto) {
            List<EmpresaResumen> empresas = resuelto.empresas().stream()
                    .map(EmpresaResumen::desde)
                    .toList();

            EmpresaResumen activa = empresas.stream()
                    .filter(e -> e.id().equals(resuelto.empresaActivaId()))
                    .findFirst()
                    .orElse(null);

            return new RespuestaContexto(
                    new UsuarioResumen(resuelto.usuarioId(), resuelto.nombre(), resuelto.email()),
                    new CuentaResumen(resuelto.cuentaId(), resuelto.esAdministradorCuenta(),
                            resuelto.estadoSuscripcion(), resuelto.soloLectura()),
                    activa,
                    empresas,
                    // Ordenados para que la respuesta sea estable entre llamadas.
                    // Un conjunto sin orden hace que el cuerpo cambie sin que
                    // cambie nada, y eso arruina el cacheado y el diagnostico.
                    resuelto.permisos().stream().sorted().toList());
        }
    }

    public record UsuarioResumen(java.util.UUID id, String nombre, String email) {
    }

    /**
     * @param esAdministrador gobierna la suscripcion y el alta de empresas.
     *                        No es un rol de la matriz de permisos — ver
     *                        {@code CuentaAdministrador}
     */
    /**
     * @param estadoSuscripcion {@code ACTIVA}, {@code EN_PRUEBA}, {@code SUSPENDIDA}
     *                          o {@code CANCELADA}. El SPA elige con esto qué
     *                          anuncio pinta
     * @param soloLectura       si la cuenta no puede escribir. Va explícito y no
     *                          deducido del estado para que la regla viva en un
     *                          solo lado; si el frontend la replicara, un cambio
     *                          en el servidor dejaría la interfaz mintiendo
     */
    public record CuentaResumen(
            java.util.UUID id,
            boolean esAdministrador,
            String estadoSuscripcion,
            boolean soloLectura) {
    }

    /**
     * @param sucursalId {@code null} significa que el usuario alcanza todas las
     *                   sucursales de esa empresa
     */
    public record EmpresaResumen(
            java.util.UUID id,
            String ruc,
            String razonSocial,
            String nombreComercial,
            String modoSunat,
            String rol,
            java.util.UUID sucursalId,
            String sucursalNombre) {

        static EmpresaResumen desde(com.ondexia.domain.identidad.AsignacionEmpresa asignacion) {
            return new EmpresaResumen(
                    asignacion.empresaId(),
                    asignacion.ruc(),
                    asignacion.razonSocial(),
                    asignacion.nombreComercial(),
                    asignacion.modoSunat().name(),
                    asignacion.rolNombre(),
                    asignacion.sucursalId(),
                    asignacion.sucursalNombre());
        }
    }
}
