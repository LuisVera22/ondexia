package com.ondexia.consultas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Consultas a fuentes públicas externas (DT-19).
 *
 * <h2>Por qué es un desplegable aparte y no una ruta de la API</h2>
 *
 * <p>Porque la Lambda de la API está en subred privada sin NAT y no alcanza
 * internet. Darle salida cuesta 32 USD/mes de NAT Gateway o 7,30 de endpoint de
 * interfaz (DTE §4.8); esta función vive fuera de la VPC, donde salir es gratis.
 *
 * <p>Lo que pierde por estar fuera: no ve la base de datos. Y no le hace falta —
 * consulta el padrón de SUNAT y firma el resultado, nada más.
 *
 * <h2>Misma estructura que ondexia.api, a propósito</h2>
 *
 * <p>La primera versión no llevaba Spring: un {@code main}, variables de entorno
 * leídas a mano y un servidor HTTP de la JDK. El argumento era el arranque en
 * frío y era cierto, pero con un solo desarrollador tener dos formas de montar
 * un módulo cuesta más que unos milisegundos — cada vez que hubiera que buscar
 * dónde se configura algo, la respuesta dependería del módulo.
 *
 * <p>Y se llevó por delante ~150 líneas de servidor, CORS y sobres JSON escritos
 * a mano. El mismo artefacto arranca con {@code spring-boot:run} en local y como
 * Lambda en AWS, sin ninguna rama condicional.
 *
 * <p>El precio es que SnapStart deja de ser opcional (DT-02): sin él, un Spring
 * Boot en Lambda arranca en 6-10 s, y esta consulta ocurre mientras alguien
 * espera con el cursor en un formulario.
 */
@SpringBootApplication
public class ConsultasApplication {

    public static void main(String[] argumentos) {
        SpringApplication.run(ConsultasApplication.class, argumentos);
    }
}
