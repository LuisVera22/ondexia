package com.ondexia.application.almacen;

import com.ondexia.domain.almacen.Almacen;
import com.ondexia.domain.almacen.AlmacenRepositorio;
import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comun.error.Conflicto;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.identidad.SucursalRepositorio;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Almacenes de la empresa activa.
 *
 * <p>Comparado con {@code Establecimientos}, este servicio es notablemente más
 * corto, y la diferencia es toda del aislamiento: allí cada método tenía que
 * comprobar a mano que la fila fuera de la empresa activa, porque
 * {@code sucursal} queda fuera de las políticas de RLS. Aquí no hay ninguna
 * comprobación de ese tipo — <strong>la base no devuelve filas de otra
 * empresa</strong>, así que buscar por identificador ya es seguro.
 *
 * <p>Esa reducción es el motivo de que esta entrega vaya antes que series y
 * correlativos: demuestra sobre una tabla real que el aislamiento funciona, con
 * una entidad tan simple que un fallo solo podría estar ahí.
 */
@Service
public class Almacenes {

    private final AlmacenRepositorio almacenes;
    private final SucursalRepositorio sucursales;
    private final RegistroDeAuditoria auditoria;
    private final ProveedorDeContexto contexto;

    public Almacenes(AlmacenRepositorio almacenes, SucursalRepositorio sucursales,
            RegistroDeAuditoria auditoria, ProveedorDeContexto contexto) {
        this.almacenes = almacenes;
        this.sucursales = sucursales;
        this.auditoria = auditoria;
        this.contexto = contexto;
    }

    public List<Almacen> listar() {
        return almacenes.listar();
    }

    /**
     * @param sucursalId opcional: hay almacenes que no cuelgan de ningún
     *                   establecimiento — mercadería en tránsito, por ejemplo
     */
    @Transactional
    public Almacen registrar(String codigo, String nombre, UUID sucursalId) {
        var almacen = new Almacen(UUID.randomUUID(), empresaActiva(), codigo, nombre,
                validarSucursal(sucursalId));

        // Cortesía, para dar un mensaje con el código dentro. La garantía la da
        // el índice único: entre esta consulta y el guardado cabe otra petición.
        almacenes.buscarPorCodigo(almacen.codigo()).ifPresent(existente -> {
            throw new Conflicto(
                    "codigo_duplicado",
                    "Ya existe un almacén con el código " + almacen.codigo() + ".",
                    "codigo");
        });

        var guardado = almacenes.guardar(almacen);
        auditoria.registrarCreacion("almacen", guardado.id(), Instantanea.de(guardado));
        return guardado;
    }

    @Transactional
    public Almacen actualizar(UUID id, String nombre, UUID sucursalId) {
        var almacen = exigir(id);
        var antes = Instantanea.de(almacen);

        almacen.actualizar(nombre, validarSucursal(sucursalId));

        var guardado = almacenes.guardar(almacen);
        auditoria.registrarActualizacion("almacen", id, antes, Instantanea.de(guardado));
        return guardado;
    }

    /**
     * Desactiva, nunca borra: el almacén aparece en cada movimiento de stock que
     * lo tocó, y borrarlo dejaría el kardex apuntando a nada.
     */
    @Transactional
    public void desactivar(UUID id) {
        var almacen = exigir(id);
        if (!almacen.estaActivo()) {
            return; // Idempotente.
        }

        var antes = Instantanea.de(almacen);
        almacen.desactivar();
        var guardado = almacenes.guardar(almacen);

        auditoria.registrar("almacen", id, "DESACTIVAR", antes, Instantanea.de(guardado));
    }

    /**
     * Sin comprobar la empresa: si la política de RLS no devolvió la fila, aquí
     * llega vacío y se responde «no encontrado». El filtro es de la base.
     */
    private Almacen exigir(UUID id) {
        return almacenes.buscarPorId(id)
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "almacen_no_encontrado", "El almacén no existe."));
    }

    /**
     * La sucursal sí hay que comprobarla a mano.
     *
     * <p>Su tabla queda fuera de RLS —es de las que hay que leer <em>para
     * saber</em> cuál es la empresa—, así que sin esto se podría colgar un
     * almacén de un establecimiento de otro cliente. Es justo la clase de hueco
     * que el aislamiento cierra en las tablas que sí protege.
     */
    private UUID validarSucursal(UUID sucursalId) {
        if (sucursalId == null) {
            return null;
        }

        var sucursal = sucursales.buscarPorId(sucursalId)
                .filter(s -> s.empresaId().equals(empresaActiva()))
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "establecimiento_invalido",
                        "El establecimiento indicado no existe en esta empresa."));

        return sucursal.id();
    }

    private UUID empresaActiva() {
        return contexto.obligatorio().empresaActivaObligatoria();
    }

    private record Instantanea(String codigo, String nombre, UUID sucursalId, boolean activo) {

        static Instantanea de(Almacen almacen) {
            return new Instantanea(almacen.codigo(), almacen.nombre(), almacen.sucursalId(),
                    almacen.estaActivo());
        }
    }
}
