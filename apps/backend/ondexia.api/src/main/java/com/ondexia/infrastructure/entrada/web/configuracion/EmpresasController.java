package com.ondexia.infrastructure.entrada.web.configuracion;

import com.ondexia.application.configuracion.EmpresasDelUsuario;
import com.ondexia.application.configuracion.RegistrarEmpresa;
import com.ondexia.domain.identidad.LimitesDeCuenta;
import com.ondexia.infrastructure.entrada.web.configuracion.EmpresaController.RespuestaEmpresa;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
 * <p>El {@code POST} sí está aquí, y no en la administración de la cuenta como
 * decía antes este comentario. La razón por la que no estaba —«afecta a la
 * suscripción»— era cierta y no bastaba: el plan limita cuántas empresas caben,
 * pero registrar la que cabe es configuración normal, y ponerlo en otro sitio
 * dejaba el sistema sin ninguna forma de crear una segunda empresa. Lo que sí
 * hace el plan es decidir si el botón aparece, y para eso está
 * {@code GET /empresas/cupo}.
 */
@RestController
@RequestMapping("/api/v1/configuracion/empresas")
@Tag(name = "Empresa", description = "Datos fiscales de la empresa activa")
public class EmpresasController {

    private final EmpresasDelUsuario empresas;
    private final RegistrarEmpresa registro;

    public EmpresasController(EmpresasDelUsuario empresas, RegistrarEmpresa registro) {
        this.empresas = empresas;
        this.registro = registro;
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

    /**
     * Lo que llega del formulario, que es <strong>solo lo nuestro</strong>.
     *
     * <p>No hay razon social, ni domicilio, ni ubigeo, ni estado: vienen dentro
     * de la atestacion firmada. Por eso el «no editable» de esos campos no se
     * puede saltar — con las herramientas del navegador se cambia cualquier campo
     * de un formulario, lo que no se puede es firmar.
     *
     * @param atestacion lo que devolvio {@code GET /consultas/ruc/{ruc}}
     * @param cuentaDetracciones sin longitud fija: el formato del Banco de la
     *     Nacion no esta publicado, y un largo inventado rechazaria cuentas
     *     validas
     */
    public record PeticionAlta(
            @NotBlank(message = "Falta la verificacion del RUC.")
            String atestacion,

            @Size(max = 300)
            String nombreComercial,

            @Pattern(regexp = "[0-9]*", message = "La cuenta de detracciones solo lleva digitos.")
            @Size(max = 30)
            String cuentaDetracciones) {
    }

    /** El cupo del plan, para que la pantalla sepa si enseniar el boton. */
    public record RespuestaCupo(Integer maxEmpresas, int empresasUsadas, boolean cabeOtra,
            String motivo) {

        static RespuestaCupo desde(LimitesDeCuenta limites) {
            return new RespuestaCupo(limites.maxEmpresas(), limites.empresasUsadas(),
                    limites.cabeOtraEmpresa(), limites.motivoDelTope());
        }
    }

    @Operation(
            summary = "Registrar una empresa",
            description = """
                    Los datos de SUNAT salen de la atestacion firmada y no del cuerpo de la                     peticion: la API no puede consultar el padron —su Lambda no tiene salida a                     internet— asi que confia en la firma de ondexia.consultas. Responde 409 si                     el RUC ya esta registrado, 403 si quien llama no es administrador de la                     cuenta, y 400 si la atestacion caduco, si no es valida, si el RUC no esta                     habido activo, o si el plan no admite otra empresa.""")
    /*
     * Sin @RequierePermiso, y no es un olvido.
     *
     * Registrar una empresa no es un permiso de la matriz de roles: es cosa del
     * administrador de la cuenta, como decide la V2 al conceder permisos al rol
     * ADMINISTRADOR. La comprobacion vive dentro de RegistrarEmpresa para que
     * viaje con el caso de uso y no dependa de que cada entrada la repita.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaEmpresa registrar(@Valid @RequestBody PeticionAlta peticion) {
        return RespuestaEmpresa.desde(registro.ejecutar(new RegistrarEmpresa.Peticion(
                peticion.atestacion(), peticion.nombreComercial(),
                peticion.cuentaDetracciones())));
    }

    @Operation(
            summary = "Cuantas empresas admite el plan",
            description = """
                    `maxEmpresas` nulo significa sin limite, no cero: es el plan a demanda.                     Existe para que la pantalla no ofrezca un formulario que va a fallar al                     enviarse.""")
    @RequierePermiso(modulo = "configuracion.empresa", accion = "consultar")
    @GetMapping("/cupo")
    public RespuestaCupo cupo() {
        return RespuestaCupo.desde(registro.cupo());
    }
}
