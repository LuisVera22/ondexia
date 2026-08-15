package com.ondexia.admin.cuentas;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * El rastro de lo que hacemos desde el panel.
 *
 * <h2>Por qué no escribe en {@code auditoria}</h2>
 *
 * <p>Esa tabla tiene política de fila por {@code empresa_id} y las acciones de
 * aquí son sobre una <strong>cuenta</strong>. Una fila con {@code empresa_id}
 * nulo no satisface {@code empresa_id = empresa_actual()} y la base la rechaza —
 * que es exactamente lo que debe hacer. Debilitar esa política para encajar algo
 * que no es de una empresa habría sido el error; de ahí {@code auditoria_admin}.
 *
 * <h2>Esto es la razón de ser del panel</h2>
 *
 * <p>Antes, cambiar el plan de un cliente era un {@code UPDATE} a mano contra
 * producción: ocurría y no quedaba constancia de quién ni cuándo. Sin este
 * registro, el panel sería lo mismo con una pantalla bonita delante.
 */
@Component
public class Bitacora {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public Bitacora(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * @param actor   quién lo hizo, tal como llega en el token del personal
     * @param antes   estado previo, o {@code null} si no lo había
     * @param despues estado resultante. <strong>Nunca credenciales ni contenido
     *                de certificados</strong> (DTE §8.4): aquí solo entran datos
     *                comerciales — plan, estado, módulos
     */
    public void registrar(UUID cuentaId, String actor, String accion,
            Map<String, Object> antes, Map<String, Object> despues) {

        jdbc.sql("""
                insert into auditoria_admin (id, cuenta_id, actor, accion, antes, despues)
                values (gen_random_uuid(), :cuenta, :actor, :accion,
                        cast(:antes as jsonb), cast(:despues as jsonb))
                """)
                .param("cuenta", cuentaId)
                .param("actor", actor)
                .param("accion", accion)
                .param("antes", serializar(antes))
                .param("despues", serializar(despues))
                .update();
    }

    /**
     * Serializa con Jackson, y no concatenando cadenas.
     *
     * <p>La primera versión construía el JSON a mano y escapaba solo comillas y
     * barras invertidas. Los <strong>caracteres de control no son válidos dentro
     * de una cadena JSON</strong>, así que un motivo tecleado con un salto de
     * línea producía un documento inválido: el {@code cast(… as jsonb)} lo
     * rechazaba y, al ir todo en la misma transacción, se perdía también el
     * cambio de plan. Bastaba con que alguien pegara un texto de dos líneas en el
     * campo «Motivo».
     *
     * <p>Escribir JSON a mano es de esas cosas que funcionan hasta que llega el
     * primer dato escrito por una persona.
     */
    private String serializar(Map<String, Object> valores) {
        return valores == null ? null : json.writeValueAsString(valores);
    }
}
