package com.ondexia.application.emision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ondexia.domain.comprobante.BusDeEmision;
import com.ondexia.domain.comprobante.ComprobanteElectronico;
import com.ondexia.domain.comprobante.ComprobanteElectronicoRepositorio;
import com.ondexia.domain.comprobante.EstadoSunat;
import com.ondexia.domain.comprobante.OrdenDeEmision;
import com.ondexia.domain.comprobante.ResultadoDeEmision;
import com.ondexia.domain.comprobante.TipoDocumento;
import com.ondexia.domain.identidad.EmpresaRepositorio;
import com.ondexia.domain.identidad.SucursalRepositorio;
import com.ondexia.domain.ventas.DocumentoVentaRepositorio;
import com.ondexia.domain.ventas.DocumentoVenta;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FronteraResultadosEmisionTest {
    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void consultaYAplicacionDirectaNoPersistenResultadoAjeno(boolean consulta, boolean otraEmpresa) {
        UUID id = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID empresa = UUID.fromString("00000000-0000-4000-8000-000000000002");
        UUID documento = UUID.fromString("00000000-0000-4000-8000-000000000003");
        Instant momento = Instant.parse("2026-10-11T03:00:00Z");
        var comprobante = ComprobanteElectronico.encolar(id, empresa, documento,
                TipoDocumento.FACTURA, "F001", 15, momento);
        var resultado = new ResultadoDeEmision(id, otraEmpresa ? documento : empresa,
                otraEmpresa ? OrdenDeEmision.Operacion.EMITIR : OrdenDeEmision.Operacion.VERIFICAR_CREDENCIALES,
                EstadoSunat.ACEPTADO, "0", "Aceptación sintética", List.of(), "xml-ajeno", "cdr-ajeno",
                "firma-ajena", null, null, momento.plusSeconds(30));
        var comprobantes = mock(ComprobanteElectronicoRepositorio.class);
        var documentos = mock(DocumentoVentaRepositorio.class);
        var bus = mock(BusDeEmision.class);
        when(comprobantes.buscarPorDocumento(documento)).thenReturn(Optional.of(comprobante));
        when(comprobantes.buscarPorId(id)).thenReturn(Optional.of(comprobante));
        when(bus.resultadoDe(empresa, id)).thenReturn(Optional.of(resultado));
        when(documentos.buscarPorId(documento)).thenReturn(Optional.of(mock(DocumentoVenta.class)));
        when(comprobantes.guardar(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        var emision = new EmisionElectronica(comprobantes, documentos, mock(EmpresaRepositorio.class),
                mock(SucursalRepositorio.class), bus, Clock.systemUTC());

        assertThatThrownBy(() -> {
            if (consulta) emision.estadoDe(documento);
            else emision.aplicarResultado(resultado);
        }).isInstanceOf(IllegalArgumentException.class);
        assertThat(comprobante.estado()).isEqualTo(EstadoSunat.EN_COLA);
        verify(comprobantes, never()).guardar(any());
        verifyNoInteractions(documentos);
    }
}
