package com.ondexia.application.emision;

import com.ondexia.application.configuracion.ConsultarEmpresa;
import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comprobante.BusDeEmision;
import com.ondexia.domain.comprobante.ClavesDelBus;
import com.ondexia.domain.comprobante.EstadoSunat;
import com.ondexia.domain.comprobante.OrdenDeEmision;
import com.ondexia.domain.comprobante.ResultadoDeEmision;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.identidad.CertificadoDigital;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.identidad.EmpresaRepositorio;
import com.ondexia.domain.identidad.ModoSunat;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Certificado digital, clave SOL y entorno de SUNAT de la empresa activa
 * (doc 14 §4).
 *
 * <h2>La API nunca ve el certificado ni la contraseña</h2>
 *
 * <p>La pantalla pide dos URL prefirmadas y sube por su cuenta el {@code .pfx} y
 * un JSON con la contraseña y la clave SOL, directo al bucket. Después confirma,
 * y aquí solo se comprueba que los dos objetos existen —por listado, sin
 * leerlos— y se anota la fecha. La contraseña no pasa por esta aplicación, por
 * su bitácora ni por sus registros. Lo que sí se hace es encolar una
 * verificación: el Emisor, que sí puede leerlos, abre el certificado y dice qué
 * hay dentro o por qué no abre. Ese resultado se lee la próxima vez que alguien
 * mire la pantalla, igual que el de una emisión.
 *
 * <p>La verificación tiene un identificador determinista —empresa y fecha de
 * carga— para no tener que guardar el de la orden: cargar de nuevo cambia la
 * fecha y por tanto la orden.
 */
@Service
public class ConfiguracionDeEmision {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Para subir dos archivos pequeños desde un formulario; acota una URL filtrada. */
    private static final Duration VALIDEZ_SUBIDA = Duration.ofMinutes(5);
    public static final String TIPO_CERTIFICADO = "application/x-pkcs12";
    public static final String TIPO_CREDENCIALES = "application/json";

    private final ConsultarEmpresa consultar;
    private final EmpresaRepositorio empresas;
    private final BusDeEmision bus;
    private final RegistroDeAuditoria auditoria;
    private final Clock reloj;

