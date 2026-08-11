package com.ondexia.domain.identidad;

import com.ondexia.domain.comun.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * Contribuyente emisor. Es la unidad fiscal y el eje del aislamiento
 * multiempresa.
 *
 * <p>Su identificador aparece en <strong>toda</strong> tabla transaccional y de
 * catalogo (DTE §5.1). Una tabla sin {@code empresa_id} es una fuga entre
 * clientes esperando ocurrir.
 */
@Entity
@Table(name = "empresa")
public class Empresa extends EntidadBase {

    /**
     * Cuenta propietaria. Una cuenta puede tener varios RUC; un RUC pertenece a
     * una sola cuenta.
     */
    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private UUID cuentaId;

    /**
     * RUC, unico a nivel global y no por cuenta.
     *
     * <p>La unicidad global es deliberada y tiene consecuencia comercial: si dos
     * clientes distintos registran el mismo RUC, hay dos sistemas emitiendo
     * contra el mismo contribuyente y los correlativos se pisan. El segundo
     * registro debe fallar y escalar a soporte, no crear una empresa duplicada.
     *
     * <p>Once digitos. La validacion del digito verificador vive en el servicio,
     * no aqui: es un algoritmo, no una forma.
     */
    @Pattern(regexp = "\\d{11}", message = "El RUC debe tener 11 digitos")
    @Column(name = "ruc", nullable = false, unique = true, length = 11, updatable = false)
    private String ruc;

    @NotBlank
    @Column(name = "razon_social", nullable = false, length = 300)
    private String razonSocial;

    @Column(name = "nombre_comercial", length = 300)
    private String nombreComercial;

    @NotBlank
    @Column(name = "domicilio_fiscal", nullable = false, length = 400)
    private String domicilioFiscal;

    /** Codigo de ubigeo del domicilio fiscal (INEI, 6 digitos). */
    @Pattern(regexp = "\\d{6}", message = "El ubigeo debe tener 6 digitos")
    @Column(name = "ubigeo", length = 6)
    private String ubigeo;

    /**
     * ARN del secreto que guarda el certificado digital.
     *
     * <p><strong>Es una referencia, jamas el certificado.</strong> Un
     * {@code .pfx} en la base de datos aparece en cada respaldo, en cada volcado
     * de desarrollo y en cada consulta de soporte. El certificado vive en
     * Secrets Manager y solo {@code ondexia-facturacion} lo lee (DTE §8.2).
     */
    @Column(name = "secret_arn_certificado", length = 512)
    private String secretArnCertificado;

    /** Usuario SOL. La clave correspondiente vive en Secrets Manager. */
    @Column(name = "usuario_sol", length = 100)
    private String usuarioSol;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_sunat", nullable = false, length = 20)
    private ModoSunat modoSunat;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected Empresa() {
        // Requerido por JPA.
    }

    public Empresa(UUID cuentaId, String ruc, String razonSocial, String domicilioFiscal) {
        this.cuentaId = cuentaId;
        this.ruc = ruc;
        this.razonSocial = razonSocial;
        this.domicilioFiscal = domicilioFiscal;
        this.modoSunat = ModoSunat.BETA;
        this.activo = true;
    }

    public UUID getCuentaId() {
        return cuentaId;
    }

    public String getRuc() {
        return ruc;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public String getNombreComercial() {
        return nombreComercial;
    }

    public String getDomicilioFiscal() {
        return domicilioFiscal;
    }

    public String getUbigeo() {
        return ubigeo;
    }

    public String getSecretArnCertificado() {
        return secretArnCertificado;
    }

    public String getUsuarioSol() {
        return usuarioSol;
    }

    public ModoSunat getModoSunat() {
        return modoSunat;
    }

    public boolean estaActiva() {
        return activo;
    }

    public void actualizarDatosFiscales(String razonSocial, String nombreComercial,
            String domicilioFiscal, String ubigeo) {
        this.razonSocial = razonSocial;
        this.nombreComercial = nombreComercial;
        this.domicilioFiscal = domicilioFiscal;
        this.ubigeo = ubigeo;
    }

    public void configurarCredencialesSunat(String secretArnCertificado, String usuarioSol) {
        this.secretArnCertificado = secretArnCertificado;
        this.usuarioSol = usuarioSol;
    }

    /**
     * Pasa la empresa a emitir contra el entorno de produccion de SUNAT.
     *
     * <p>Sin certificado configurado no se permite: dejar pasar el cambio
     * produciria un rechazo en la primera emision real, que es el peor momento
     * para descubrirlo. Ver DTE §10.3 — la primera emision con certificado de
     * produccion es tambien el disparador de la transicion a Fase 2.
     */
    public void habilitarProduccion() {
        if (secretArnCertificado == null || secretArnCertificado.isBlank()) {
            throw new IllegalStateException(
                    "La empresa " + ruc + " no tiene certificado digital configurado");
        }
        this.modoSunat = ModoSunat.PRODUCCION;
    }
}
