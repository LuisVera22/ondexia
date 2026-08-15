package com.ondexia.admin.cuentas;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

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

    public Bitacora(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param actor  quién lo hizo, tal como llega en el token del personal
     * @param antes  estado previo en JSON, o {@code null} si no lo había
     * @param despues estado resultante. <strong>Nunca credenciales ni contenido
     *                de certificados</strong> (DTE §8.4): aquí solo entran datos
     *                comerciales — plan, estado, módulos
     */
    public void registrar(UUID cuentaId, String actor, String accion,
            String antes, String despues) {
        jdbc.sql("""
                insert into auditoria_admin (id, cuenta_id, actor, accion, antes, despues)
                values (gen_random_uuid(), :cuenta, :actor, :accion,
                        cast(:antes as jsonb), cast(:despues as jsonb))
                """)
                .param("cuenta", cuentaId)
                .param("actor", actor)
                .param("accion", accion)
                .param("antes", antes)
                .param("despues", despues)
                .update();
    }
}
