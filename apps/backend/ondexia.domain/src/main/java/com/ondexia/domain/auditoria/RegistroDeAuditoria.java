package com.ondexia.domain.auditoria;

import java.util.List;
import java.util.UUID;

/**
 * Puerto: dejar constancia de un cambio.
 *
 * <p>El adaptador escribe <strong>dentro de la transacción del cambio</strong>,
 * y esa propiedad es la que hace que la bitácora sirva de algo:
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
 * <p>El objeto que se pasa se serializa entero: <strong>nada de claves SOL,
 * contenido de certificados ni contraseñas</strong> (DTE §8.4).
 */
public interface RegistroDeAuditoria {

    void registrarCreacion(String entidad, UUID entidadId, Object despues);

    /**
     * El estado anterior hay que capturarlo <strong>antes</strong> de tocar el
     * agregado. Si se pasa la misma instancia ya modificada, ambos lados salen
     * iguales y la anotación no dice nada — un fallo silencioso que solo se
     * descubre leyendo la bitácora meses después.
     */
    void registrarActualizacion(String entidad, UUID entidadId, Object antes, Object despues);

    void registrar(String entidad, UUID entidadId, String accion, Object antes, Object despues);

    List<Anotacion> historialDe(UUID empresaId, String entidad, UUID entidadId);
}
