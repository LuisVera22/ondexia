package com.ondexia.domain.identidad;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.Ubigeo;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.util.Objects;
import java.util.UUID;

/**
 * Contribuyente emisor. Unidad fiscal y eje del aislamiento multiempresa.
 *
 * <p>Su identificador aparece en <strong>toda</strong> tabla transaccional
 * (DTE §5.1). Una tabla sin {@code empresa_id} es una fuga entre clientes
 * esperando ocurrir.
 *
 * <p>Agregado puro: sin anotaciones de persistencia. Su representación en base
 * de datos es {@code EmpresaJpa}, en infraestructura.
 */
public class Empresa {

    private final UUID id;
    private final UUID cuentaId;
    private final Ruc ruc;

    private String razonSocial;
    private String nombreComercial;
    private String domicilioFiscal;
    private Ubigeo ubigeo;
    private String secretArnCertificado;
    private String usuarioSol;
    private ModoSunat modoSunat;
    private boolean activa;

    /**
     * Alta.
     *
     * <p>El RUC llega como {@link Ruc}, no como cadena: quien construye una
     * empresa ya ha tenido que superar la validación del dígito verificador.
     * Aquí no hace falta volver a comprobarlo, y ese es el punto.
     */
    public Empresa(UUID id, UUID cuentaId, Ruc ruc, String razonSocial, String domicilioFiscal) {
        this.id = Objects.requireNonNull(id, "id");
        this.cuentaId = Objects.requireNonNull(cuentaId, "cuentaId");
        this.ruc = Objects.requireNonNull(ruc, "ruc");
        this.razonSocial = exigirTexto(razonSocial, "razon_social", "La razón social");
        this.domicilioFiscal = exigirTexto(domicilioFiscal, "domicilio_fiscal", "El domicilio fiscal");
        this.modoSunat = ModoSunat.BETA;
        this.activa = true;
    }

    /** Reconstrucción desde persistencia. Solo lo usa el mapeador. */
    public Empresa(UUID id, UUID cuentaId, Ruc ruc, String razonSocial, String nombreComercial,
            String domicilioFiscal, Ubigeo ubigeo, String secretArnCertificado, String usuarioSol,
            ModoSunat modoSunat, boolean activa) {
        this.id = id;
        this.cuentaId = cuentaId;
        this.ruc = ruc;
        this.razonSocial = razonSocial;
        this.nombreComercial = nombreComercial;
        this.domicilioFiscal = domicilioFiscal;
        this.ubigeo = ubigeo;
        this.secretArnCertificado = secretArnCertificado;
        this.usuarioSol = usuarioSol;
        this.modoSunat = modoSunat;
        this.activa = activa;
    }

    public UUID id() {
        return id;
    }

    public UUID cuentaId() {
        return cuentaId;
    }

    public Ruc ruc() {
        return ruc;
    }

    public String razonSocial() {
        return razonSocial;
    }

    public String nombreComercial() {
        return nombreComercial;
    }

    public String domicilioFiscal() {
        return domicilioFiscal;
    }

    public Ubigeo ubigeo() {
        return ubigeo;
    }

    public String secretArnCertificado() {
        return secretArnCertificado;
    }

    public String usuarioSol() {
        return usuarioSol;
    }

    public ModoSunat modoSunat() {
        return modoSunat;
    }

    public boolean estaActiva() {
        return activa;
    }

    public void actualizarDatosFiscales(String razonSocial, String nombreComercial,
            String domicilioFiscal, Ubigeo ubigeo) {
        this.razonSocial = exigirTexto(razonSocial, "razon_social", "La razón social");
        this.domicilioFiscal = exigirTexto(domicilioFiscal, "domicilio_fiscal", "El domicilio fiscal");
        this.nombreComercial = nombreComercial;
        this.ubigeo = ubigeo;
    }

    /**
     * Referencia al secreto, jamás el certificado.
     *
     * <p>Un {@code .pfx} guardado aquí aparecería en cada respaldo, en cada
     * volcado de desarrollo y en cada consulta de soporte. El certificado vive
     * en Secrets Manager (DTE §8.2).
     */
    public void configurarCredencialesSunat(String secretArnCertificado, String usuarioSol) {
        this.secretArnCertificado = secretArnCertificado;
        this.usuarioSol = usuarioSol;
    }

    /**
     * Pasa a emitir contra el entorno de producción de SUNAT.
     *
     * <p>Sin certificado no se permite: dejarlo pasar produciría un rechazo en
     * la primera emisión real, que es el peor momento para descubrirlo.
     */
    public void habilitarProduccion() {
        if (secretArnCertificado == null || secretArnCertificado.isBlank()) {
            throw new ReglaDeNegocioViolada(
                    "sin_certificado",
                    "La empresa " + ruc + " no tiene certificado digital configurado.");
        }
        this.modoSunat = ModoSunat.PRODUCCION;
    }

    public void desactivar() {
        this.activa = false;
    }

    public void activar() {
        this.activa = true;
    }

    private static String exigirTexto(String valor, String codigo, String queEs) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaDeNegocioViolada(codigo + "_requerido", queEs + " es obligatorio.");
        }
        return valor.trim();
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Empresa otra && id.equals(otra.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
