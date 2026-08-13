package com.ondexia.application.configuracion;

import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.Ubigeo;
import com.ondexia.domain.comun.error.Conflicto;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.identidad.Sucursal;
import com.ondexia.domain.identidad.SucursalRepositorio;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Establecimientos de la empresa activa.
 *
 * <p>Los cuatro casos de uso viven juntos porque comparten una invariante que
 * conviene tener a la vista de una sola lectura: <strong>toda operación se
 * limita a la empresa del contexto</strong>. Repartirlos en cuatro archivos
 * dejaría esa comprobación copiada cuatro veces, que es como se acaba
 * escribiendo tres correctas y una no.
 *
 * <p>«Establecimiento» de cara al usuario, {@code Sucursal} en el dominio. Es la
 * palabra que usa SUNAT para el anexo que emite comprobantes, y la que aparece
 * en la ficha RUC que el contribuyente tiene delante.
 */
@Service
public class Establecimientos {

    private final SucursalRepositorio sucursales;
    private final RegistroDeAuditoria auditoria;
    private final ProveedorDeContexto contexto;

    public Establecimientos(SucursalRepositorio sucursales, RegistroDeAuditoria auditoria,
            ProveedorDeContexto contexto) {
        this.sucursales = sucursales;
        this.auditoria = auditoria;
        this.contexto = contexto;
    }

    public List<Sucursal> listar() {
        return sucursales.listarDeEmpresa(empresaActiva());
    }

    /**
     * @param codigo el que asigna SUNAT: cuatro dígitos, {@code 0000} para la
     *               casa matriz. No se genera aquí — lo da la ficha RUC, y
     *               inventarlo produciría comprobantes con un anexo que SUNAT no
     *               reconoce
     */
    @Transactional
    public Sucursal registrar(String codigo, String nombre, String direccion, String ubigeo) {
        var empresaId = empresaActiva();
        String normalizado = normalizarCodigo(codigo);

        // Se comprueba antes por cortesía, para dar un mensaje claro. La
        // garantía de verdad la da el índice único de la base: entre esta
        // consulta y el guardado cabe otra petición, y solo la restricción
        // cubre esa rendija.
        sucursales.buscarPorCodigo(empresaId, normalizado).ifPresent(existente -> {
            throw new Conflicto(
                    "codigo_duplicado",
                    "Ya existe un establecimiento con el código " + normalizado + ".");
        });

        var sucursal = new Sucursal(
                UUID.randomUUID(), empresaId, normalizado,
                exigirTexto(nombre, "nombre_requerido", "El establecimiento necesita un nombre."),
                exigirTexto(direccion, "direccion_requerida", "La dirección es obligatoria."));

        if (ubigeo != null && !ubigeo.isBlank()) {
            sucursal.actualizar(sucursal.nombre(), sucursal.direccion(), new Ubigeo(ubigeo));
        }

        var guardada = sucursales.guardar(sucursal);
        auditoria.registrarCreacion("sucursal", guardada.id(), Instantanea.de(guardada));
        return guardada;
    }

    /**
     * El código no se puede cambiar: identifica al establecimiento ante SUNAT y
     * ya está impreso en las series que cuelgan de él.
     */
    @Transactional
    public Sucursal actualizar(UUID id, String nombre, String direccion, String ubigeo) {
        var sucursal = exigirDeEstaEmpresa(id);
        var antes = Instantanea.de(sucursal);

        sucursal.actualizar(
                exigirTexto(nombre, "nombre_requerido", "El establecimiento necesita un nombre."),
                exigirTexto(direccion, "direccion_requerida", "La dirección es obligatoria."),
                ubigeo == null || ubigeo.isBlank() ? null : new Ubigeo(ubigeo));

        var guardada = sucursales.guardar(sucursal);
        auditoria.registrarActualizacion("sucursal", id, antes, Instantanea.de(guardada));
        return guardada;
    }

    /**
     * Desactiva, nunca borra.
     *
     * <p>Un establecimiento aparece en los comprobantes que ya se emitieron.
     * Borrarlo dejaría documentos apuntando a nada y rompería el libro
     * electrónico; desactivarlo lo saca de los desplegables y conserva la
     * historia.
     */
    @Transactional
    public void desactivar(UUID id) {
        var sucursal = exigirDeEstaEmpresa(id);

        if (!sucursal.estaActiva()) {
            return; // Idempotente: repetir la operación no es un error.
        }

        var antes = Instantanea.de(sucursal);
        sucursal.desactivar();
        var guardada = sucursales.guardar(sucursal);

        auditoria.registrar("sucursal", id, "DESACTIVAR", antes, Instantanea.de(guardada));
    }

    private UUID empresaActiva() {
        return contexto.obligatorio().empresaActivaObligatoria();
    }

    /**
     * Carga el establecimiento y comprueba que sea de la empresa activa.
     *
     * <p>La tabla {@code sucursal} está fuera de las políticas de RLS —es una de
     * las que hay que leer <em>para saber</em> cuál es la empresa, así que no
     * puede depender de conocerla—. Esa excepción está documentada, y su precio
     * es exactamente este método: aquí el filtro es responsabilidad del código.
     *
     * <p>Se responde «no encontrado» y no «prohibido» a propósito: decir
     * «prohibido» confirmaría que ese identificador existe en otra empresa.
     */
    private Sucursal exigirDeEstaEmpresa(UUID id) {
        var sucursal = sucursales.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontrado(
                        "establecimiento_no_encontrado", "El establecimiento no existe."));

        if (!sucursal.empresaId().equals(empresaActiva())) {
            throw new RecursoNoEncontrado(
                    "establecimiento_no_encontrado", "El establecimiento no existe.");
        }
        return sucursal;
    }

    private static String normalizarCodigo(String codigo) {
        if (codigo == null || !codigo.trim().matches("\\d{4}")) {
            throw new ReglaDeNegocioViolada(
                    "codigo_invalido",
                    "El código de establecimiento son cuatro dígitos, como aparece en la "
                            + "ficha RUC. La casa matriz es 0000.");
        }
        return codigo.trim();
    }

    /**
     * El nombre y la dirección son {@code NOT NULL} en el esquema (V1).
     *
     * <p>Comprobarlo aquí no es redundante con la validación del controlador:
     * dejar que lo cace la base produce un error de integridad que se traduce a
     * un 409 genérico —«choca con un dato existente»— que no menciona el campo
     * ni se parece a lo que ha pasado.
     */
    private static String exigirTexto(String valor, String codigo, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new ReglaDeNegocioViolada(codigo, mensaje);
        }
        return valor.trim();
    }

    private record Instantanea(
            String codigo, String nombre, String direccion, String ubigeo, boolean activa) {

        static Instantanea de(Sucursal sucursal) {
            return new Instantanea(
                    sucursal.codigo(),
                    sucursal.nombre(),
                    sucursal.direccion(),
                    sucursal.ubigeo() == null ? null : sucursal.ubigeo().valor(),
                    sucursal.estaActiva());
        }
    }
}
