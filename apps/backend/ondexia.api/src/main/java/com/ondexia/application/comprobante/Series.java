package com.ondexia.application.comprobante;

import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comprobante.SerieCorrelativo;
import com.ondexia.domain.comprobante.SerieCorrelativoRepositorio;
import com.ondexia.domain.comprobante.TipoDocumento;
import com.ondexia.domain.comprobante.TiposDeComprobanteRepositorio;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.Conflicto;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.identidad.SucursalRepositorio;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Series de comprobante de la empresa activa.
 *
 * <h2>Lo que este servicio deliberadamente no ofrece</h2>
 *
 * <p><strong>No hay forma de cambiar el número.</strong> Ni de corregirlo, ni de
 * reiniciarlo, ni de ponerlo donde el cliente diga que iba. La única operación
 * que lo mueve es {@link AsignadorDeCorrelativo}, que lo sube de uno en uno
 * dentro de la transacción que crea el comprobante.
 *
 * <p>Es una ausencia intencionada, y conviene dejarla escrita porque es de las
 * que alguien «arregla» más adelante: un endpoint para ajustar el correlativo
 * parece una comodidad razonable —«el cliente migró de otro sistema y va por el
 * 4300»— hasta que se usa para tapar un error y produce dos comprobantes con el
 * mismo número. La migración desde otro sistema se resuelve creando la serie con
 * su número inicial en el alta, una sola vez, que es lo que hace
 * {@link #registrar}.
 *
 * <p>Tampoco hay borrado. El permiso {@code configuracion.serie} solo tiene
 * {@code consultar}, {@code registrar} y {@code editar}, y no es un olvido del
 * catálogo: una serie nombra a todos los comprobantes que emitió.
 */
@Service
public class Series {

    private final SerieCorrelativoRepositorio series;
    private final SucursalRepositorio sucursales;
    private final TiposDeComprobanteRepositorio tipos;
    private final RegistroDeAuditoria auditoria;
    private final ProveedorDeContexto contexto;

    public Series(SerieCorrelativoRepositorio series, SucursalRepositorio sucursales,
            TiposDeComprobanteRepositorio tipos, RegistroDeAuditoria auditoria,
            ProveedorDeContexto contexto) {
        this.series = series;
        this.sucursales = sucursales;
        this.tipos = tipos;
        this.auditoria = auditoria;
        this.contexto = contexto;
    }

    public List<SerieCorrelativo> listar() {
        return series.listar();
    }

    /**
     * Da de alta una serie.
     *
     * @param numeroInicial último número ya emitido en el sistema anterior, o
     *                      {@code 0} si la serie empieza de cero. Es la
     *                      <strong>única</strong> vez que se puede fijar: existe
     *                      para quien migra desde otro sistema y no puede volver
     *                      a numerar desde el 1 sin duplicar comprobantes ya
     *                      declarados
     */
    @Transactional
    public SerieCorrelativo registrar(UUID sucursalId, String tipoDocumento, String serie,
            long numeroInicial) {
        var tipo = TipoDocumento.porCodigo(tipoDocumento);

        // Lo que hace que la pantalla de Comprobantes signifique algo. Sin esta
        // línea sería una declaración que el sistema ignora, y el cliente se
        // enteraría al ver series de un tipo que había apagado.
        if (!tipos.emite(tipo)) {
            throw new ReglaDeNegocioViolada(
                    "tipo_no_habilitado",
                    "La empresa tiene desactivada la emisión de " + tipo.nombre().toLowerCase()
                            + ". Habilítala en Configuración › Comprobantes.");
        }

        var nueva = new SerieCorrelativo(
                UUID.randomUUID(), empresaActiva(), validarSucursal(sucursalId), tipo, serie);

        // Cortesía, para dar un mensaje con la serie dentro. La garantía la da el
        // índice único: entre esta consulta y el guardado cabe otra petición.
        series.buscarPorSerie(tipo, nueva.serie()).ifPresent(existente -> {
            throw new Conflicto(
                    "serie_duplicada",
                    "Ya existe una serie " + nueva.serie() + " para " + tipo.nombre().toLowerCase()
                            + " en esta empresa.");
        });

        aplicarNumeroInicial(nueva, numeroInicial);

        var guardada = series.guardar(nueva);
        auditoria.registrarCreacion("serie_correlativo", guardada.id(), Instantanea.de(guardada));
        return guardada;
    }

    /**
     * Lo único editable es si la serie sigue emitiendo.
     *
     * <p>El establecimiento, el tipo y la serie identifican a los comprobantes ya
     * emitidos; cambiarlos reescribiría documentos con valor tributario. Y el
     * número no se toca por lo dicho en la cabecera de la clase.
     */
    @Transactional
    public SerieCorrelativo cambiarEstado(UUID id, boolean activa) {
        var serie = exigir(id);

        if (serie.estaActiva() == activa) {
            return serie; // Idempotente.
        }

        var antes = Instantanea.de(serie);
        if (activa) {
            serie.activar();
        } else {
            serie.desactivar();
        }

        var guardada = series.guardar(serie);
        auditoria.registrar("serie_correlativo", id, activa ? "ACTIVAR" : "DESACTIVAR",
                antes, Instantanea.de(guardada));
        return guardada;
    }

    /**
     * Sin comprobar la empresa: si la política de RLS no devolvió la fila, aquí
     * llega vacío y se responde «no encontrada». El filtro es de la base.
     */
    private SerieCorrelativo exigir(UUID id) {
        return series.buscarPorId(id)
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "serie_no_encontrada", "La serie no existe."));
    }

    /**
     * El establecimiento sí hay que comprobarlo a mano: {@code sucursal} queda
     * fuera de RLS, así que sin esto se podría colgar una serie de un
     * establecimiento de otro cliente.
     */
    private UUID validarSucursal(UUID sucursalId) {
        if (sucursalId == null) {
            throw new ReglaDeNegocioViolada(
                    "establecimiento_requerido",
                    "La serie necesita un establecimiento: SUNAT la relaciona con el anexo "
                            + "que emite.");
        }

        return sucursales.buscarPorId(sucursalId)
                .filter(s -> s.empresaId().equals(empresaActiva()))
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "establecimiento_invalido",
                        "El establecimiento indicado no existe en esta empresa."))
                .id();
    }

    /**
     * Coloca el punto de partida avanzando el agregado, no escribiéndole el
     * campo.
     *
     * <p>Podría ser un constructor con el número dentro, y sería peor: el
     * agregado dejaría de tener una sola puerta para mover el correlativo, que es
     * la propiedad de la que depende todo lo demás. Aquí el bucle es
     * intrascendente —corre una vez, al dar de alta— y a cambio
     * {@code asignarSiguienteNumero} sigue siendo el único camino.
     */
    private static void aplicarNumeroInicial(SerieCorrelativo serie, long numeroInicial) {
        if (numeroInicial < 0) {
            throw new ReglaDeNegocioViolada(
                    "numero_inicial_invalido",
                    "El número inicial no puede ser negativo.");
        }
        if (numeroInicial > 99_999_999L) {
            throw new ReglaDeNegocioViolada(
                    "numero_inicial_invalido",
                    "El número inicial supera los ocho dígitos que admite SUNAT.");
        }
        for (long i = 0; i < numeroInicial; i++) {
            serie.asignarSiguienteNumero();
        }
    }

    private UUID empresaActiva() {
        return contexto.obligatorio().empresaActivaObligatoria();
    }

    private record Instantanea(
            UUID sucursalId, String tipoDocumento, String serie, long ultimoNumero,
            boolean activa) {

        static Instantanea de(SerieCorrelativo serie) {
            return new Instantanea(
                    serie.sucursalId(),
                    serie.tipoDocumento().codigo(),
                    serie.serie(),
                    serie.ultimoNumero(),
                    serie.estaActiva());
        }
    }
}
