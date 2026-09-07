package com.ondexia.infrastructure.salida.persistencia.ventas;

import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.ventas.FormaDePago;
import com.ondexia.domain.ventas.SesionCaja;
import com.ondexia.domain.ventas.SesionCajaRepositorio;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class SesionCajaAdaptador implements SesionCajaRepositorio {

    private final SesionCajaJpaRepository filas;
    private final ProveedorDeContexto contexto;

    public SesionCajaAdaptador(SesionCajaJpaRepository filas, ProveedorDeContexto contexto) {
        this.filas = filas;
        this.contexto = contexto;
    }

    @Override
    public Optional<SesionCaja> buscarPorId(UUID id) {
        return filas.findById(id).map(SesionCajaAdaptador::aDominio);
    }

    @Override
    public Optional<SesionCaja> buscarAbierta(UUID cajaId) {
        return filas.findByCajaIdAndEstado(cajaId, SesionCaja.Estado.ABIERTA.name())
                .map(SesionCajaAdaptador::aDominio);
    }

    @Override
    public List<SesionCaja> listarAbiertas() {
        return filas.findAllByEstadoOrderByAbiertaEnDesc(SesionCaja.Estado.ABIERTA.name()).stream()
                .map(SesionCajaAdaptador::aDominio)
                .toList();
    }

    @Override
    public List<SesionCaja> listarDeCaja(UUID cajaId) {
        return filas.findAllByCajaIdOrderByAbiertaEnDesc(cajaId).stream()
                .map(SesionCajaAdaptador::aDominio)
                .toList();
    }

    @Override
    @Transactional
    public SesionCaja guardar(SesionCaja sesion) {
        var fila = filas.findById(sesion.id()).orElse(null);
        if (fila == null) {
            fila = new SesionCajaJpa(sesion.id(),
                    contexto.obligatorio().empresaActivaObligatoria(),
                    sesion.cajaId(), sesion.abiertaPor(), sesion.abiertaEn(), sesion.montoInicial());
        }
        fila.cerrarDesde(sesion.cerradaPor(), sesion.cerradaEn(), sesion.estado().name(),
                aJson(sesion.declarado()), aJson(sesion.calculado()));
        return aDominio(filas.save(fila));
    }

    static SesionCaja aDominio(SesionCajaJpa fila) {
        return new SesionCaja(fila.getId(), fila.getCajaId(), fila.getAbiertaPor(),
                fila.getAbiertaEn(), fila.getMontoInicial(), fila.getCerradaPor(),
                fila.getCerradaEn(), SesionCaja.Estado.valueOf(fila.getEstado()),
                deJson(fila.getDeclarado()), deJson(fila.getCalculado()));
    }

    private static Map<String, BigDecimal> aJson(Map<FormaDePago, BigDecimal> mapa) {
        var json = new LinkedHashMap<String, BigDecimal>();
        mapa.forEach((forma, importe) -> json.put(forma.name(), importe));
        return json;
    }

    private static Map<FormaDePago, BigDecimal> deJson(Map<String, BigDecimal> json) {
        var mapa = new EnumMap<FormaDePago, BigDecimal>(FormaDePago.class);
        if (json != null) {
            json.forEach((clave, importe) -> mapa.put(FormaDePago.valueOf(clave), importe));
        }
        return mapa;
    }
}
