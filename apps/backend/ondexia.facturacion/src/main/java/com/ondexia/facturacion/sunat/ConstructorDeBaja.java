package com.ondexia.facturacion.sunat;

import com.ondexia.domain.comprobante.OrdenDeEmision;
import io.github.project.openubl.xbuilder.content.models.common.Direccion;
import io.github.project.openubl.xbuilder.content.models.common.Firmante;
import io.github.project.openubl.xbuilder.content.models.common.Proveedor;
import io.github.project.openubl.xbuilder.content.models.sunat.baja.VoidedDocuments;
import io.github.project.openubl.xbuilder.content.models.sunat.baja.VoidedDocumentsItem;
import io.github.project.openubl.xbuilder.enricher.ContentEnricher;
import io.github.project.openubl.xbuilder.enricher.config.Defaults;
import io.github.project.openubl.xbuilder.renderer.TemplateProducer;
import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * De la orden al XML de la comunicación de baja (doc 13 §6).
 *
 * <p>Mucho más simple que un comprobante: no lleva importes, ni adquirente, ni
 * impuestos. Solo quién comunica, de qué día son los comprobantes, y la lista
 * de los que no debieron existir con su motivo. El identificador que SUNAT usa
 * —{@code RA-yyyyMMdd-N}— lo compone la plantilla con la fecha de emisión y el
 * número, así que los dos tienen que ser exactamente los que la orden trae.
 */
public class ConstructorDeBaja {

    public VoidedDocuments modelar(OrdenDeEmision orden) {
        OrdenDeEmision.Baja baja = orden.baja();
        if (baja == null || baja.comprobantes().isEmpty()) {
            throw new IllegalArgumentException(
                    "Una comunicación de baja necesita al menos un comprobante.");
        }
        OrdenDeEmision.Emisor e = orden.emisor();

        var comprobantes = new ArrayList<VoidedDocumentsItem>();
        for (OrdenDeEmision.ComprobanteDadoDeBaja c : baja.comprobantes()) {
            comprobantes.add(VoidedDocumentsItem.builder()
                    .serie(c.serie())
                    .numero((int) c.numero())
                    .tipoComprobante(c.tipo())
                    .descripcionSustento(c.motivo())
                    .build());
        }

        var documento = VoidedDocuments.builder()
                .numero(baja.numeroDelDia())
                .fechaEmision(baja.fechaDeGeneracion())
                .fechaEmisionComprobantes(baja.fechaDeLosComprobantes())
                .moneda("PEN")
                .proveedor(Proveedor.builder()
                        .ruc(e.ruc())
                        .razonSocial(e.razonSocial())
                        .nombreComercial(e.nombreComercial())
                        .direccion(Direccion.builder()
                                .direccion(e.direccion())
                                .codigoLocal(e.codigoEstablecimiento() == null
                                        ? "0000" : e.codigoEstablecimiento())
                                .build())
                        .build())
                .firmante(Firmante.builder().ruc(e.ruc()).razonSocial(e.razonSocial()).build())
                .comprobantes(comprobantes)
                .build();

        // El enriquecedor completa lo que la plantilla espera y no viene en la
        // orden. Aquí no hay importes que pueda recalcular mal, así que no hay
        // nada que imponer después: es la diferencia con el comprobante.
        new ContentEnricher(Defaults.builder()
                .igvTasa(new BigDecimal("0.18"))
                .icbTasa(new BigDecimal("0.50"))
                .ivapTasa(new BigDecimal("0.04"))
                .build(), baja::fechaDeGeneracion).enrich(documento);
        return documento;
    }

    /** El XML listo para firmar. */
    public String construir(OrdenDeEmision orden) {
        return TemplateProducer.getInstance().getVoidedDocument().data(modelar(orden)).render();
    }
}
