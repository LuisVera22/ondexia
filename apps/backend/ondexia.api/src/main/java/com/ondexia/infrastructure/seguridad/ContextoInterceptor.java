package com.ondexia.infrastructure.seguridad;

import com.ondexia.application.identidad.ResolverContexto;
import com.ondexia.domain.comun.error.NoAutenticado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Establece el contexto antes del controlador y lo limpia después.
 *
 * <p><strong>Interceptor y no filtro.</strong> Un filtro corre antes del
 * {@code DispatcherServlet}, así que sus excepciones no pasan por
 * {@code @RestControllerAdvice} y habría que serializar el error a mano,
 * manteniendo dos formatos de respuesta. El interceptor corre dentro, de modo
 * que un acceso denegado aquí sale con el mismo cuerpo RFC 9457 que cualquier
 * otro error.
 */
@Component
public class ContextoInterceptor implements HandlerInterceptor {

    /**
     * Empresa activa que declara el cliente. Es una petición, no una afirmación:
     * {@link ResolverContexto} comprueba contra la base que el usuario tenga esa
     * asignación.
     */
    public static final String CABECERA_EMPRESA = "X-Empresa-Id";

    private final ResolverContexto resolver;

    public ContextoInterceptor(ResolverContexto resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta,
            Object manejador) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacion instanceof JwtAuthenticationToken token)) {
            throw new NoAutenticado("La peticion no lleva un token valido.");
        }

        Jwt jwt = token.getToken();
        ContextoActual.establecer(
                resolver.ejecutar(jwt.getSubject(), leerEmpresa(peticion), obtenerIp(peticion)));
        return true;
    }

    /**
     * Se limpia aquí y no en {@code postHandle}, que no se ejecuta si el
     * controlador lanza. Dejar el contexto puesto en un hilo que se reutiliza es
     * la fuga entre clientes que este mecanismo existe para impedir.
     */
    @Override
    public void afterCompletion(HttpServletRequest peticion, HttpServletResponse respuesta,
            Object manejador, Exception error) {
        ContextoActual.limpiar();
    }

    private UUID leerEmpresa(HttpServletRequest peticion) {
        String valor = peticion.getHeader(CABECERA_EMPRESA);
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException e) {
            throw new ReglaDeNegocioViolada("solicitud_invalida",
                    "La cabecera " + CABECERA_EMPRESA + " no es un identificador valido.");
        }
    }

    /**
     * Detrás de CloudFront y API Gateway la IP del socket es la del último salto
     * de AWS. La real llega en {@code X-Forwarded-For}, cuyo primer elemento es
     * el cliente original.
     *
     * <p>Esa cabecera es falsificable por quien llegue directo al origen. Aquí no
     * decide nada —solo se registra— y por eso se acepta. <strong>No debe usarse
     * jamás para autorizar.</strong>
     */
    private String obtenerIp(HttpServletRequest peticion) {
        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            int coma = reenviada.indexOf(',');
            return recortar((coma >= 0 ? reenviada.substring(0, coma) : reenviada).trim());
        }
        return recortar(peticion.getRemoteAddr());
    }

    /** La columna admite 45 caracteres: una IPv6 con IPv4 embebida. */
    private String recortar(String ip) {
        return ip == null || ip.length() <= 45 ? ip : ip.substring(0, 45);
    }
}
