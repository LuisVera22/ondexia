package com.ondexia.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * El panel administrativo interno de Ondexia.
 *
 * <p>Arranque propio y despliegue propio, no un perfil de {@code ondexia.api}.
 * Ver ondexia.docs/09-panel-administrativo.md §6.
 *
 * <p><strong>El escaneo de componentes arranca en este paquete</strong>, que es
 * lo que impide que un controlador de inquilinos acabe publicado aquí. No es una
 * precaución teórica: es la diferencia entre una consola interna y una puerta
 * trasera a los datos de todos los clientes.
 */
@SpringBootApplication
public class AplicacionAdmin {

    public static void main(String[] argumentos) {
        SpringApplication.run(AplicacionAdmin.class, argumentos);
    }
}
