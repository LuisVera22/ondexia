package com.ondexia.facturacion;

import com.ondexia.facturacion.bus.AlmacenDelBus;
import com.ondexia.facturacion.sunat.ClienteSunat;
import com.ondexia.facturacion.sunat.ConstructorDeComprobante;
import com.ondexia.facturacion.sunat.FirmadorDeComprobante;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class FacturacionConfig {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    ClienteSunat clienteSunat(PropiedadesEmision propiedades) {
        return new ClienteSunat(propiedades.tiempoDeEspera());
    }

    @Bean
    ProcesadorDeOrdenes procesadorDeOrdenes(AlmacenDelBus bus, ClienteSunat sunat,
            PropiedadesEmision propiedades, ObjectMapper json, Clock reloj) {
        return new ProcesadorDeOrdenes(bus, sunat, new ConstructorDeComprobante(),
                new FirmadorDeComprobante(), propiedades, json, reloj);
    }
}
