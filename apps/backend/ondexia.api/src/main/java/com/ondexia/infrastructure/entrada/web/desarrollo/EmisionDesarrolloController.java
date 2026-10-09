package com.ondexia.infrastructure.entrada.web.desarrollo;

import com.ondexia.application.emision.ComunicacionesDeBaja;
import com.ondexia.application.emision.ConfiguracionDeEmision;
import com.ondexia.application.emision.EmisionElectronica;
import com.ondexia.domain.comprobante.OrdenDeEmision;
import com.ondexia.domain.comprobante.ResultadoDeEmision;
import com.ondexia.infrastructure.salida.emision.BusDeEmisionEnMemoria;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El lado de la API del ensayo local contra la beta de SUNAT (doc 14 §6).
 *
 * <p>Sin bucket, el bus es un mapa en memoria. Estas rutas dejan hacer a mano lo
 * que en la plataforma hace S3: leer las órdenes que la API publicó para
 * pasárselas a un Emisor levantado en local, y depositar lo que ese Emisor
 * respondió para que la API lo aplique. Solo con el perfil {@code local}, como
 * el emisor de tokens de desarrollo.
 */
@Hidden
@RestController
@RequestMapping("/desarrollo/emision")
@Profile("local")
public class EmisionDesarrolloController {

    private final BusDeEmisionEnMemoria bus;
    private final EmisionElectronica emision;
    private final ConfiguracionDeEmision configuracion;
    private final ComunicacionesDeBaja bajas;

    public EmisionDesarrolloController(BusDeEmisionEnMemoria bus, EmisionElectronica emision,
            ConfiguracionDeEmision configuracion, ComunicacionesDeBaja bajas) {
        this.bus = bus;
        this.emision = emision;
        this.configuracion = configuracion;
        this.bajas = bajas;
    }

    /** Las órdenes publicadas desde el arranque: lo que iría a {@code pendientes/}. */
    @GetMapping("/ordenes")
    public List<OrdenDeEmision> ordenes() {
        return bus.ordenes();
    }

    /** Lo que el Emisor local respondió: como si hubiera aparecido en {@code resultados/}. */
    @PostMapping("/resultados")
    public void depositar(@RequestBody ResultadoDeEmision resultado) {
        bus.depositarResultado(resultado);
        switch (resultado.operacion()) {
            case VERIFICAR_CREDENCIALES -> configuracion.aplicarResultadoDeVerificacion(resultado);
            case ENVIAR_BAJA, CONSULTAR_TICKET -> bajas.aplicarResultado(resultado);
            case EMITIR -> emision.aplicarResultado(resultado);
        }
    }

    /** Finge que el navegador subió un objeto por su URL prefirmada. */
    @PostMapping("/objetos")
    public void fingirSubida(@RequestParam String clave) {
        bus.fingirSubida(clave);
    }
}
