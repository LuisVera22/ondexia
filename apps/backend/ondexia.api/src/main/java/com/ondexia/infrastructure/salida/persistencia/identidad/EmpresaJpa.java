package com.ondexia.infrastructure.salida.persistencia.identidad;

import com.ondexia.domain.consultas.CondicionDomicilio;
import com.ondexia.domain.consultas.EstadoContribuyente;
import com.ondexia.domain.identidad.ModoSunat;
import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Fila de {@code empresa}. */
@Entity
@Table(name = "empresa")
public class EmpresaJpa extends EntidadJpaBase {

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private UUID cuentaId;

    @Column(name = "ruc", nullable = false, unique = true, length = 11, updatable = false)
    private String ruc;

    @Column(name = "razon_social", nullable = false, length = 300)
    private String razonSocial;

    @Column(name = "nombre_comercial", length = 300)
    private String nombreComercial;

    @Column(name = "domicilio_fiscal", nullable = false, length = 400)
    private String domicilioFiscal;

    @Column(name = "ubigeo", length = 6)
    private String ubigeo;

    /** Referencia al secreto, jamás el certificado (DTE §8.2). */
    @Column(name = "secret_arn_certificado", length = 512)
    private String secretArnCertificado;

    @Column(name = "usuario_sol", length = 100)
    private String usuarioSol;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_sunat", nullable = false, length = 20)
    private ModoSunat modoSunat;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    // ── Lo que dice SUNAT (V12) ────────────────────────────────────────────
    //
    // Los tres primeros van juntos o no van: lo impone la restriccion
    // empresa_verificacion_completa. Un estado sin fecha es exactamente la
    // mentira que verificado_en existe para evitar.

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_contribuyente", length = 20)
    private EstadoContribuyente estadoContribuyente;

    @Enumerated(EnumType.STRING)
    @Column(name = "condicion_domicilio", length = 20)
    private CondicionDomicilio condicionDomicilio;

    @Column(name = "verificado_en")
    private Instant verificadoEn;

    @Column(name = "distrito", length = 100)
    private String distrito;

    @Column(name = "provincia", length = 100)
    private String provincia;

    @Column(name = "departamento", length = 100)
    private String departamento;

    @Column(name = "es_agente_retencion", nullable = false)
    private boolean esAgenteRetencion;

    @Column(name = "es_buen_contribuyente", nullable = false)
    private boolean esBuenContribuyente;

    @Column(name = "tipo_societario", length = 120)
    private String tipoSocietario;

    @Column(name = "cuenta_detracciones", length = 30)
    private String cuentaDetracciones;

    protected EmpresaJpa() {
    }

    public EmpresaJpa(UUID id, UUID cuentaId, String ruc, String razonSocial,
            String nombreComercial, String domicilioFiscal, String ubigeo,
            String secretArnCertificado, String usuarioSol, ModoSunat modoSunat, boolean activo) {
        this.id = id;
        this.cuentaId = cuentaId;
        this.ruc = ruc;
        actualizarDesde(razonSocial, nombreComercial, domicilioFiscal, ubigeo,
                secretArnCertificado, usuarioSol, modoSunat, activo);
    }

    public final void actualizarDesde(String razonSocial, String nombreComercial,
            String domicilioFiscal, String ubigeo, String secretArnCertificado, String usuarioSol,
            ModoSunat modoSunat, boolean activo) {
        this.razonSocial = razonSocial;
        this.nombreComercial = nombreComercial;
        this.domicilioFiscal = domicilioFiscal;
        this.ubigeo = ubigeo;
        this.secretArnCertificado = secretArnCertificado;
        this.usuarioSol = usuarioSol;
        this.modoSunat = modoSunat;
        this.activo = activo;
    }

    /**
     * Lo que vino del padrón, en un solo método.
     *
     * <p>Junto y no campo a campo porque la base exige que estado, condición y
     * fecha vayan los tres o ninguno. Con setters sueltos, poner dos y olvidar el
     * tercero compila, y falla al confirmar la transacción en un sitio que no
     * menciona ninguno de ellos.
     */
    public final void verificacionDeSunat(EstadoContribuyente estado,
            CondicionDomicilio condicion, Instant verificadoEn, String distrito,
            String provincia, String departamento, boolean esAgenteRetencion,
            boolean esBuenContribuyente, String tipoSocietario) {
        this.estadoContribuyente = estado;
        this.condicionDomicilio = condicion;
        this.verificadoEn = verificadoEn;
        this.distrito = distrito;
        this.provincia = provincia;
        this.departamento = departamento;
        this.esAgenteRetencion = esAgenteRetencion;
        this.esBuenContribuyente = esBuenContribuyente;
        this.tipoSocietario = tipoSocietario;
    }

    public void setCuentaDetracciones(String cuentaDetracciones) {
        this.cuentaDetracciones = cuentaDetracciones;
    }

    public EstadoContribuyente getEstadoContribuyente() {
        return estadoContribuyente;
    }

    public CondicionDomicilio getCondicionDomicilio() {
        return condicionDomicilio;
    }

    public Instant getVerificadoEn() {
        return verificadoEn;
    }

    public String getDistrito() {
        return distrito;
    }

    public String getProvincia() {
        return provincia;
    }

    public String getDepartamento() {
        return departamento;
    }

    public boolean isEsAgenteRetencion() {
        return esAgenteRetencion;
    }

    public boolean isEsBuenContribuyente() {
        return esBuenContribuyente;
    }

    public String getTipoSocietario() {
        return tipoSocietario;
    }

    public String getCuentaDetracciones() {
        return cuentaDetracciones;
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

    public boolean isActivo() {
        return activo;
    }
}
