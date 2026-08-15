package com.ondexia.admin.cuentas;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Los cambios que el panel puede hacer sobre una cuenta.
 *
 * <h2>Cada cambio invalida la caché de permisos</h2>
 *
 * <p>La API de clientes cachea los permisos efectivos en memoria del contenedor
 * y decide si están rancios comparando {@code cuenta.permisos_version}. Si aquí
 * se apagara un módulo sin incrementar esa versión, el cliente <strong>seguiría
 * entrando</strong> hasta que a Lambda le diera por reciclar el contenedor: un
 * corte de acceso que ocurre horas después y de forma aleatoria, que es la peor
 * versión posible de un control de acceso.
 *
 * <p>Por eso el incremento va en la misma transacción que el cambio. Si algo
 * falla, no queda ni el cambio ni la versión a medias.
 */
@Service
public class GestionDeCuentas {

    private final JdbcClient jdbc;
    private final Bitacora bitacora;

    public GestionDeCuentas(JdbcClient jdbc, Bitacora bitacora) {
        this.jdbc = jdbc;
        this.bitacora = bitacora;
    }

    private static final List<String> ESTADOS =
            List.of("EN_PRUEBA", "ACTIVA", "SUSPENDIDA", "CANCELADA");

    @Transactional
    public void cambiarPlan(UUID cuentaId, String planCodigo, String actor) {
        String anterior = planActual(cuentaId);

        if (!existePlan(planCodigo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El plan " + planCodigo + " no existe.");
        }

        jdbc.sql("update cuenta set plan = :plan, actualizado_en = now() where id = :id")
                .param("plan", planCodigo).param("id", cuentaId).update();

        invalidarCacheDePermisos(cuentaId);
        bitacora.registrar(cuentaId, actor, "CAMBIO_DE_PLAN",
                "{\"plan\":\"" + anterior + "\"}", "{\"plan\":\"" + planCodigo + "\"}");
    }

    /**
     * Suspender o reactivar.
     *
     * <p>Suspender <strong>no</strong> borra nada ni cierra el acceso a los
     * datos: la API de clientes deja entrar en solo lectura. Es la decisión del
     * doc 09 §5.1, y el motivo es que los comprobantes tienen conservación
     * obligatoria de cinco años y quien responde por ellos ante SUNAT es el
     * cliente.
     */
    @Transactional
    public void cambiarEstado(UUID cuentaId, String estado, String motivo, String actor) {
        if (!ESTADOS.contains(estado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Estado no valido: " + estado);
        }

        String anterior = jdbc.sql("select estado_suscripcion from cuenta where id = :id")
                .param("id", cuentaId).query(String.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe la cuenta " + cuentaId));

        jdbc.sql("""
                update cuenta set estado_suscripcion = :estado, actualizado_en = now()
                where id = :id
                """)
                .param("estado", estado).param("id", cuentaId).update();

        // La suspension recorta permisos en la API de clientes, asi que tambien
        // tiene que surtir efecto ya.
        invalidarCacheDePermisos(cuentaId);

        bitacora.registrar(cuentaId, actor, "CAMBIO_DE_ESTADO",
                "{\"estado\":\"" + anterior + "\"}",
                "{\"estado\":\"" + estado + "\",\"motivo\":" + comoJson(motivo) + "}");
    }

    /**
     * Enciende o apaga un módulo para una cuenta.
     *
     * @param habilitado {@code null} <strong>borra la decisión</strong> y devuelve
     *                   la cuenta a lo que dicte su plan. Es distinto de apagarlo:
     *                   una cuenta sin fila hereda los módulos nuevos que se
     *                   añadan al plan; una con fila en {@code false} no
     */
    @Transactional
    public void decidirModulo(UUID cuentaId, UUID permisoId, Boolean habilitado,
            String motivo, String actor) {

        String nivel = jdbc.sql("select nivel from permiso where id = :id")
                .param("id", permisoId).query(String.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe el permiso " + permisoId));

        if (!List.of("MODULO", "SUBMODULO").contains(nivel)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se contratan modulos y submodulos, no funciones sueltas.");
        }

        jdbc.sql("delete from cuenta_modulo where cuenta_id = :cuenta and permiso_id = :permiso")
                .param("cuenta", cuentaId).param("permiso", permisoId).update();

        if (habilitado != null) {
            jdbc.sql("""
                    insert into cuenta_modulo
                        (cuenta_id, permiso_id, nivel, habilitado, motivo, decidido_por)
                    values (:cuenta, :permiso, :nivel, :habilitado, :motivo, :actor)
                    """)
                    .param("cuenta", cuentaId).param("permiso", permisoId)
                    .param("nivel", nivel).param("habilitado", habilitado)
                    .param("motivo", motivo).param("actor", actor)
                    .update();
        }

        invalidarCacheDePermisos(cuentaId);

        bitacora.registrar(cuentaId, actor, "DECISION_DE_MODULO", null,
                "{\"permiso\":\"" + permisoId + "\",\"habilitado\":" + habilitado
                        + ",\"motivo\":" + comoJson(motivo) + "}");
    }

    private void invalidarCacheDePermisos(UUID cuentaId) {
        jdbc.sql("""
                update cuenta set permisos_version = permisos_version + 1
                where id = :id
                """).param("id", cuentaId).update();
    }

    private String planActual(UUID cuentaId) {
        return jdbc.sql("select plan from cuenta where id = :id")
                .param("id", cuentaId).query(String.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe la cuenta " + cuentaId));
    }

    private boolean existePlan(String codigo) {
        return jdbc.sql("select count(*) from plan where codigo = :c and activo")
                .param("c", codigo).query(Integer.class).single() > 0;
    }

    /** Escapa lo justo: el motivo lo escribe una persona y puede traer comillas. */
    private static String comoJson(String texto) {
        if (texto == null) {
            return "null";
        }
        return "\"" + texto.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
