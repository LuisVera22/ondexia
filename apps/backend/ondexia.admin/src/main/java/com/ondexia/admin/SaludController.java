package com.ondexia.admin;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Señal de vida, sin autenticación y sin tocar la base.
 *
 * <p>Existe desde el primer commit del módulo por lo aprendido desplegando la
 * API: hay fallos que solo aparecen en la nube —el empaquetado, el arranque de
 * Spring, la instantánea de SnapStart— y sin un endpoint que responda no hay
 * forma de distinguir «la función no arranca» de «la función arranca y algo más
 * falla».
 *
 * <p>No dice nada que un desconocido no pueda saber ya: que aquí hay algo
 * escuchando. La versión sale de una variable de entorno que pone el despliegue.
 */
@RestController
@RequestMapping("/salud")
public class SaludController {

    @GetMapping
    public Map<String, String> consultar() {
        return Map.of(
                "estado", "vivo",
                "componente", "admin",
                "version", System.getenv().getOrDefault("ONDEXIA_VERSION", "desarrollo"));
    }
}
