package com.ondexia.infrastructure.entrada.web.configuracion;

import com.ondexia.application.configuracion.ActualizarEmpresa;
import com.ondexia.application.configuracion.ConsultarEmpresa;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.identidad.RegimenTributario;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Datos fiscales de la empresa activa.
 *
 * <p>El alta está en {@link EmpresasController}; aquí solo se edita la activa.
 *
 * <p>El listado y la ficha de las <em>demás</em> empresas del usuario están en
 * {@link EmpresasController}, y son de solo lectura. Escribir sigue estando
 * aquí, sobre la activa, porque es la única empresa bajo la que la bitácora
 * puede archivar el cambio.
 */
@RestController
@RequestMapping("/api/v1/configuracion/empresa")
@Tag(name = "Empresa", description = "Datos fiscales de la empresa activa")
public class EmpresaController {

    private final ConsultarEmpresa consultar;
    private final ActualizarEmpresa actualizar;

    public EmpresaController(ConsultarEmpresa consultar, ActualizarEmpresa actualizar) {
        this.consultar = consultar;
        this.actualizar = actualizar;
    }

    @Operation(summary = "Datos de la empresa activa")
    @RequierePermiso(modulo = "configuracion.empresa", accion = "consultar")
    @GetMapping
    public RespuestaEmpresa consultar() {
        return RespuestaEmpresa.desde(consultar.ejecutar());
    }

    @Operation(
            summary = "Actualiza lo editable de la empresa",
            description = """
                    Solo el nombre comercial y la cuenta de detracciones, que son los unicos \
                    campos que no vienen de SUNAT. La razon social, el domicilio y el ubigeo \
                    no estan en el cuerpo: un valor distinto del padron hace que SUNAT rechace \
                    todos los comprobantes de la empresa. Para traerlos del padron esta \
                    POST /empresa/verificacion.""")
    @RequierePermiso(modulo = "configuracion.empresa", accion = "editar")
    @PutMapping
    public RespuestaEmpresa actualizar(@Valid @RequestBody PeticionEmpresa peticion) {
        return RespuestaEmpresa.desde(actualizar.ejecutar(
                peticion.nombreComercial(), peticion.cuentaDetracciones(),
                peticion.regimenTributario(), peticion.permiteVentaSinStock()));
    }

    @Operation(
            summary = "Trae del padron los datos que no se pueden editar",
            description = """
                    Sirve para dos cosas: refrescar una empresa cuando su razon social cambia \
                    en SUNAT, y verificar por primera vez una empresa creada en el onboarding, \
                    cuyos datos los teclearon a mano. Responde 400 si la atestacion no es \
                    valida, si caduco, o si es de un RUC distinto al de la empresa activa.""")
    @RequierePermiso(modulo = "configuracion.empresa", accion = "editar")
    @PostMapping("/verificacion")
    public RespuestaEmpresa verificar(@Valid @RequestBody PeticionVerificacion peticion) {
        return RespuestaEmpresa.desde(actualizar.refrescarDesdeSunat(peticion.atestacion()));
    }

    /**
     * Lo editable, que es solo lo nuestro.
     *
     * <p>Un cuerpo que traiga tambien {@code razonSocial} no falla: Jackson
     * ignora lo que el record no declara. Es deliberado — la garantia es que el
     * caso de uso no ofrece ninguna via, no que alguien recuerde validarlo.
     *
     * @param cuentaDetracciones sin longitud fija: el formato del Banco de la
     *     Nacion no esta publicado, y un largo inventado rechazaria cuentas
     *     validas. Vacio la borra
     */
    public record PeticionEmpresa(
            @Size(max = 300)
            String nombreComercial,

            @Pattern(regexp = "[0-9]*", message = "La cuenta de detracciones solo lleva digitos.")
            @Size(max = 30)
            String cuentaDetracciones,

            /** {@code null} = no cambiarlo. Ver {@link RegimenTributario}. */
            RegimenTributario regimenTributario,

            /** {@code null} = no cambiarlo. Si el mostrador vende con existencias insuficientes. */
            Boolean permiteVentaSinStock) {
    }

    public record PeticionVerificacion(
            @NotBlank(message = "Falta la verificacion del RUC.")
            String atestacion) {
    }

    /**
     * @param modoSunat se expone aunque todavía no haya integración: el frontend
     *                  lo muestra como aviso de que la empresa está en pruebas
     * @param verificadoEn {@code null} significa <strong>nunca se comprobó</strong>,
     *                  que no es lo mismo que «está mal». La interfaz necesita
     *                  distinguirlo para ofrecer comprobarlo en vez de acusar
     * @param editable qué campos acepta el {@code PUT}. Va calculado desde aquí
     *                  para que el formulario no tenga que repetir la regla —
     *                  dos implementaciones de qué es editable acabarían
     *                  discrepando, y la que manda es esta
     */
    public record RespuestaEmpresa(
            java.util.UUID id,
            String ruc,
            String razonSocial,
            String nombreComercial,
            String domicilioFiscal,
            String ubigeo,
            String distrito,
            String provincia,
            String departamento,
            String estado,
            String condicion,
            String verificadoEn,
            String tipoSocietario,
            boolean esAgenteRetencion,
            boolean esBuenContribuyente,
            String cuentaDetracciones,
            String regimenTributario,
            boolean emiteFacturas,
            boolean permiteVentaSinStock,
            String modoSunat,
            boolean activa,
            java.util.List<String> editable) {

        /** Los únicos campos que son nuestros y no de SUNAT. */
        private static final java.util.List<String> EDITABLE = java.util.List.of(
                "nombreComercial", "cuentaDetracciones", "regimenTributario", "permiteVentaSinStock");

        static RespuestaEmpresa desde(Empresa empresa) {
            var verificacion = empresa.verificacion();
            return new RespuestaEmpresa(
                    empresa.id(),
                    empresa.ruc().valor(),
                    empresa.razonSocial(),
                    empresa.nombreComercial(),
                    empresa.domicilioFiscal(),
                    empresa.ubigeo() == null ? null : empresa.ubigeo().valor(),
                    verificacion == null ? null : verificacion.distrito(),
                    verificacion == null ? null : verificacion.provincia(),
                    verificacion == null ? null : verificacion.departamento(),
                    verificacion == null ? null : verificacion.estado().name(),
                    verificacion == null ? null : verificacion.condicion().name(),
                    verificacion == null ? null : verificacion.verificadoEn().toString(),
                    verificacion == null ? null : verificacion.tipoSocietario(),
                    verificacion != null && verificacion.esAgenteRetencion(),
                    verificacion != null && verificacion.esBuenContribuyente(),
                    empresa.cuentaDetracciones(),
                    empresa.regimen().name(),
                    empresa.emiteFacturas(),
                    empresa.permiteVentaSinStock(),
                    empresa.modoSunat().name(),
                    empresa.estaActiva(),
                    EDITABLE);
        }
    }
}
