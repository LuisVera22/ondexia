package com.ondexia.infrastructure.salida.auditoria;

import com.ondexia.domain.auditoria.Anotacion;
import com.ondexia.infrastructure.salida.persistencia.auditoria.AnotacionJpa;
import com.ondexia.infrastructure.salida.persistencia.auditoria.AnotacionJpaRepository;
import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comun.ContextoOperacion;
import com.ondexia.domain.comun.ProveedorDeContexto;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Adaptador del puerto {@link RegistroDeAuditoria}: escribe en la tabla.
 *
 * <p>{@code Propagation.MANDATORY} <strong>exige</strong> una transacción ya
 * abierta y falla si no la hay. Es lo que hace que la bitácora sirva de algo: si
 * el cambio se deshace, la anotación se deshace con él; y si la anotación falla,
 * el cambio se deshace también.
 *
 * <p>La alternativa —{@code REQUIRES_NEW}, o un evento asíncrono— haría que la
 * bitácora sobreviviera a un fallo del cambio, que es exactamente lo que no se
 * quiere.
 *
 * <p>Jackson 3, no Jackson 2: Spring Boot 4 trae {@code tools.jackson}. Con los
 * imports de la versión 2 el código compila —springdoc la arrastra— y luego no
 * encuentra el bean al arrancar.
 */
@Component
public class RegistroDeAuditoriaJpa implements RegistroDeAuditoria {

    private static final Logger LOG = LoggerFactory.getLogger(RegistroDeAuditoriaJpa.class);

    private final AnotacionJpaRepository filas;
    private final ProveedorDeContexto contexto;
    private final ObjectMapper json;

    public RegistroDeAuditoriaJpa(AnotacionJpaRepository filas, ProveedorDeContexto contexto,
            ObjectMapper json) {
        this.filas = filas;
        this.contexto = contexto;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarCreacion(String entidad, UUID entidadId, Object despues) {
        registrar(entidad, entidadId, Anotacion.CREAR, null, despues);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarActualizacion(String entidad, UUID entidadId, Object antes,
            Object despues) {
        registrar(entidad, entidadId, Anotacion.ACTUALIZAR, antes, despues);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String entidad, UUID entidadId, String accion, Object antes,
            Object despues) {
        ContextoOperacion actual = contexto.actual().orElse(null);

        filas.save(new AnotacionJpa(
                UUID.randomUUID(),
                actual == null ? null : actual.empresaId(),
                actual == null ? null : actual.usuarioId(),
                entidad,
                entidadId,
                accion,
                aJson(antes),
                aJson(despues),
                actual == null ? null : actual.ip()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Anotacion> historialDe(UUID empresaId, String entidad, UUID entidadId) {
        return filas.findByEmpresaIdAndEntidadAndEntidadIdOrderByCreadoEnDesc(
                        empresaId, entidad, entidadId).stream()
                .map(fila -> new Anotacion(fila.getId(), fila.getEmpresaId(), fila.getUsuarioId(),
                        fila.getEntidad(), fila.getEntidadId(), fila.getAccion(),
                        fila.getDatosAntes(), fila.getDatosDespues(), fila.getIp()))
                .toList();
    }

    /**
     * Solo records: nunca un agregado del dominio (hallazgo M8).
     *
     * <p>{@code registrar} acepta {@code Object}, y eso invita a pasarle la
     * entidad entera «para que quede todo». La entidad {@code Empresa} lleva
     * {@code usuarioSol} y el ARN del certificado; {@code Usuario} lleva el
     * {@code sub} de Cognito. Serializar cualquiera de ellas dejaría esos campos
     * en {@code datos_antes} y {@code datos_despues} para siempre, en una tabla
     * que se lee desde soporte.
     *
     * <p>Todos los casos de uso pasan hoy un {@code record} de instantánea
     * escrito a mano con los campos que se quieren guardar. Esta comprobación
     * hace que seguir haciéndolo no sea una costumbre sino una condición: un
     * agregado revienta aquí, en la prueba del caso de uso que lo intente, y no
     * en producción meses después.
     */
    private static void exigirInstantanea(Object valor) {
        if (!valor.getClass().isRecord()) {
            throw new IllegalArgumentException(
                    "La bitácora solo admite instantáneas (records), no " + valor.getClass().getName()
                            + ". Un agregado del dominio puede llevar credenciales dentro.");
        }
    }

    /**
     * Un fallo de serialización guarda un marcador en vez de propagar. Es la
     * única concesión: impedir que se guarde un establecimiento porque uno de
     * sus campos no supo convertirse a JSON sería dejar que la bitácora bloquee
     * la operación que documenta.
     */
    private String aJson(Object valor) {
        if (valor == null) {
            return null;
        }
        exigirInstantanea(valor);
        try {
            return json.writeValueAsString(valor);
        } catch (JacksonException e) {
            LOG.error("No se pudo serializar el estado para la bitacora ({})",
                    valor.getClass().getName(), e);
            return "{\"_error\":\"no serializable\",\"_tipo\":\""
                    + valor.getClass().getName() + "\"}";
        }
    }
}
