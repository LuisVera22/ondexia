package com.ondexia.infrastructure.entrada.web.configuracion;

import com.ondexia.application.emision.ConfiguracionDeEmision;
import com.ondexia.domain.identidad.CertificadoDigital;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.identidad.ModoSunat;
import com.ondexia.infrastructure.seguridad.RequierePermiso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Certificado digital, clave SOL y entorno de SUNAT de la empresa activa
 * (doc 14 §4).
 *
 * <p>Tres pasos desde la pantalla: pedir las URL de subida, subir los dos
 * archivos directo al bucket, confirmar. Ni el {@code .pfx} ni la contraseña
 * pasan por esta API.
 */
@RestController
@RequestMapping("/api/v1/configuracion/empresa/emision")
@Tag(name = "Emisión electrónica", description = "Certificado digital, clave SOL y entorno de SUNAT")
public class EmisionController {

    private final ConfiguracionDeEmision configuracion;

    public EmisionController(ConfiguracionDeEmision configuracion) {
        this.configuracion = configuracion;
    }

    @Operation(
            summary = "Cómo está la emisión electrónica de la empresa",
            description = "Aplica el resultado de la verificación del certificado si el Emisor ya respondió.")
    @RequierePermiso(modulo = "configuracion.empresa", accion = "consultar")
    @GetMapping
    public RespuestaEmision estado() {
        return RespuestaEmision.desde(configuracion.estado());
    }

    @Operation(
            summary = "URL prefirmadas para subir el certificado y las credenciales",
            description = """
                    Dos URL de PUT, válidas cinco minutos: una para el .pfx (application/x-pkcs12) \
                    y otra para un JSON {"claveCertificado": ..., "claveSol": ...} \
                    (application/json). El navegador sube los dos y después llama a PUT /carga.""")
    @RequierePermiso(modulo = "configuracion.empresa", accion = "editar")
    @PostMapping("/carga")
    public RespuestaAutorizacion autorizarCarga() {
        var a = configuracion.autorizarCarga();
        return new RespuestaAutorizacion(a.urlCertificado(), a.urlCredenciales(),
                a.claveCertificado(), a.claveCredenciales(), a.validez().toSeconds());
    }

    @Operation(
            summary = "Confirma la carga y encola la verificación del certificado",
            description = """
                    Comprueba que los dos objetos existen en el bucket; responde 400 si falta \
                    alguno. El Emisor abrirá el certificado y el resultado aparece en GET.""")
    @RequierePermiso(modulo = "configuracion.empresa", accion = "editar")
    @PutMapping("/carga")
    public RespuestaEmision confirmarCarga(@Valid @RequestBody PeticionConfirmacion peticion) {
        return RespuestaEmision.desde(configuracion.confirmarCarga(peticion.usuarioSol()));
    }

    @Operation(
            summary = "Cambia entre la beta y producción de SUNAT",
            description = """
                    Producción exige un certificado verificado y vigente. Lo que se emita en \
                    producción tiene valor tributario; lo de la beta, no.""")
    @RequierePermiso(modulo = "configuracion.empresa", accion = "editar")
    @PutMapping("/modo")
    public RespuestaEmision cambiarModo(@Valid @RequestBody PeticionModo peticion) {
        return RespuestaEmision.desde(configuracion.cambiarModo(peticion.modo()));
    }

    public record PeticionConfirmacion(
            @NotBlank(message = "El usuario SOL es obligatorio.")
            @Size(max = 100)
            String usuarioSol) {
    }

    public record PeticionModo(@NotNull(message = "Indica BETA o PRODUCCION.") ModoSunat modo) {
    }

    public record RespuestaAutorizacion(String urlCertificado, String urlCredenciales,
            String claveCertificado, String claveCredenciales, long validezSegundos) {
    }

    /**
     * @param puedeEmitir si una boleta o factura saldría hacia SUNAT ahora
     * @param certificadoVerificadoEn nulo mientras el Emisor no haya abierto el archivo
     * @param certificadoError por qué no abrió, si fue el caso
     */
    public record RespuestaEmision(String modoSunat, String usuarioSol, boolean puedeEmitir,
            Instant certificadoCargadoEn, Instant certificadoVerificadoEn, String certificadoSujeto,
            LocalDate certificadoVenceEn, String certificadoError) {

        static RespuestaEmision desde(Empresa e) {
            CertificadoDigital c = e.certificado();
            return new RespuestaEmision(e.modoSunat().name(), e.usuarioSol(),
                    e.puedeEmitirElectronicamente(),
                    c == null ? null : c.cargadoEn(), c == null ? null : c.verificadoEn(),
                    c == null ? null : c.sujeto(), c == null ? null : c.venceEn(),
                    c == null ? null : c.error());
        }
    }
}
