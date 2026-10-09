package com.ondexia.application.ventas;

import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.Conflicto;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.identidad.SucursalRepositorio;
import com.ondexia.domain.ventas.Caja;
import com.ondexia.domain.ventas.CajaRepositorio;
import com.ondexia.domain.ventas.SesionCajaRepositorio;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las cajas de la empresa activa: alta, nombre y estado.
 *
 * <p>Sigue la forma de {@code Almacenes}, con una diferencia que importa: la caja
 * exige establecimiento, y quien opera acotado a un establecimiento solo ve y
 * toca las cajas de ese establecimiento. Es lo que hace útil el alcance por
 * sucursal de {@code usuario_empresa}: el cajero de Miraflores no abre la caja de
 * San Isidro por un descuido de un desplegable (doc 04 §3.1).
 */
@Service
public class Cajas {

    /** Código y nombre de la caja que nace con cada establecimiento. */
    public static final String CODIGO_PRIMERA = "CAJA1";
    public static final String NOMBRE_PRIMERA = "Caja 1";

    private final CajaRepositorio cajas;
    private final SesionCajaRepositorio sesiones;
    private final SucursalRepositorio sucursales;
    private final RegistroDeAuditoria auditoria;
    private final ProveedorDeContexto contexto;

    public Cajas(CajaRepositorio cajas, SesionCajaRepositorio sesiones,
            SucursalRepositorio sucursales, RegistroDeAuditoria auditoria,
            ProveedorDeContexto contexto) {
        this.cajas = cajas;
        this.sesiones = sesiones;
        this.sucursales = sucursales;
        this.auditoria = auditoria;
        this.contexto = contexto;
    }

    /** Las que el usuario alcanza: todas, o las de su establecimiento. */
    public List<Caja> listar() {
        var actual = contexto.obligatorio();
        return cajas.listar().stream()
                .filter(caja -> actual.alcanzaTodasLasSucursales()
                        || caja.sucursalId().equals(actual.sucursalId()))
                .toList();
    }

    @Transactional
    public Caja registrar(String codigo, String nombre, UUID sucursalId) {
        var caja = new Caja(UUID.randomUUID(), empresaActiva(), validarSucursal(sucursalId),
                codigo, nombre);

        // Cortesía: el índice único da la garantía, esto da el mensaje.
        cajas.buscarPorCodigo(caja.sucursalId(), caja.codigo()).ifPresent(existente -> {
            throw new Conflicto(
                    "codigo_duplicado",
                    "Ya existe una caja con el código " + caja.codigo()
                            + " en ese establecimiento.",
                    "codigo");
        });

        var guardada = cajas.guardar(caja);
        auditoria.registrarCreacion("caja", guardada.id(), Instantanea.de(guardada));
        return guardada;
    }

    @Transactional
    public Caja renombrar(UUID id, String nombre) {
        var caja = exigir(id);
        var antes = Instantanea.de(caja);
        caja.renombrar(nombre);
        var guardada = cajas.guardar(caja);
        auditoria.registrarActualizacion("caja", id, antes, Instantanea.de(guardada));
        return guardada;
    }

    @Transactional
    public Caja cambiarEstado(UUID id, boolean activa) {
        var caja = exigir(id);
        if (caja.estaActiva() == activa) {
            return caja; // Idempotente.
        }
        if (!activa && sesiones.buscarAbierta(id).isPresent()) {
            throw new ReglaDeNegocioViolada(
                    "caja_con_sesion_abierta",
                    "La caja tiene una sesión abierta. Ciérrala antes de desactivarla.");
        }

        var antes = Instantanea.de(caja);
        if (activa) {
            caja.activar();
        } else {
            caja.desactivar();
        }
        var guardada = cajas.guardar(caja);
        auditoria.registrar("caja", id, activa ? "ACTIVAR" : "DESACTIVAR",
                antes, Instantanea.de(guardada));
        return guardada;
    }

    /**
     * La caja, si existe y el usuario la alcanza. Fuera de su establecimiento se
     * responde «no encontrada» y no «prohibida»: decir prohibida confirmaría que
     * ese identificador existe.
     */
    Caja exigir(UUID id) {
        var actual = contexto.obligatorio();
        return cajas.buscarPorId(id)
                .filter(caja -> actual.alcanzaTodasLasSucursales()
                        || caja.sucursalId().equals(actual.sucursalId()))
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "caja_no_encontrada", "La caja no existe."));
    }

    private UUID validarSucursal(UUID sucursalId) {
        if (sucursalId == null) {
            throw new ReglaDeNegocioViolada(
                    "establecimiento_requerido",
                    "La caja tiene que pertenecer a un establecimiento.");
        }
        var actual = contexto.obligatorio();
        if (!actual.alcanzaTodasLasSucursales() && !sucursalId.equals(actual.sucursalId())) {
            throw new ReglaDeNegocioViolada(
                    "establecimiento_fuera_de_alcance",
                    "Solo puedes crear cajas en tu establecimiento.");
        }
        return sucursales.buscarPorId(sucursalId)
                .filter(s -> s.empresaId().equals(empresaActiva()))
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "establecimiento_invalido",
                        "El establecimiento indicado no existe en esta empresa."))
                .id();
    }

    private UUID empresaActiva() {
        return contexto.obligatorio().empresaActivaObligatoria();
    }

    private record Instantanea(String codigo, String nombre, UUID sucursalId, boolean activa) {

        static Instantanea de(Caja caja) {
            return new Instantanea(caja.codigo(), caja.nombre(), caja.sucursalId(),
                    caja.estaActiva());
        }
    }
}
