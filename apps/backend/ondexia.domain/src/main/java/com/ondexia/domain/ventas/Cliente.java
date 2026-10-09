package com.ondexia.domain.ventas;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un adquirente identificado: la persona o empresa a la que se le emite.
 *
 * <p>El documento no cambia después del alta: identifica al cliente ante SUNAT
 * y ya está impreso en sus comprobantes. Si se tecleó mal, se crea otro.
 *
 * <p>{@code verificadoEn} dice cuándo se comprobó el RUC contra el padrón. Un
 * RUC sin verificar se admite —el servicio de consulta puede no estar— pero la
 * pantalla lo dice, y la factura de la iteración 4 lo volverá a comprobar.
 */
public class Cliente {

    private final UUID id;
    private final UUID empresaId;
    private final TipoDocumentoIdentidad tipoDocumento;
    private final String numeroDocumento;
    private String nombre;
    private String direccion;
    private String correo;
    private String telefono;
    private Instant verificadoEn;
    private boolean activo;

    public Cliente(UUID id, UUID empresaId, TipoDocumentoIdentidad tipoDocumento,
            String numeroDocumento, String nombre, String direccion, String correo,
            String telefono) {
        this.id = Objects.requireNonNull(id, "id");
        this.empresaId = Objects.requireNonNull(empresaId, "empresaId");
        this.tipoDocumento = Objects.requireNonNull(tipoDocumento, "tipoDocumento");
        this.numeroDocumento = tipoDocumento.normalizar(numeroDocumento);
        this.activo = true;
        actualizar(nombre, direccion, correo, telefono);
    }

    /** Reconstrucción desde la persistencia: no valida. */
    public Cliente(UUID id, UUID empresaId, TipoDocumentoIdentidad tipoDocumento,
            String numeroDocumento, String nombre, String direccion, String correo,
            String telefono, Instant verificadoEn, boolean activo) {
        this.id = id;
        this.empresaId = empresaId;
        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.nombre = nombre;
        this.direccion = direccion;
        this.correo = correo;
        this.telefono = telefono;
        this.verificadoEn = verificadoEn;
        this.activo = activo;
    }

    public void actualizar(String nombre, String direccion, String correo, String telefono) {
        this.nombre = exigirNombre(nombre);
        this.direccion = limpiar(direccion, 300, "direccion", "La dirección admite hasta 300 caracteres.");
        this.correo = limpiarCorreo(correo);
        this.telefono = limpiar(telefono, 30, "telefono", "El teléfono admite hasta 30 caracteres.");
    }

    /** Lo que dijo el padrón manda sobre lo tecleado: razón social y domicilio. */
    public void verificarConPadron(String razonSocial, String domicilioFiscal, Instant ahora) {
        if (tipoDocumento != TipoDocumentoIdentidad.RUC) {
            throw new ReglaDeNegocioViolada(
                    "sin_ruc", "Solo un cliente con RUC se verifica contra el padrón.");
        }
        this.nombre = exigirNombre(razonSocial);
        if (domicilioFiscal != null && !domicilioFiscal.isBlank()) {
            this.direccion = limpiar(domicilioFiscal, 300, "direccion", "La dirección admite hasta 300 caracteres.");
        }
        this.verificadoEn = Objects.requireNonNull(ahora, "ahora");
    }

    public void desactivar() {
        this.activo = false;
    }

    public void activar() {
        this.activo = true;
    }

    public UUID id() {
        return id;
    }

    public UUID empresaId() {
        return empresaId;
    }

    public TipoDocumentoIdentidad tipoDocumento() {
        return tipoDocumento;
    }

    public String numeroDocumento() {
        return numeroDocumento;
    }

    public String nombre() {
        return nombre;
    }

    public String direccion() {
        return direccion;
    }

    public String correo() {
        return correo;
    }

    public String telefono() {
        return telefono;
    }

    public Instant verificadoEn() {
        return verificadoEn;
    }

    public boolean estaActivo() {
        return activo;
    }

    /** Puede recibir una factura: tiene RUC. */
    public boolean admiteFactura() {
        return tipoDocumento == TipoDocumentoIdentidad.RUC;
    }

    private static String exigirNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaDeNegocioViolada(
                    "nombre_requerido", "El cliente necesita un nombre o razón social.", "nombre");
        }
        String limpio = nombre.trim();
        if (limpio.length() > 300) {
            throw new ReglaDeNegocioViolada(
                    "nombre_invalido", "El nombre admite hasta 300 caracteres.", "nombre");
        }
        return limpio;
    }

    private static String limpiar(String valor, int maximo, String campo, String mensaje) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim();
        if (limpio.length() > maximo) {
            throw new ReglaDeNegocioViolada(campo + "_invalido", mensaje, campo);
        }
        return limpio;
    }

    private static String limpiarCorreo(String correo) {
        String limpio = limpiar(correo, 200, "correo", "El correo admite hasta 200 caracteres.");
        if (limpio != null && !limpio.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new ReglaDeNegocioViolada(
                    "correo_invalido", "El correo no tiene un formato válido.", "correo");
        }
        return limpio == null ? null : limpio.toLowerCase();
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof Cliente cliente && id.equals(cliente.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
