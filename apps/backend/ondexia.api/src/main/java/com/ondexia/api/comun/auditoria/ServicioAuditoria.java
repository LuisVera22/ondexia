package com.ondexia.api.comun.auditoria;

import com.ondexia.api.comun.seguridad.ContextoActual;
import com.ondexia.api.comun.seguridad.ContextoPeticion;
import com.ondexia.domain.auditoria.Auditoria;
import com.ondexia.domain.auditoria.AuditoriaRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
// Jackson 3, no Jackson 2. Spring Boot 4 trae `tools.jackson` — el paquete raiz
// cambio de `com.fasterxml.jackson` al renombrarse la biblioteca, y ambas pueden
// convivir en el classpath a proposito. Si se importa la version 2, el codigo
// compila (springdoc la arrastra) y luego no encuentra el bean al arrancar.
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Deja constancia de quién cambió qué.
 *
 * <h2>Se escribe dentro de la transacción del cambio</h2>
 *
 * {@code Propagation.MANDATORY} lo impone: este método <strong>exige</strong>
 * una transacción ya abierta y falla si no la hay. No es rigidez — es la
 * propiedad que hace que la bitácora sirva de algo:
 *
 * <ul>
 *   <li>Si el cambio se deshace, la anotación se deshace con él. Una bitácora
 *       que registra cambios que nunca ocurrieron miente igual que una que
 *       omite los que sí.</li>
 *   <li>Si la anotación falla, el cambio se deshace también. En un sistema con
 *       obligaciones de conservación, un cambio sin rastro es peor que un
 *       cambio que no se llegó a hacer.</li>
 * </ul>
 *
 * <p>La alternativa —{@code REQUIRES_NEW}, o publicar un evento asíncrono— hace
 * que la bitácora sobreviva a un fallo del cambio, que es exactamente lo que no
 * se quiere.
 *
 * <h2>Qué NO debe pasar por aquí</h2>
 *
 * Claves SOL, contenido de certificados, contraseñas. El objeto que se pasa se
 * serializa entero. Ver DTE §8.4.
 */
@Service
public class ServicioAuditoria {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioAuditoria.class);

    /** Acciones habituales. No es un enumerado: los módulos añadirán las suyas. */
    public static final String CREAR = "crear";
    public static final String ACTUALIZAR = "actualizar";
    public static final String DESACTIVAR = "desactivar";
    public static final String ACTIVAR = "activar";
    public static final String ELIMINAR = "eliminar";

    private final AuditoriaRepository auditorias;
    private final ObjectMapper json;

    public ServicioAuditoria(AuditoriaRepository auditorias, ObjectMapper json) {
        this.auditorias = auditorias;
        this.json = json;
    }

    /** Alta: no hay estado anterior. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarCreacion(String entidad, UUID entidadId, Object despues) {
        registrar(entidad, entidadId, CREAR, null, despues);
    }

    /**
     * Modificación.
     *
     * <p>El estado anterior hay que capturarlo <strong>antes</strong> de tocar
     * la entidad. Si se pasa la misma instancia ya modificada, ambos lados
     * salen iguales y la anotación no dice nada — un fallo silencioso que solo
     * se detecta leyendo la bitácora meses después.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarActualizacion(String entidad, UUID entidadId, Object antes, Object despues) {
        registrar(entidad, entidadId, ACTUALIZAR, antes, despues);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String entidad, UUID entidadId, String accion, Object antes, Object despues) {
        ContextoPeticion contexto = ContextoActual.obtener().orElse(null);

        auditorias.save(new Auditoria(
                contexto == null ? null : contexto.empresaId(),
                contexto == null ? null : contexto.usuarioId(),
                entidad,
                entidadId,
                accion,
                aJson(antes),
                aJson(despues),
                contexto == null ? null : contexto.ip()));
    }

    /**
     * Serializa a JSON.
     *
     * <p>Un fallo de serialización guarda un marcador en vez de propagar. Es la
     * única concesión de esta clase, y tiene motivo: impedir que se guarde un
     * establecimiento porque uno de sus campos no supo convertirse a JSON sería
     * dejar que la bitácora bloquee la operación que documenta. Queda el rastro
     * de que algo pasó, con su error en el log, que es más que nada.
     */
    private String aJson(Object valor) {
        if (valor == null) {
            return null;
        }
        try {
            return json.writeValueAsString(valor);
        } catch (JacksonException e) {
            // En Jackson 3 las excepciones son NO comprobadas, así que el
            // compilador no obliga a este catch. Quitarlo dejaría que un fallo
            // de serialización tumbara la operación de negocio, que es
            // exactamente lo que el comentario de arriba dice que no debe pasar.
            LOG.error("No se pudo serializar el estado para la bitácora ({})",
                    valor.getClass().getName(), e);
            return "{\"_error\":\"no serializable\",\"_tipo\":\""
                    + valor.getClass().getName() + "\"}";
        }
    }
}
