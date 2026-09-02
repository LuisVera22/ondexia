package com.ondexia.admin.cuentas;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las cuentas cliente. Todo exige token del grupo de personal.
 *
 * <p>Los cambios son {@code PUT} sobre un aspecto concreto —el plan, el estado,
 * un módulo— y no un {@code PUT} de la cuenta entera. Con un objeto completo, dos
 * operadores editando a la vez se pisan sin enterarse: el último en guardar
 * revierte lo del otro con datos que ni siquiera miró.
 */
@RestController
@RequestMapping("/api/v1/cuentas")
public class CuentaController {

    private final ConsultaDeCuentas consulta;
    private final ConsultaDeModulos modulos;
    private final GestionDeCuentas gestion;

    public CuentaController(ConsultaDeCuentas consulta, ConsultaDeModulos modulos,
            GestionDeCuentas gestion) {
        this.consulta = consulta;
        this.modulos = modulos;
        this.gestion = gestion;
    }

    public record CambioDePlan(String plan) {
    }

    public record CambioDeEstado(String estado, String motivo) {
    }

    /** {@code habilitado} nulo borra la decisión y devuelve la cuenta a su plan. */
    public record DecisionDeModulo(UUID permisoId, Boolean habilitado, String motivo) {
    }

    @GetMapping
    public List<CuentaResumen> listar() {
        return consulta.listar();
    }

    @GetMapping("/{id}/modulos")
    public List<ModuloContratado> modulosDe(@PathVariable UUID id) {
        return modulos.de(id);
    }

    @PutMapping("/{id}/plan")
    public void cambiarPlan(@PathVariable UUID id, @RequestBody CambioDePlan cambio,
            @AuthenticationPrincipal Jwt token) {
        gestion.cambiarPlan(id, cambio.plan(), Operador.de(token));
    }

    @PutMapping("/{id}/estado")
    public void cambiarEstado(@PathVariable UUID id, @RequestBody CambioDeEstado cambio,
            @AuthenticationPrincipal Jwt token) {
        gestion.cambiarEstado(id, cambio.estado(), cambio.motivo(), Operador.de(token));
    }

    @PutMapping("/{id}/modulos")
    public void decidirModulo(@PathVariable UUID id, @RequestBody DecisionDeModulo decision,
            @AuthenticationPrincipal Jwt token) {
        gestion.decidirModulo(id, decision.permisoId(), decision.habilitado(),
                decision.motivo(), Operador.de(token));
    }

}
