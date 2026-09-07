package com.ondexia.application.comprobante;

import com.ondexia.application.configuracion.ConsultarEmpresa;
import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comprobante.SerieCorrelativoRepositorio;
import com.ondexia.domain.comprobante.TipoDocumento;
import com.ondexia.domain.comprobante.TiposDeComprobanteRepositorio;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Qué tipos de comprobante emite la empresa activa.
 *
 * <p>Es la parte de la pantalla «Comprobantes» que entra en este módulo (plan 07
 * §1.1: <em>parcial</em>). Emitir, anular y consultar el estado ante SUNAT son de
 * C1 y no viven aquí.
 *
 * <p>La declaración no es decorativa: {@link Series} la consulta antes de dejar
 * crear una serie. Una pantalla de configuración que no cambia el comportamiento
 * de nada es una pantalla que el cliente rellena y luego descubre que daba igual.
 */
@Service
public class TiposDeComprobante {

    private final TiposDeComprobanteRepositorio tipos;
    private final SerieCorrelativoRepositorio series;
    private final RegistroDeAuditoria auditoria;
    private final ConsultarEmpresa empresa;

    public TiposDeComprobante(TiposDeComprobanteRepositorio tipos,
            SerieCorrelativoRepositorio series, RegistroDeAuditoria auditoria,
            ConsultarEmpresa empresa) {
        this.tipos = tipos;
        this.series = series;
        this.auditoria = auditoria;
        this.empresa = empresa;
    }

    /**
     * @param seriesActivas cuántas series vivas cuelgan del tipo. La pantalla lo
     *                      necesita para explicar por qué no se puede apagar
     */
    public record EstadoDeTipo(TipoDocumento tipo, boolean emite, long seriesActivas) {
    }

    /**
     * Los cinco tipos del catálogo con su estado.
     *
     * <p>Se compone contra {@link TipoDocumento#values()} y no contra la tabla,
     * porque la tabla solo guarda las decisiones: lo que no aparece en ella está
     * habilitado.
     */
    public List<EstadoDeTipo> listar() {
        var apagados = tipos.desactivados();
        var seriesVivas = series.listar().stream()
                .filter(s -> s.estaActiva())
                .toList();

        boolean emiteFacturas = empresa.ejecutar().emiteFacturas();

        return Arrays.stream(TipoDocumento.values())
                // La nota de venta no es comprobante: siempre se emite y no se apaga.
                .filter(TipoDocumento::esFiscal)
                .map(tipo -> new EstadoDeTipo(
                        tipo,
                        !apagados.contains(tipo) && (tipo != TipoDocumento.FACTURA || emiteFacturas),
                        seriesVivas.stream().filter(s -> s.tipoDocumento() == tipo).count()))
                .toList();
    }

    /**
     * Habilita o deshabilita un tipo.
     *
     * <p>No se puede apagar un tipo que todavía tiene series activas. La
     * alternativa —apagarlo y dejar las series emitiendo— haría que la pantalla
     * dijera una cosa y el sistema hiciera otra, que es peor que no tener la
     * pantalla.
     */
    @Transactional
    public EstadoDeTipo cambiarEstado(String codigoTipo, boolean emite) {
        var tipo = TipoDocumento.porCodigo(codigoTipo);
        if (!tipo.esFiscal()) {
            throw new ReglaDeNegocioViolada(
                    "tipo_no_configurable",
                    "La nota de venta no es un comprobante: siempre se emite y no se desactiva.");
        }

        /*
         * El regimen manda sobre la casilla (doc 12 §3.1). Una empresa en el
         * Nuevo RUS no emite facturas por ley; habilitarlas aqui produciria un
         * comprobante que SUNAT rechaza. La salida no es esta pantalla sino
         * cambiar el regimen en la ficha de la empresa, y el mensaje lo dice.
         */
        if (emite && tipo == TipoDocumento.FACTURA && !empresa.ejecutar().emiteFacturas()) {
            throw new ReglaDeNegocioViolada(
                    "nuevo_rus_no_emite_facturas",
                    "Una empresa en el Nuevo RUS no puede emitir facturas. Si el negocio "
                            + "cambió de régimen, actualízalo en los datos de la empresa.");
        }

        if (!emite) {
            long vivas = series.listar().stream()
                    .filter(s -> s.tipoDocumento() == tipo && s.estaActiva())
                    .count();

            if (vivas > 0) {
                throw new ReglaDeNegocioViolada(
                        "tipo_con_series_activas",
                        "Hay " + vivas + " serie(s) activa(s) de " + tipo.nombre().toLowerCase()
                                + ". Desactívalas primero.");
            }
        }

        boolean antes = tipos.emite(tipo);
        tipos.fijar(tipo, emite);

        if (antes != emite) {
            // El identificador es determinista a partir del código del tipo: no
            // hay entidad con identidad propia que referenciar, y la bitácora
            // necesita algo estable para agrupar el historial de cada tipo.
            auditoria.registrar("tipo_comprobante", identificadorDe(tipo),
                    emite ? "HABILITAR" : "DESHABILITAR",
                    new Instantanea(tipo.codigo(), antes),
                    new Instantanea(tipo.codigo(), emite));
        }

        return listar().stream()
                .filter(estado -> estado.tipo() == tipo)
                .findFirst()
                .orElseThrow();
    }

    /** Lo que consulta {@link Series} antes de dar de alta una serie. */
    public boolean emite(TipoDocumento tipo) {
        if (tipo == TipoDocumento.FACTURA && !empresa.ejecutar().emiteFacturas()) {
            return false;
        }
        return tipos.emite(tipo);
    }

    private static UUID identificadorDe(TipoDocumento tipo) {
        return UUID.nameUUIDFromBytes(("tipo_comprobante:" + tipo.codigo()).getBytes());
    }

    private record Instantanea(String tipoDocumento, boolean emite) {
    }
}
