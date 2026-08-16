package com.ondexia.infrastructure.entrada.web.configuracion;

import com.ondexia.application.configuracion.EmpresasDelUsuario;
import com.ondexia.infrastructure.entrada.web.configuracion.EmpresaController.RespuestaEmpresa;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las empresas que el usuario alcanza, para el listado de configuración.
 *
 * <h2>Solo lectura</h2>
 *
 * <p>No hay {@code PUT} aquí. Los datos fiscales se cambian en
 * {@link EmpresaController}, que opera sobre la empresa <em>activa</em> y no
 * recibe id. No es una omisión pendiente de completar: la bitácora archiva cada
 * anotación bajo la empresa activa —y su política de aislamiento solo admite
 * ese valor—, así que un {@code PUT /empresas/{id}} guardaría el cambio de una
 * empresa en el historial de otra sin dar ningún error. La pantalla lo refleja:
 * la ficha de una empresa que no es la activa se muestra en solo lectura, y
 * para editarla hay que pasar a trabajar en ella.
 *
 * <p>Tampoco hay {@code POST}, por lo mismo que en {@link EmpresaController}:
 * dar de alta una empresa afecta a la suscripción y se hace desde la
 * administración de la cuenta.
 */
@RestController
@RequestMapping("/api/v1/configuracion/empresas")
@Tag(name = "Empresa", description = "Datos fiscales de la empresa activa")
public class EmpresasController {

    private final EmpresasDelUsuario empresas;

    public EmpresasController(EmpresasDelUsuario empresas) {
        this.empresas = empresas;
    }

    @Operation(
            summary = "Empresas que alcanza el usuario",
            description = """
                    Las de la cuenta a las que tiene asignación, que son las mismas que ofrece \
                    el selector de contexto. No las de la cuenta entera: dos usuarios de la \
                    misma cuenta pueden alcanzar empresas distintas.""")
    @RequierePermiso(modulo = "configuracion.empresa", accion = "consultar")
    @GetMapping
    public List<RespuestaEmpresa> listar() {
        return empresas.listar().stream().map(RespuestaEmpresa::desde).toList();
    }

    @Operation(
            summary = "Datos de una empresa del usuario",
            description = """
                    Responde 403 si el id no es de una empresa que el usuario alcance, y 404 si \
                    no existe. Ninguno de los dos mensajes distingue «no es tuya» de «no \
                    existe» más de lo imprescindible.""")
    @RequierePermiso(modulo = "configuracion.empresa", accion = "consultar")
    @GetMapping("/{id}")
    public RespuestaEmpresa consultar(@PathVariable UUID id) {
        return RespuestaEmpresa.desde(empresas.exigirAcceso(id));
    }
}
