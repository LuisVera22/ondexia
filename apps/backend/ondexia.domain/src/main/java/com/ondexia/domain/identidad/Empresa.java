package com.ondexia.domain.identidad;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.Ubigeo;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.consultas.DatosDeRuc;
import java.time.Instant;
import java.time.LocalDate;
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
    /**
     * Lo que se sabe del certificado digital; {@code null} si nunca se cargó.
     * El archivo y su contraseña no están aquí: ver {@link CertificadoDigital}.
     */
    private CertificadoDigital certificado;
    private String usuarioSol;
    private ModoSunat modoSunat;
    private boolean activa;

    /** {@code null} en las empresas anteriores a la consulta del padrón. */
    private VerificacionSunat verificacion;

    /** Del Banco de la Nación. Opcional; ver doc 11 §6.1. */
    private String cuentaDetracciones;

    /** Lo único del régimen que importa aquí: si emite facturas. Ver {@link RegimenTributario}. */
    private RegimenTributario regimen = RegimenTributario.porOmision();

    /**
     * Si el mostrador puede vender con existencias insuficientes (doc 12 §3.5).
     * Por omisión sí, con aviso: un mostrador no se detiene por un conteo
     * desfasado. Un almacén formal lo apaga y entonces la venta se rechaza.
     */
    private boolean permiteVentaSinStock = true;

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

    /**
     * Alta a partir de una consulta al padrón ya comprobada.
     *
     * <h2>Por qué esta es la única forma de dar de alta una empresa nueva</h2>
     *
     * <p>Porque recibe {@link DatosDeRuc}, y un {@code DatosDeRuc} solo existe
     * si vino de una atestación firmada por {@code ondexia.consultas}. Es decir:
     * <strong>no se puede construir una empresa con una razón social que SUNAT
     * no haya dicho.</strong> No es una comprobación que alguien pueda olvidar
     * poner en un caso de uso; es que no hay otro camino.
     *
     * <p>Importa más de lo que parece: una razón social que no coincide con el
     * padrón hace que SUNAT rechace <em>todos</em> los comprobantes de esa
     * empresa, y el fallo aparecería en la primera emisión real.
     *
     * @throws ReglaDeNegocioViolada si el RUC no está activo y habido
     */
    public static Empresa registrar(UUID id, UUID cuentaId, DatosDeRuc datos,
            RegimenTributario regimen) {
        if (!datos.aptaParaRegistro()) {
            throw new ReglaDeNegocioViolada("ruc_no_apto", datos.motivoDeRechazo());
        }

        /*
         * Quien puede registrarse lo dice el prefijo del RUC (doc 12 §3.1):
         * personas naturales con negocio y personas juridicas. Una sucesion
         * indivisa o una entidad con otro documento no es hoy un cliente, y
         * admitirla seria emitir con reglas que nadie ha revisado para ella.
         * Esta en la fabrica y no en el caso de uso por la misma razon que la
         * aptitud del RUC: no hay otro camino para crear una empresa.
         */
        var tipo = datos.ruc().tipoDeContribuyente();
        if (!tipo.puedeRegistrarse()) {
            throw new ReglaDeNegocioViolada(
                    "tipo_de_contribuyente_no_admitido",
                    "El RUC " + datos.ruc() + " es de " + tipo.descripcion()
                            + ". Ondexia admite por ahora personas naturales con negocio "
                            + "(RUC 10) y personas jurídicas (RUC 20).");
        }

        Empresa empresa = new Empresa(id, cuentaId, datos.ruc(), datos.razonSocial(),
                datos.domicilioFiscal());
        empresa.ubigeo = datos.ubigeo();
        empresa.verificacion = VerificacionSunat.de(datos);
        empresa.cambiarRegimen(regimen);
        return empresa;
    }

    /** Reconstrucción desde persistencia. Solo lo usa el mapeador. */
    public Empresa(UUID id, UUID cuentaId, Ruc ruc, String razonSocial, String nombreComercial,
            String domicilioFiscal, Ubigeo ubigeo, CertificadoDigital certificado, String usuarioSol,
            ModoSunat modoSunat, boolean activa, VerificacionSunat verificacion,
            String cuentaDetracciones, RegimenTributario regimen) {
        this.id = id;
        this.cuentaId = cuentaId;
        this.ruc = ruc;
        this.razonSocial = razonSocial;
        this.nombreComercial = nombreComercial;
        this.domicilioFiscal = domicilioFiscal;
        this.ubigeo = ubigeo;
        this.certificado = certificado;
        this.usuarioSol = usuarioSol;
        this.modoSunat = modoSunat;
        this.activa = activa;
        this.verificacion = verificacion;
        this.cuentaDetracciones = cuentaDetracciones;
        this.regimen = regimen == null ? RegimenTributario.porOmision() : regimen;
    }

    /** Reconstrucción completa, con la política de venta sin existencias. */
    public Empresa(UUID id, UUID cuentaId, Ruc ruc, String razonSocial, String nombreComercial,
            String domicilioFiscal, Ubigeo ubigeo, CertificadoDigital certificado, String usuarioSol,
            ModoSunat modoSunat, boolean activa, VerificacionSunat verificacion,
            String cuentaDetracciones, RegimenTributario regimen, boolean permiteVentaSinStock) {
        this(id, cuentaId, ruc, razonSocial, nombreComercial, domicilioFiscal, ubigeo,
                certificado, usuarioSol, modoSunat, activa, verificacion,
                cuentaDetracciones, regimen);
        this.permiteVentaSinStock = permiteVentaSinStock;
    }

    public boolean permiteVentaSinStock() {
        return permiteVentaSinStock;
    }

    public void fijarVentaSinStock(boolean permitir) {
        this.permiteVentaSinStock = permitir;
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

    /** @return {@code null} si nunca se cargó un certificado */
    public CertificadoDigital certificado() {
        return certificado;
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

    /** @return {@code null} si nunca se comprobó contra SUNAT */
    public VerificacionSunat verificacion() {
        return verificacion;
    }

    public RegimenTributario regimen() {
        return regimen;
    }

    /** Si puede emitir facturas: lo decide el régimen, no una casilla. */
    public boolean emiteFacturas() {
        return regimen.emiteFacturas();
    }

    /**
     * El régimen lo declara el contribuyente y puede cambiar —quien crece sale
     * del Nuevo RUS—. Lo que no puede es contradecir al RUC: una persona jurídica
     * en el RUS no existe.
     */
    public void cambiarRegimen(RegimenTributario nuevo) {
        var regimenNuevo = nuevo == null ? RegimenTributario.porOmision() : nuevo;
        if (regimenNuevo == RegimenTributario.NUEVO_RUS
                && !ruc.tipoDeContribuyente().puedeEstarEnNuevoRus()) {
            throw new ReglaDeNegocioViolada(
                    "nuevo_rus_solo_persona_natural",
                    "El Nuevo RUS es solo para personas naturales. El RUC " + ruc
                            + " es de una persona jurídica.");
        }
        this.regimen = regimenNuevo;
    }

    public String cuentaDetracciones() {
        return cuentaDetracciones;
    }

    /**
     * Refresca lo que SUNAT dice, con los datos de una consulta nueva.
     *
     * <h2>Por qué esto existe y por qué no valida la aptitud</h2>
     *
     * <p>Existe porque «no editable» no puede significar «congelado»: una razón
     * social cambia legítimamente en SUNAT, y sin forma de refrescarla un dato
     * corregible se vuelve imposible de corregir — peor que dejarlo editable,
     * porque el rechazo de SUNAT no tendría salida desde la aplicación.
     *
     * <p>Y no exige que siga apta a propósito. Una empresa que ya opera y pasa a
     * NO HABIDO tiene que poder registrar ese hecho: es justo lo que hay que ver
     * en pantalla. Bloquear la actualización dejaría el dato viejo, que es la
     * única versión que de verdad engaña. Lo que se haga con una empresa no apta
     * —avisar, impedir emitir— es decisión de quien lea esto, no de aquí.
     */
    public void refrescarDesdeSunat(DatosDeRuc datos) {
        if (!datos.ruc().equals(this.ruc)) {
            throw new ReglaDeNegocioViolada("ruc_distinto",
                    "La consulta es del RUC " + datos.ruc() + " y esta empresa es "
                            + this.ruc + ".");
        }
        this.razonSocial = exigirTexto(datos.razonSocial(), "razon_social", "La razón social");
        this.domicilioFiscal = datos.domicilioFiscal() == null
                ? this.domicilioFiscal
                : datos.domicilioFiscal();
        if (datos.ubigeo() != null) {
            this.ubigeo = datos.ubigeo();
        }
        this.verificacion = VerificacionSunat.de(datos);
    }

    /**
     * La cuenta de detracciones del Banco de la Nación.
     *
     * <p>Solo dígitos, y sin longitud fija: el formato del BN no está publicado
     * ni en su web ni en la orientación de SUNAT. Un largo inventado rechazaría
     * cuentas válidas, que es peor que no comprobar.
     */
    public void anotarCuentaDetracciones(String cuenta) {
        if (cuenta == null || cuenta.isBlank()) {
            this.cuentaDetracciones = null;
            return;
        }
        String limpia = cuenta.trim();
        if (!limpia.matches("\\d+")) {
            throw new ReglaDeNegocioViolada("cuenta_detracciones_invalida",
                    "La cuenta de detracciones solo puede tener dígitos.");
        }
        this.cuentaDetracciones = limpia;
    }

    /**
     * El nombre comercial, que es nuestro y no de SUNAT.
     *
     * <p>Es el único dato de identificación que se edita a mano: SUNAT tiene uno
     * registrado, pero las empresas usan el que quieren en sus facturas y no hay
     * ninguna consecuencia en que difieran. La razón social, el domicilio y el
     * ubigeo solo entran por {@link #refrescarDesdeSunat}.
     *
     * <p>Hubo un {@code actualizarDatosFiscales} que admitía razón social y
     * domicilio a mano, sin tocar la verificación. Nadie lo llamaba y se retiró
     * (hallazgo M7): un método que puede sobrescribir lo que vino del padrón es
     * un método que algún día alguien llama.
     */
    public void renombrarComercialmente(String nombreComercial) {
        this.nombreComercial = nombreComercial == null || nombreComercial.isBlank()
                ? null
                : nombreComercial.trim();
    }

    /**
     * Anota que el certificado y las credenciales quedaron en el bucket del bus.
     *
     * <p>Aquí no llega ni el {@code .pfx} ni su contraseña ni la clave SOL: el
     * navegador los sube directo a S3 con URL prefirmadas, y este agregado solo
     * sabe que ocurrió (doc 14 §4). Un certificado guardado en la base
     * aparecería en cada respaldo, en cada volcado de desarrollo y en cada
     * consulta de soporte. El DTE §8.2 decía Secrets Manager; el doc 12 §5.3
     * explica por qué es el bucket.
     *
     * <p>Cargar de nuevo reinicia la verificación: el archivo nuevo puede no
     * abrir con la contraseña nueva, y lo que se sabía era del anterior.
     */
    public void cargarCertificado(String usuarioSol, Instant ahora) {
        this.usuarioSol = exigirTexto(usuarioSol, "usuario_sol", "El usuario SOL");
        this.certificado = CertificadoDigital.cargado(ahora);
    }

    /** Lo que el Emisor dijo al abrir el certificado con su contraseña. */
    public void anotarVerificacionDeCertificado(Instant ahora, String sujeto, LocalDate venceEn) {
        exigirCertificadoCargado();
        this.certificado = certificado.verificado(ahora, sujeto, venceEn);
    }

    public void anotarFalloDeCertificado(Instant ahora, String motivo) {
        exigirCertificadoCargado();
        this.certificado = certificado.fallido(ahora, motivo);
    }

    /**
     * Si tiene lo necesario para que una boleta o factura salga hacia SUNAT:
     * certificado cargado y usuario SOL. No exige que el certificado ya se haya
     * verificado: si no abre, la emisión falla con ese motivo y se ve.
     */
    public boolean puedeEmitirElectronicamente() {
        return certificado != null && certificado.cargadoEn() != null
                && usuarioSol != null && !usuarioSol.isBlank();
    }

    /**
     * Pasa a emitir contra el entorno de producción de SUNAT.
     *
     * <p>Solo con un certificado que el Emisor abrió y que no ha caducado:
     * dejarlo pasar produciría un rechazo en la primera emisión real, que es el
     * peor momento para descubrirlo. En la beta se admite cualquier cosa, que
     * para eso está.
     */
    public void habilitarProduccion(LocalDate hoy) {
        if (certificado == null || !certificado.vigente(hoy)) {
            throw new ReglaDeNegocioViolada(
                    "sin_certificado_vigente",
                    "La empresa " + ruc + " no tiene un certificado digital verificado y vigente. "
                            + "Cárgalo y espera a que se verifique antes de pasar a producción.");
        }
        if (usuarioSol == null || usuarioSol.isBlank()) {
            throw new ReglaDeNegocioViolada(
                    "sin_usuario_sol", "La empresa " + ruc + " no tiene usuario SOL configurado.");
        }
        this.modoSunat = ModoSunat.PRODUCCION;
    }

    /** Vuelve a la beta. Siempre se puede: lo que se emita desde ahora no vale ante SUNAT. */
    public void volverABeta() {
        this.modoSunat = ModoSunat.BETA;
    }

    private void exigirCertificadoCargado() {
        if (certificado == null) {
            throw new ReglaDeNegocioViolada(
                    "sin_certificado", "La empresa " + ruc + " no tiene certificado digital cargado.");
        }
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
