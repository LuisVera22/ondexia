package com.ondexia.facturacion.web;

import com.ondexia.domain.comprobante.OrdenDeEmision;
import com.ondexia.domain.comprobante.ResultadoDeEmision;
import com.ondexia.facturacion.ProcesadorDeOrdenes;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El ensayo contra la beta desde una máquina de desarrollo (doc 14 §6): se le
 * pasa la orden que la API publicó ({@code GET /desarrollo/emision/ordenes}) y
 * responde con el resultado, que después se deposita en la API
 * ({@code POST /desarrollo/emision/resultados}). El XML y el CDR quedan en el
 * directorio del bus local. Solo con el perfil {@code local}: en AWS no hay
 * HTTP, la función la invoca S3.
 */
@RestController
@RequestMapping("/emision")
@Profile("local")
public class OrdenesController {

    private final ProcesadorDeOrdenes procesador;

    public OrdenesController(ProcesadorDeOrdenes procesador) {
        this.procesador = procesador;
    }

    @PostMapping("/ordenes")
    public ResultadoDeEmision procesar(@RequestBody OrdenDeEmision orden) {
        return procesador.procesar(orden);
    }
}
