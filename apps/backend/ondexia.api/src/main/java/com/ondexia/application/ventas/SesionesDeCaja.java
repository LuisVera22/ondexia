package com.ondexia.application.ventas;

import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.ventas.Caja;
import com.ondexia.domain.ventas.CobrosDeSesion;
import com.ondexia.domain.ventas.FormaDePago;
import com.ondexia.domain.ventas.SesionCaja;
import com.ondexia.domain.ventas.SesionCajaRepositorio;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abrir y cerrar caja.
 *
 * <p>La regla que gobierna todo lo demás del primer producto: <strong>no se
 * vende con la caja cerrada</strong> (doc 12 §3.4). El punto de venta pregunta
 * por {@link #abiertaDe(UUID)} antes de dejar cobrar.
 */
@Service
public class SesionesDeCaja {

    private final SesionCajaRepositorio sesiones;
    private final Cajas cajas;
    private final CobrosDeSesion cobros;
    private final RegistroDeAuditoria auditoria;
    private final ProveedorDeContexto contexto;
    private final Clock reloj;

    public SesionesDeCaja(SesionCajaRepositorio sesiones, Cajas cajas, CobrosDeSesion cobros,
            RegistroDeAuditoria auditoria, ProveedorDeContexto contexto, Clock reloj) {
        this.sesiones = sesiones;
        this.cajas = cajas;
        this.cobros = cobros;
        this.auditoria = auditoria;
        this.contexto = contexto;
        this.reloj = reloj;
    }

    public Optional<SesionCaja> abiertaDe(UUID cajaId) {
        cajas.exigir(cajaId);
        return sesiones.buscarAbierta(cajaId);
    }

    /** Las sesiones abiertas de las cajas que el usuario alcanza. */
    public List<SesionCaja> abiertas() {
        var visibles = cajas.listar().stream().map(Caja::id).toList();
        return sesiones.listarAbiertas().stream()
                .filter(sesion -> visibles.contains(sesion.cajaId()))
                .toList();
    }

    public List<SesionCaja> historialDe(UUID cajaId) {
        cajas.exigir(cajaId);
        return sesiones.listarDeCaja(cajaId);
    }

    @Transactional
    public SesionCaja abrir(UUID cajaId, BigDecimal montoInicial) {
        var caja = cajas.exigir(cajaId);
        if (!caja.estaActiva()) {
            throw new ReglaDeNegocioViolada("caja_inactiva", "La caja está desactivada.");
        }
        // Cortesía; la garantía es el índice único parcial de la base.
        sesiones.buscarAbierta(cajaId).ifPresent(abierta -> {
            throw new ReglaDeNegocioViolada(
                    "caja_ya_abierta",
                    "La caja ya tiene una sesión abierta. Ciérrala antes de abrir otra.");
        });

        var sesion = SesionCaja.abrir(UUID.randomUUID(), cajaId,
                contexto.obligatorio().usuarioId(), montoInicial, reloj.instant());
        var guardada = sesiones.guardar(sesion);
        auditoria.registrar("sesion_caja", guardada.id(), "ABRIR", null, Instantanea.de(guardada));
        return guardada;
    }

    /**
     * Cierra con el arqueo. Lo cobrado lo aporta {@link CobrosDeSesion}; lo
     * declarado, la persona. La diferencia queda escrita, no se corrige.
     */
    @Transactional
    public SesionCaja cerrar(UUID sesionId, Map<FormaDePago, BigDecimal> declarado) {
        var sesion = exigir(sesionId);
        var antes = Instantanea.de(sesion);

        sesion.cerrar(contexto.obligatorio().usuarioId(), cobros.cobradoEn(sesionId), declarado,
                reloj.instant());

        var guardada = sesiones.guardar(sesion);
        auditoria.registrar("sesion_caja", sesionId, "CERRAR", antes, Instantanea.de(guardada));
        return guardada;
    }

    public SesionCaja exigir(UUID sesionId) {
        var sesion = sesiones.buscarPorId(sesionId)
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "sesion_no_encontrada", "La sesión de caja no existe."));
        // Alcance por establecimiento: la caja de la sesión tiene que ser visible.
        cajas.exigir(sesion.cajaId());
        return sesion;
    }

    private record Instantanea(UUID cajaId, String estado, BigDecimal montoInicial,
            Map<FormaDePago, BigDecimal> declarado, Map<FormaDePago, BigDecimal> calculado) {

        static Instantanea de(SesionCaja sesion) {
            return new Instantanea(sesion.cajaId(), sesion.estado().name(),
                    sesion.montoInicial(), sesion.declarado(), sesion.calculado());
        }
    }

    /** La caja de una sesión, comprobando que el usuario la alcanza. */
    public Caja cajaDe(SesionCaja sesion) {
        return cajas.exigir(sesion.cajaId());
    }
}
