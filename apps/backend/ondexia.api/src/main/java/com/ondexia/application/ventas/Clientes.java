package com.ondexia.application.ventas;

import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.Conflicto;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.consultas.DatosDeRuc;
import com.ondexia.domain.consultas.VerificacionDeRuc;
import com.ondexia.domain.ventas.Cliente;
import com.ondexia.domain.ventas.ClienteRepositorio;
import com.ondexia.domain.ventas.TipoDocumentoIdentidad;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los adquirentes de la empresa activa (doc 12 §4.3).
 *
 * <p>Un cliente con RUC puede llegar con la atestación de la consulta al padrón
 * —la misma firma que verifica el alta de empresas— y entonces la razón social
 * y el domicilio salen de ahí y no del formulario. Sin atestación también se
 * admite, porque el servicio de consulta puede no estar y el mostrador no
 * puede esperar; queda sin {@code verificadoEn}, y la pantalla lo dice.
 */
@Service
public class Clientes {

    private static final int MAXIMO_BUSQUEDA = 50;

    private final ClienteRepositorio clientes;
    private final VerificacionDeRuc verificacion;
    private final RegistroDeAuditoria auditoria;
    private final ProveedorDeContexto contexto;
    private final Clock reloj;

    public Clientes(ClienteRepositorio clientes, VerificacionDeRuc verificacion,
            RegistroDeAuditoria auditoria, ProveedorDeContexto contexto, Clock reloj) {
        this.clientes = clientes;
        this.verificacion = verificacion;
        this.auditoria = auditoria;
        this.contexto = contexto;
        this.reloj = reloj;
    }

    public record Datos(String nombre, String direccion, String correo, String telefono) {
    }

    public List<Cliente> listar() {
        return clientes.listar();
    }

    public List<Cliente> buscar(String texto) {
        if (texto == null || texto.isBlank()) {
            return clientes.listar();
        }
        return clientes.buscar(texto.trim(), MAXIMO_BUSQUEDA);
    }

    public Cliente obtener(UUID id) {
        return exigir(id);
    }

    /**
     * @param atestacion opcional, solo con RUC: lo que devolvió la consulta al
     *                   padrón. Si viene, el RUC del formulario tiene que ser
     *                   el atestado, y la razón social se toma de la firma
     */
    @Transactional
    public Cliente registrar(TipoDocumentoIdentidad tipo, String numero, Datos datos,
            String atestacion) {
        DatosDeRuc padron = comprobarAtestacion(tipo, atestacion);

        var cliente = new Cliente(UUID.randomUUID(), empresaActiva(), tipo, numero,
                padron != null ? padron.razonSocial() : datos.nombre(),
                datos.direccion(), datos.correo(), datos.telefono());

        if (padron != null) {
            if (!padron.ruc().valor().equals(cliente.numeroDocumento())) {
                throw new ReglaDeNegocioViolada(
                        "atestacion_de_otro_ruc",
                        "La verificación corresponde a otro RUC. Vuelve a consultar.",
                        "numeroDocumento");
            }
            cliente.verificarConPadron(padron.razonSocial(), padron.domicilioFiscal(),
                    reloj.instant());
        }

        // Cortesía: el índice único da la garantía, esto da el mensaje.
        clientes.buscarPorDocumento(cliente.tipoDocumento(), cliente.numeroDocumento())
                .ifPresent(existente -> {
                    throw new Conflicto(
                            "documento_duplicado",
                            "Ya existe un cliente con ese " + tipo.nombre() + ".",
                            "numeroDocumento");
                });

        var guardado = clientes.guardar(cliente);
        auditoria.registrarCreacion("cliente", guardado.id(), Instantanea.de(guardado));
        return guardado;
    }

    @Transactional
    public Cliente actualizar(UUID id, Datos datos) {
        var cliente = exigir(id);
        var antes = Instantanea.de(cliente);
        cliente.actualizar(datos.nombre(), datos.direccion(), datos.correo(), datos.telefono());
        var guardado = clientes.guardar(cliente);
        auditoria.registrarActualizacion("cliente", id, antes, Instantanea.de(guardado));
        return guardado;
    }

    /** Vuelve a comprobar el RUC contra el padrón y toma de ahí lo que no se edita. */
    @Transactional
    public Cliente verificar(UUID id, String atestacion) {
        var cliente = exigir(id);
        if (!cliente.admiteFactura()) {
            throw new ReglaDeNegocioViolada(
                    "sin_ruc", "Solo un cliente con RUC se verifica contra el padrón.");
        }
        DatosDeRuc padron = comprobarAtestacion(TipoDocumentoIdentidad.RUC, atestacion);
        if (padron == null) {
            throw new ReglaDeNegocioViolada(
                    "atestacion_requerida", "Falta la verificación del RUC.", "atestacion");
        }
        if (!padron.ruc().valor().equals(cliente.numeroDocumento())) {
            throw new ReglaDeNegocioViolada(
                    "atestacion_de_otro_ruc",
                    "La verificación corresponde a otro RUC.", "atestacion");
        }
        var antes = Instantanea.de(cliente);
        cliente.verificarConPadron(padron.razonSocial(), padron.domicilioFiscal(), reloj.instant());
        var guardado = clientes.guardar(cliente);
        auditoria.registrar("cliente", id, "VERIFICAR", antes, Instantanea.de(guardado));
        return guardado;
    }

    @Transactional
    public Cliente cambiarEstado(UUID id, boolean activo) {
        var cliente = exigir(id);
        if (cliente.estaActivo() == activo) {
            return cliente; // Idempotente.
        }
        var antes = Instantanea.de(cliente);
        if (activo) {
            cliente.activar();
        } else {
            cliente.desactivar();
        }
        var guardado = clientes.guardar(cliente);
        auditoria.registrar("cliente", id, activo ? "ACTIVAR" : "DESACTIVAR",
                antes, Instantanea.de(guardado));
        return guardado;
    }

    private DatosDeRuc comprobarAtestacion(TipoDocumentoIdentidad tipo, String atestacion) {
        if (atestacion == null || atestacion.isBlank()) {
            return null;
        }
        if (tipo != TipoDocumentoIdentidad.RUC) {
            throw new ReglaDeNegocioViolada(
                    "sin_ruc", "La verificación del padrón solo aplica a un cliente con RUC.",
                    "atestacion");
        }
        // Firmada para quien la pidió: la misma regla que en el alta de empresas.
        return verificacion.comprobar(atestacion, contexto.obligatorio().sub());
    }

    private Cliente exigir(UUID id) {
        return clientes.buscarPorId(id)
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "cliente_no_encontrado", "El cliente no existe."));
    }

    private UUID empresaActiva() {
        return contexto.obligatorio().empresaActivaObligatoria();
    }

    private record Instantanea(String tipoDocumento, String numeroDocumento, String nombre,
            String direccion, String correo, String telefono, boolean verificado, boolean activo) {

        static Instantanea de(Cliente c) {
            return new Instantanea(c.tipoDocumento().codigo(), c.numeroDocumento(), c.nombre(),
                    c.direccion(), c.correo(), c.telefono(), c.verificadoEn() != null,
                    c.estaActivo());
        }
    }
}