    public ConfiguracionDeEmision(ConsultarEmpresa consultar, EmpresaRepositorio empresas,
            BusDeEmision bus, RegistroDeAuditoria auditoria, Clock reloj) {
        this.consultar = consultar;
        this.empresas = empresas;
        this.bus = bus;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /**
     * @param claveCertificado y {@code claveCredenciales}: dónde quedan, para que
     *                         la pantalla sepa qué está subiendo
     */
    public record AutorizacionDeCarga(String urlCertificado, String urlCredenciales,
            String claveCertificado, String claveCredenciales, Duration validez) {
    }

    /** La empresa, con la verificación del certificado aplicada si el Emisor ya respondió. */
    @Transactional
    public Empresa estado() {
        var empresa = consultar.ejecutar();
        CertificadoDigital certificado = empresa.certificado();
        if (certificado == null || certificado.verificadoEn() != null) {
            return empresa;
        }
        return bus.resultadoDe(empresa.id(), idDeVerificacion(empresa))
                .map(resultado -> aplicarVerificacion(empresa, resultado))
                .orElse(empresa);
    }

    public AutorizacionDeCarga autorizarCarga() {
        var empresa = consultar.ejecutar();
        String ruc = empresa.ruc().valor();
        return new AutorizacionDeCarga(
                bus.urlDeSubida(ClavesDelBus.certificado(ruc), TIPO_CERTIFICADO, VALIDEZ_SUBIDA),
                bus.urlDeSubida(ClavesDelBus.credenciales(ruc), TIPO_CREDENCIALES, VALIDEZ_SUBIDA),
                ClavesDelBus.certificado(ruc), ClavesDelBus.credenciales(ruc), VALIDEZ_SUBIDA);
    }

    /** La pantalla ya subió los dos objetos: se comprueba que están y se encola la verificación. */
    @Transactional
    public Empresa confirmarCarga(String usuarioSol) {
        var empresa = consultar.ejecutar();
        String ruc = empresa.ruc().valor();
        if (!bus.existe(ClavesDelBus.certificado(ruc))) {
            throw new ReglaDeNegocioViolada(
                    "certificado_no_subido",
                    "El certificado no llegó al almacén. Vuelve a elegir el archivo .pfx.",
                    "certificado");
        }
        if (!bus.existe(ClavesDelBus.credenciales(ruc))) {
            throw new ReglaDeNegocioViolada(
                    "credenciales_no_subidas",
                    "La contraseña del certificado y la clave SOL no llegaron al almacén.",
                    "claveSol");
        }
        var antes = Instantanea.de(empresa);
        empresa.cargarCertificado(usuarioSol, reloj.instant());
        var guardada = empresas.guardar(empresa);
        auditoria.registrar("empresa", guardada.id(), "CARGAR_CERTIFICADO", antes,
                Instantanea.de(guardada));
        EmisionElectronica.publicarTrasConfirmar(bus, ordenDeVerificacion(guardada));
        return guardada;
    }

    @Transactional
    public Empresa cambiarModo(ModoSunat modo) {
        var empresa = consultar.ejecutar();
        var antes = Instantanea.de(empresa);
        if (modo == ModoSunat.PRODUCCION) {
            empresa.habilitarProduccion(LocalDate.ofInstant(reloj.instant(), LIMA));
        } else {
            empresa.volverABeta();
        }
        var guardada = empresas.guardar(empresa);
        auditoria.registrar("empresa", guardada.id(), "CAMBIAR_MODO_SUNAT", antes,
                Instantanea.de(guardada));
        return guardada;
    }

    /** Público para el ensayo local (doc 14 §6). */
    @Transactional
    public Empresa aplicarResultadoDeVerificacion(ResultadoDeEmision resultado) {
        var empresa = empresas.buscarPorId(resultado.empresaId())
                .orElseThrow(() -> new IllegalStateException("La empresa del resultado no existe."));
        return aplicarVerificacion(empresa, resultado);
    }

    private Empresa aplicarVerificacion(Empresa empresa, ResultadoDeEmision resultado) {
        Instant ahora = resultado.procesadoEn() == null ? reloj.instant() : resultado.procesadoEn();
        if (resultado.estado() == EstadoSunat.ACEPTADO) {
            empresa.anotarVerificacionDeCertificado(ahora, resultado.certificadoSujeto(),
                    resultado.certificadoVenceEn());
        } else {
            empresa.anotarFalloDeCertificado(ahora, resultado.descripcion());
        }
        return empresas.guardar(empresa);
    }

    /**
     * A segundos: la fecha vuelve de PostgreSQL con microsegundos y el reloj de
     * Java la da con nanosegundos; sin truncar, la orden que se publicó al
     * confirmar y la que se busca al consultar tendrían identificadores
     * distintos y la verificación no se aplicaría nunca.
     */
    static UUID idDeVerificacion(Empresa empresa) {
        return UUID.nameUUIDFromBytes(("verificacion:" + empresa.id() + ":"
                + empresa.certificado().cargadoEn().truncatedTo(ChronoUnit.SECONDS))
                .getBytes(StandardCharsets.UTF_8));
    }

    private OrdenDeEmision ordenDeVerificacion(Empresa e) {
        var emisor = new OrdenDeEmision.Emisor(e.ruc().valor(), e.razonSocial(), e.nombreComercial(),
                e.domicilioFiscal(), e.ubigeo() == null ? null : e.ubigeo().valor(), "0000",
                e.usuarioSol());
        return OrdenDeEmision.paraVerificarCredenciales(idDeVerificacion(e), e.id(), e.modoSunat(),
                emisor, reloj.instant());
    }

    /**
     * Lo que va a la bitácora: ni la clave SOL ni nada del certificado más allá
     * de las fechas. El usuario SOL tampoco: junto a la clave identifica el
     * acceso al portal de SUNAT (DTE §8.4).
     */
    private record Instantanea(String modoSunat, Instant certificadoCargadoEn,
            Instant certificadoVerificadoEn, LocalDate certificadoVenceEn) {

        static Instantanea de(Empresa e) {
            var c = e.certificado();
            return new Instantanea(e.modoSunat().name(), c == null ? null : c.cargadoEn(),
                    c == null ? null : c.verificadoEn(), c == null ? null : c.venceEn());
        }
    }
}
