package com.ondexia.infrastructure.salida.persistencia.ventas;

import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.ventas.Cliente;
import com.ondexia.domain.ventas.ClienteRepositorio;
import com.ondexia.domain.ventas.TipoDocumentoIdentidad;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ClienteAdaptador implements ClienteRepositorio {

    private final ClienteJpaRepository filas;
    private final ProveedorDeContexto contexto;

    public ClienteAdaptador(ClienteJpaRepository filas, ProveedorDeContexto contexto) {
        this.filas = filas;
        this.contexto = contexto;
    }

    @Override
    public Optional<Cliente> buscarPorId(UUID id) {
        return filas.findById(id).map(ClienteAdaptador::aDominio);
    }

    @Override
    public Optional<Cliente> buscarPorDocumento(TipoDocumentoIdentidad tipo, String numero) {
        return filas.findByTipoDocumentoAndNumeroDocumento(tipo.codigo(), numero)
                .map(ClienteAdaptador::aDominio);
    }

    @Override
    public List<Cliente> buscar(String texto, int maximo) {
        return filas.buscar(texto, maximo).stream().map(ClienteAdaptador::aDominio).toList();
    }

    @Override
    public List<Cliente> listar() {
        return filas.findAllByOrderByNombreAsc().stream().map(ClienteAdaptador::aDominio).toList();
    }

    @Override
    @Transactional
    public Cliente guardar(Cliente cliente) {
        var fila = filas.findById(cliente.id()).orElseGet(() -> new ClienteJpa(cliente.id(),
                contexto.obligatorio().empresaActivaObligatoria(), cliente.tipoDocumento().codigo(),
                cliente.numeroDocumento()));
        fila.actualizarDesde(cliente.nombre(), cliente.direccion(), cliente.correo(),
                cliente.telefono(), cliente.verificadoEn(), cliente.estaActivo());
        return aDominio(filas.save(fila));
    }

    static Cliente aDominio(ClienteJpa fila) {
        return new Cliente(fila.getId(), fila.getEmpresaId(),
                TipoDocumentoIdentidad.porCodigo(fila.getTipoDocumento()), fila.getNumeroDocumento(),
                fila.getNombre(), fila.getDireccion(), fila.getCorreo(), fila.getTelefono(),
                fila.getVerificadoEn(), fila.isActivo());
    }
}
