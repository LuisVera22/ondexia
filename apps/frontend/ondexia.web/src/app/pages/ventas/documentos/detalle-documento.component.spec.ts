import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { DetalleDocumentoComponent } from './detalle-documento.component';
import { DocumentoVentaApi, VentasApiService } from '../../../nucleo/ventas.api.service';
import { ConfiguracionApiService } from '../../../nucleo/configuracion.api.service';
import { CajaActivaService } from '../../../nucleo/caja-activa.service';
import { ContextoService } from '../../../shared/services/contexto.service';
import { AvisosService } from '../../../shared/services/avisos.service';

describe('Detalle del documento · presentación interna y fiscal', () => {
  const nota: DocumentoVentaApi = {
    id: 'nota-1', tipoNombre: 'Nota de venta', serie: 'NV01', numero: 2, sucursalId: 'suc-1', sesionCajaId: 'ses-1', fechaEmision: '2026-10-08', emitidoPor: 'vendedor', moneda: 'PEN', documentoOrigenId: null, motivo: null, motivoNombre: null, tipo: 'NV', fiscal: false, numeroCompleto: 'NV01-00000002', estado: 'EMITIDO',
    emitidoEn: '2026-10-08T18:00:00Z', cliente: null, pagos: [], origen: null, observaciones: 'PRUEBA',
    totalGravado: 381.36, totalIgv: 68.64, totalExonerado: 0, totalInafecto: 0,
    totalDescuento: 0, total: 450, lineas: [{ orden: 1, descripcion: 'Cajas', codigo: 'CAJ', unidad: 'NIU',
      productoId: 'p-1', cantidad: 3, precioUnitario: 150, valorUnitario: 127.118644, descuento: 0, afectacion: 'GRAVADO', valorVenta: 381.36, igv: 68.64, total: 450 }],
  };

  beforeEach(() => TestBed.configureTestingModule({
    providers: [provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({}) } } },
      { provide: VentasApiService, useValue: {} },
      { provide: ConfiguracionApiService, useValue: {} },
      { provide: CajaActivaService, useValue: { enUso: signal(null) } },
      { provide: ContextoService, useValue: { puede: () => false } },
      { provide: AvisosService, useValue: {} },
    ],
  }));

  function mostrar(documento: DocumentoVentaApi, formato: 'ticket' | 'a4') {
    const vista = TestBed.createComponent(DetalleDocumentoComponent);
    vista.componentInstance.documento.set(documento);
    vista.componentInstance.cargando.set(false);
    vista.componentInstance.error.set(null);
    vista.componentInstance.formato.set(formato);
    vista.detectChanges();
    return vista.nativeElement.querySelector('article') as HTMLElement;
  }

  for (const formato of ['ticket', 'a4'] as const) {
    it(`la nota en ${formato} muestra 450 sin IGV y su naturaleza solo al pie`, () => {
      const hoja = mostrar(nota, formato);
      const resumen = hoja.querySelector('dl:last-of-type')!;
      expect(resumen.textContent).toContain('Importe de venta');
      expect(resumen.textContent).not.toContain('IGV');
      expect(resumen.textContent).not.toContain('Op. gravada');
      expect(Array.from(resumen.querySelectorAll('dd')).map((celda) => celda.textContent?.trim())).toEqual(['S/ 450.00', 'S/ 450.00']);
      expect(hoja.querySelector('footer')?.textContent).toContain('Documento interno de venta. No constituye comprobante de pago ni se envía a SUNAT');
      expect(hoja.textContent).not.toContain('No válido');
      expect(hoja.querySelector('header')?.textContent).not.toContain('Documento interno');
    });
  }

  for (const tipo of ['01', '03'] as const) {
    it(`conserva el desglose fiscal de ${tipo}`, () => {
      const hoja = mostrar({ ...nota, tipo, fiscal: true }, 'ticket');
      const resumen = hoja.querySelector('dl:last-of-type')!;
      expect(Array.from(resumen.querySelectorAll('dd')).map((celda) => celda.textContent?.trim())).toEqual(['S/ 381.36', 'S/ 68.64', 'S/ 450.00']);
      expect(resumen.textContent).toContain('IGV 18 %');
      expect(resumen.textContent).not.toContain('Importe de venta');
    });
  }

  it('no resta de nuevo descuentos ni reclasifica afectaciones mixtas en la nota', () => {
    const hoja = mostrar({ ...nota, totalGravado: 100, totalIgv: 18, totalExonerado: 50,
      totalInafecto: 32, totalDescuento: 10, total: 200 }, 'a4');
    const resumen = hoja.querySelector('dl:last-of-type')!;
    expect(Array.from(resumen.querySelectorAll('dd')).map((celda) => celda.textContent?.trim())).toEqual(['S/ 200.00', 'S/ 200.00']);
    expect(resumen.textContent).not.toContain('Op. gravada');
  });
});
