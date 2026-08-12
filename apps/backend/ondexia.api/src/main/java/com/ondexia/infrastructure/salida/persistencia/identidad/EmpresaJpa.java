package com.ondexia.infrastructure.salida.persistencia.identidad;

import com.ondexia.domain.identidad.ModoSunat;
import com.ondexia.infrastructure.salida.persistencia.comun.EntidadJpaBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
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
