package com.ondexia.admin.cuentas;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Las cuentas cliente. Requiere token del grupo de personal. */
@RestController
@RequestMapping("/api/v1/cuentas")
public class CuentaController {

    private final ConsultaDeCuentas consulta;

    public CuentaController(ConsultaDeCuentas consulta) {
        this.consulta = consulta;
    }

    @GetMapping
    public List<CuentaResumen> listar() {
        return consulta.listar();
    }
}
