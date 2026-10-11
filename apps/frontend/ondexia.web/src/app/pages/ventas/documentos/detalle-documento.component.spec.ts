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
    logoPrincipal: null, logoTicket: null, id: 'nota-1', tipoNombre: 'Nota de venta', serie: 'NV01', numero: 2, sucursalId: 'suc-1', sesionCajaId: 'ses-1', fechaEmision: '2026-10-08', emitidoPor: 'vendedor', moneda: 'PEN', documentoOrigenId: null, motivo: null, motivoNombre: null, tipo: 'NV', fiscal: false, numeroCompleto: 'NV01-00000002', estado: 'EMITIDO',
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
    const boton = Array.from(vista.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find((b) => b.textContent?.includes('Previsualizar comprobante'));
    boton?.click();
    vista.detectChanges();
    return vista.nativeElement.querySelector('article') as HTMLElement;
  }

  it('abre como registro y permite previsualizar y volver sin modificar el documento', () => {
    const vista = TestBed.createComponent(DetalleDocumentoComponent);
    vista.componentInstance.documento.set(nota);
    vista.componentInstance.cargando.set(false);
    vista.componentInstance.error.set(null);
    vista.detectChanges();
    const raiz = vista.nativeElement as HTMLElement;
    const hoja = raiz.querySelector('article')!;
    const registro = () => raiz.querySelector('[aria-label="Registro de venta"]');
    const pulsar = (texto: string) => {
      const boton = Array.from(raiz.querySelectorAll('button')).find((b) => b.textContent?.includes(texto));
      expect(boton).withContext(texto).toBeDefined();
      boton?.click();
      vista.detectChanges();
    };
    expect(registro()?.textContent).toContain('NV01-00000002');
    expect(registro()?.textContent).toContain('Emitido');
    expect(registro()?.textContent).toContain('Cajas');
    expect(registro()?.textContent).toContain('450.00');
    expect(registro()?.textContent).not.toContain('IGV');
    expect(hoja.style.display).toBe('none');
    expect(raiz.querySelector('[aria-label="Formato de impresión"]')).toBeNull();
    pulsar('Previsualizar comprobante');
    expect(hoja.style.display).toBe('');
    expect(registro()).toBeNull();
    expect(raiz.querySelector('[aria-label="Formato de impresión"]')).not.toBeNull();
    pulsar('A4');
    expect(hoja.classList.contains('hoja--ticket')).toBeFalse();
    pulsar('Volver al registro');
    expect(hoja.style.display).toBe('none');
    expect(registro()).not.toBeNull();
    expect(vista.componentInstance.documento()).toBe(nota);
  });

  for (const tipo of ['01', '03', '07'] as const) {
    it(`el registro fiscal ${tipo} conserva base, IGV, total y leyenda en la previsualización`, () => {
      const vista = TestBed.createComponent(DetalleDocumentoComponent);
      vista.componentInstance.documento.set({ ...nota, tipo, fiscal: true });
      vista.componentInstance.cargando.set(false);
      vista.componentInstance.error.set(null);
      vista.detectChanges();
      const raiz = vista.nativeElement as HTMLElement;
      const resumen = raiz.querySelector('[aria-label="Registro de venta"] section:last-child dl')!;
      expect(Array.from(resumen.querySelectorAll('dd')).map((celda) => celda.textContent?.trim())).toEqual(['S/ 381.36', 'S/ 68.64', 'S/ 450.00']);
      expect(resumen.textContent).toContain('IGV 18 %');
      expect(raiz.querySelector('article')?.style.display).toBe('none');
      const boton = Array.from(raiz.querySelectorAll('button')).find((b) => b.textContent?.includes('Previsualizar comprobante'))!;
      boton.click();
      vista.detectChanges();
      expect(raiz.querySelector('article footer')?.textContent).toContain('Representación impresa del comprobante electrónico');
    });
  }

  for (const formato of ['ticket', 'a4'] as const) {
    it(`la nota en ${formato} muestra 450 sin IGV ni leyenda interna al pie`, () => {
      const hoja = mostrar(nota, formato);
      const resumen = hoja.querySelector('dl:last-of-type')!;
      expect(resumen.textContent).toContain('Importe de venta');
      expect(resumen.textContent).not.toContain('IGV');
      expect(resumen.textContent).not.toContain('Op. gravada');
      expect(Array.from(resumen.querySelectorAll('dd')).map((celda) => celda.textContent?.trim())).toEqual(['S/ 450.00', 'S/ 450.00']);
      expect(hoja.querySelector('footer')).toBeNull();
      expect(hoja.textContent).not.toContain('Documento interno de venta');
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

  for (const formato of ['ticket', 'a4'] as const) {
    it(`muestra exclusivamente la versión guardada para ${formato}`, () => {
      const documento = { ...nota, logoPrincipal: '/logos/principal-A.png', logoTicket: '/logos/ticket-A.png' };
      const hoja = mostrar(documento, formato);
      const imagen = hoja.querySelector('header img') as HTMLImageElement;
      expect(imagen).not.toBeNull();
      expect(imagen?.getAttribute('src')).toBe(formato === 'a4' ? '/logos/principal-A.png' : '/logos/ticket-A.png');
      expect(hoja.querySelector('header')?.classList.contains('cabecera--a4')).toBe(formato === 'a4');
    });

    it(`un documento sin versión no añade logo en ${formato}`, () => {
      const hoja = mostrar(nota, formato);
      expect(hoja.querySelector('header img')).toBeNull();
    });
  }

  for (const formato of ['ticket', 'a4'] as const) {
    it(`la cabecera ${formato} usa los datos del momento de emisión`, () => {
      const hoja = mostrar({ ...nota, datosHistoricos: {
        emisor: { ruc: '20100000009', razonSocial: 'Emisor al emitir', nombreComercial: 'Marca al emitir',
          domicilioFiscal: 'Domicilio al emitir', ubigeo: '150101' },
        local: { id: 'suc-1', nombre: 'Local al emitir', direccion: 'Dirección del local al emitir',
          ubigeo: '150101', codigo: '0000' }, cliente: null,
      } }, formato);
      expect(hoja.querySelector('header')?.textContent).toContain('Emisor al emitir');
      expect(hoja.querySelector('header')?.textContent).toContain('Dirección del local al emitir');
      expect(hoja.querySelector('header')?.textContent).toContain('20100000009');
    });
  }

  it('distingue el legado y no presenta la falta de datos como cliente anónimo', () => {
    const vista = TestBed.createComponent(DetalleDocumentoComponent);
    vista.componentInstance.documento.set(nota);
    vista.componentInstance.cargando.set(false);
    vista.componentInstance.error.set(null);
    vista.detectChanges();
    expect(vista.nativeElement.querySelector('[role="status"]')?.textContent).toContain('datos originales');
    expect(vista.nativeElement.querySelector('[aria-label="Registro de venta"]')?.textContent)
      .toContain('Datos originales no disponibles');
    expect(vista.nativeElement.querySelector('[aria-label="Registro de venta"]')?.textContent)
      .not.toContain('Cliente varios');
  });

  it('no resta de nuevo descuentos ni reclasifica afectaciones mixtas en la nota', () => {
    const hoja = mostrar({ ...nota, totalGravado: 100, totalIgv: 18, totalExonerado: 50,
      totalInafecto: 32, totalDescuento: 10, total: 200 }, 'a4');
    const resumen = hoja.querySelector('dl:last-of-type')!;
    expect(Array.from(resumen.querySelectorAll('dd')).map((celda) => celda.textContent?.trim())).toEqual(['S/ 200.00', 'S/ 200.00']);
    expect(resumen.textContent).not.toContain('Op. gravada');
  });
});
