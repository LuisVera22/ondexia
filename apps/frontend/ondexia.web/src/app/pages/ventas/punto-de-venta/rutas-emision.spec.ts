import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { signal } from '@angular/core';
import { routes } from '../../../app.routes';
import { PuntoDeVentaComponent } from './punto-de-venta.component';
import { AlmacenApiService } from '../../../nucleo/almacen.api.service';
import { VentasApiService } from '../../../nucleo/ventas.api.service';
import { CajaActivaService } from '../../../nucleo/caja-activa.service';
import { ConfiguracionApiService } from '../../../nucleo/configuracion.api.service';
import { ContextoService } from '../../../shared/services/contexto.service';
import { AvisosService } from '../../../shared/services/avisos.service';

describe('Rutas reales de nueva emisión', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [
    provideRouter(routes.flatMap((ruta) => ruta.children ?? []).filter((ruta) =>
      ['ventas/notas-venta/nueva', 'ventas/boletas/nueva', 'ventas/facturas/nueva', 'ventas/punto-de-venta'].includes(ruta.path ?? ''))),
    { provide: VentasApiService, useValue: { seriesDisponibles: async () => [] } },
    { provide: AlmacenApiService, useValue: {} },
    { provide: CajaActivaService, useValue: { enUso: signal({ id: 'caja-1', sucursalId: 'suc-1', nombre: 'Caja' }) } },
    { provide: ConfiguracionApiService, useValue: { empresa: async () => ({ emiteFacturas: true }) } },
    { provide: ContextoService, useValue: { puede: () => true, empresaActiva: signal({ id: 'empresa-1' }), establecimientoActivo: signal({ id: 'suc-1' }) } },
    { provide: AvisosService, useValue: { error: () => undefined } },
  ] }));

  it('entrada directa y navegación entre módulos fijan el tipo sin selector y no cambian el general', async () => {
    const navegador = await RouterTestingHarness.create();
    for (const [ruta, tipo] of [['notas-venta', 'NV'], ['boletas', 'BOLETA'], ['facturas', 'FACTURA']]) {
      const c = await navegador.navigateByUrl(`/ventas/${ruta}/nueva`, PuntoDeVentaComponent);
      navegador.detectChanges();
      expect(c.tipo()).toBe(tipo);
      expect(navegador.routeNativeElement?.querySelector('app-desplegable[idcampo="tipo"]')).toBeNull();
      expect(navegador.routeNativeElement?.querySelector('#tipo')?.textContent).toContain(c.nombreTipo());
    }
    const general = await navegador.navigateByUrl('/ventas/punto-de-venta', PuntoDeVentaComponent);
    navegador.detectChanges();
    expect(general.tipoFijo()).toBeNull();
    expect(navegador.routeNativeElement?.querySelector('app-desplegable[idcampo="tipo"]')).not.toBeNull();
  });
});
