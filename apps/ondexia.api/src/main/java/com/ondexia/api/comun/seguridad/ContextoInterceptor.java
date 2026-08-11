package com.ondexia.api.comun.seguridad;

import com.ondexia.api.comun.error.NoAutenticadoException;
import com.ondexia.api.comun.error.SolicitudInvalidaException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Establece el contexto de la peticion antes del controlador y lo limpia
 * despues.
 *
 * <p><strong>Por que un interceptor y no un filtro.</strong> Un filtro corre
 * antes del {@code DispatcherServlet}, asi que las excepciones que lanza no
 * pasan por {@code @RestControllerAdvice}: habria que serializar el error a
 * mano en el filtro y mantener dos formatos de respuesta de error. El
 * interceptor corre dentro del despachador, de modo que un acceso denegado aqui
 * sale con el mismo cuerpo RFC 9457 que cualquier otro error del sistema.
 */
@Component
public class ContextoInterceptor implements HandlerInterceptor {

    /**
     * Empresa activa que declara el cliente.
     *
     * <p>Es una peticion, no una afirmacion: {@link ResolutorContexto} comprueba
     * contra la base que el usuario tenga esa asignacion.
     */
    public static final String CABECERA_EMPRESA = "X-Empresa-Id";

    private final ResolutorContexto resolutor;

    public ContextoInterceptor(ResolutorContexto resolutor) {
        this.resolutor = resolutor;
    }

    @Override
    public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta,
            Object manejador) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacion instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token)) {
            throw new NoAutenticadoException("La peticion no lleva un token valido");
        }

        Jwt jwt = token.getToken();
        String cognitoSub = jwt.getSubject();

        ContextoActual.establecer(
                resolutor.resolver(cognitoSub, leerEmpresaPedida(peticion), obtenerIp(peticion)));
        return true;
    }

    /**
     * Se limpia aqui y no en {@code postHandle}.
     *
     * <p>{@code postHandle} no se ejecuta si el controlador lanza una excepcion.
     * {@code afterCompletion} si, siempre — y dejar el contexto puesto en un
     * hilo que se reutiliza es la fuga entre clientes que este mecanismo existe
     * para impedir. Ver {@link ContextoActual}.
     */
    @Override
    public void afterCompletion(HttpServletRequest peticion, HttpServletResponse respuesta,
            Object manejador, Exception error) {
        ContextoActual.limpiar();
    }

    private UUID leerEmpresaPedida(HttpServletRequest peticion) {
        String valor = peticion.getHeader(CABECERA_EMPRESA);
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalidaException(
                    "La cabecera " + CABECERA_EMPRESA + " no es un identificador valido");
        }
    }

    /**
     * IP de origen, para la bitacora.
     *
     * <p>Detras de CloudFront y API Gateway la IP del socket es la del ultimo
     * salto de AWS, no la del cliente. La real llega en {@code X-Forwarded-For},
     * cuyo primer elemento es el cliente original.
     *
     * <p>Esa cabecera es falsificable por quien llega directo al origen. Aqui no
     * decide nada —solo se registra— y por eso se acepta. <strong>No debe usarse
     * jamas para autorizar</strong> ni para limitar por IP sin validar antes la
     * cadena de proxies de confianza.
     */
    private String obtenerIp(HttpServletRequest peticion) {
        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            int coma = reenviada.indexOf(',');
            String primera = coma >= 0 ? reenviada.substring(0, coma) : reenviada;
            return recortar(primera.trim());
        }
        return recortar(peticion.getRemoteAddr());
    }

    /** La columna admite 45 caracteres, la longitud de una IPv6 con IPv4 embebida. */
    private String recortar(String ip) {
        if (ip == null) {
            return null;
        }
        return ip.length() > 45 ? ip.substring(0, 45) : ip;
    }
}
