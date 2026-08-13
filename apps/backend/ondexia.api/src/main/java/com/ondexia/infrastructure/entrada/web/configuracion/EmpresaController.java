package com.ondexia.infrastructure.entrada.web.configuracion;

import com.ondexia.application.configuracion.ActualizarEmpresa;
import com.ondexia.application.configuracion.ConsultarEmpresa;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Datos fiscales de la empresa activa.
 *
 * <p>No hay {@code POST}: las empresas se dan de alta desde la administración de
 * la cuenta, que es otra cosa —afecta a la suscripción y a lo que se factura— y
 * no la gobierna un permiso de configuración.
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
            summary = "Actualiza los datos fiscales",
            description = """
                    El RUC no se puede cambiar y por eso no está en el cuerpo. Cambiarlo no \
                    es corregir un dato: es decir que los comprobantes ya emitidos pertenecen \
                    a otro contribuyente.""")
    @RequierePermiso(modulo = "configuracion.empresa", accion = "editar")
    @PutMapping
    public RespuestaEmpresa actualizar(@Valid @RequestBody PeticionEmpresa peticion) {
        return RespuestaEmpresa.desde(actualizar.ejecutar(
                peticion.razonSocial(),
                peticion.nombreComercial(),
                peticion.domicilioFiscal(),
                peticion.ubigeo()));
    }

    /**
     * @param ubigeo seis dígitos, opcional mientras no haya integración con
     *               SUNAT, que es quien lo exige
     */
    public record PeticionEmpresa(
            @NotBlank(message = "La razón social es obligatoria.")
            @Size(max = 300)
            String razonSocial,

            @Size(max = 300)
            String nombreComercial,

            @NotBlank(message = "El domicilio fiscal es obligatorio.")
            @Size(max = 400)
            String domicilioFiscal,

            @Pattern(regexp = "^$|^\\d{6}$", message = "El ubigeo son seis dígitos.")
            String ubigeo) {
    }

    /**
     * @param modoSunat se expone aunque todavía no haya integración: el frontend
     *                  lo muestra como aviso de que la empresa está en pruebas
     */
    public record RespuestaEmpresa(
            java.util.UUID id,
            String ruc,
            String razonSocial,
            String nombreComercial,
            String domicilioFiscal,
            String ubigeo,
            String modoSunat,
            boolean activa) {

        static RespuestaEmpresa desde(Empresa empresa) {
            return new RespuestaEmpresa(
                    empresa.id(),
                    empresa.ruc().valor(),
                    empresa.razonSocial(),
                    empresa.nombreComercial(),
                    empresa.domicilioFiscal(),
                    empresa.ubigeo() == null ? null : empresa.ubigeo().valor(),
                    empresa.modoSunat().name(),
                    empresa.estaActiva());
        }
    }
}
