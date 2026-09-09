package com.ondexia.facturacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * El Emisor (doc 14): toma órdenes del bus, construye el XML UBL 2.1, lo firma
 * con el certificado de la empresa, lo envía a SUNAT y deja el resultado en el
 * bus. Sin base de datos, fuera de la VPC. Ver el pom para el porqué de cada
 * cosa.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FacturacionApplication {

    public static void main(String[] argumentos) {
        SpringApplication.run(FacturacionApplication.class, argumentos);
    }
}
