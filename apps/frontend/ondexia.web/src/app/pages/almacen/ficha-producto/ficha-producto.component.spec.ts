import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { FichaProductoComponent } from './ficha-producto.component';
import { AlmacenApiService } from '../../../nucleo/almacen.api.service';
import { ConfiguracionApiService } from '../../../nucleo/configuracion.api.service';
import { ContextoService } from '../../../shared/services/contexto.service';
import { AvisosService } from '../../../shared/services/avisos.service';
import { ConfirmacionesService } from '../../../shared/services/confirmaciones.service';

describe('Formularios separados de bienes y servicios', () => {
  let api: jasmine.SpyObj<AlmacenApiService>;
  let configuracion: jasmine.SpyObj<ConfiguracionApiService>;
  let ruta: { snapshot: { paramMap: Map<string, string>; data: { tipo: string } } };
  beforeEach(() => {
    api = jasmine.createSpyObj('AlmacenApiService', ['catalogos', 'producto', 'disponibilidad', 'existencias', 'movimientos', 'crearProducto']);
    api.catalogos.and.resolveTo({ unidades: [
      { codigo: 'ZZ', nombre: 'Servicio' }, { codigo: 'HUR', nombre: 'Hora' },
      { codigo: 'DAY', nombre: 'Día' }, { codigo: 'BG', nombre: 'Bolsa' }, { codigo: 'NIU', nombre: 'Unidad' },
    ], afectaciones: [] });
    api.disponibilidad.and.resolveTo([]); api.existencias.and.resolveTo([]); api.movimientos.and.resolveTo([]);
    configuracion = jasmine.createSpyObj('ConfiguracionApiService', ['establecimientos', 'almacenes']);
    configuracion.establecimientos.and.resolveTo([]); configuracion.almacenes.and.resolveTo([]);
    ruta = { snapshot: { paramMap: new Map([['id', 'nuevo']]), data: { tipo: 'SERVICIO' } } };
    TestBed.configureTestingModule({ imports: [FichaProductoComponent], providers: [
      provideRouter([]), { provide: ActivatedRoute, useValue: ruta },
      { provide: AlmacenApiService, useValue: api }, { provide: ConfiguracionApiService, useValue: configuracion },
      { provide: ContextoService, useValue: { puede: () => true, establecimientoActivo: () => ({ id: 'matriz', nombre: 'Matriz' }) } },
      { provide: AvisosService, useValue: jasmine.createSpyObj('AvisosService', ['exito', 'error']) },
      { provide: ConfirmacionesService, useValue: { pedir: () => Promise.resolve(true) } },
    ] });
  });
  async function crear() {
    const ficha = TestBed.createComponent(FichaProductoComponent);
    await ficha.whenStable(); ficha.detectChanges(); return ficha;
  }
  it('preselecciona ZZ y ofrece solamente ZZ, HUR y DAY', async () => {
    const ficha = await crear();
    expect(ficha.componentInstance.controles.unidad.value).toBe('ZZ');
    expect(ficha.componentInstance.opcionesUnidad().map(u => u.valor)).toEqual(['ZZ', 'HUR', 'DAY']);
  });
  it('el servicio guardado no expone existencias ni consulta almacenes', async () => {
    ruta.snapshot.paramMap.set('id', 'servicio');
    api.producto.and.resolveTo({ id: 'servicio', codigo: 'S-1', nombre: 'Consultoría', descripcion: null,
      unidad: 'HUR', unidadNombre: 'Hora', afectacion: 'GRAVADO', afectacionNombre: 'Gravado',
      llevaIgv: true, precioLista: '20.000000', controlaStock: false, activo: true, tipo: 'SERVICIO' } as never);
    const ficha = await crear();
    expect(ficha.nativeElement.textContent).not.toContain('Existencias');
    expect(ficha.nativeElement.querySelector('[formControlName="controlaStock"]')).toBeNull();
    expect(ficha.nativeElement.querySelector('[formControlName="almacenId"]')).toBeNull();
    expect(configuracion.almacenes).not.toHaveBeenCalled();
    expect(api.existencias).not.toHaveBeenCalled(); expect(api.movimientos).not.toHaveBeenCalled();
  });
  it('admite horas sin reemplazarlas por ZZ y cambia la unidad incompatible al convertir', async () => {
    const ficha = await crear();
    ficha.componentInstance.controles.unidad.setValue('HUR');
    expect(ficha.componentInstance.controles.unidad.value).toBe('HUR');
    ficha.componentInstance.formulario.get('tipo')!.setValue('BIEN');
    expect(ficha.componentInstance.controles.unidad.value).toBe('HUR');
    ficha.componentInstance.controles.unidad.setValue('BG');
    ficha.componentInstance.formulario.get('tipo')!.setValue('SERVICIO');
    expect(ficha.componentInstance.controles.unidad.value).toBe('ZZ');
  });
  it('envía tipo y el importe exacto sin un control de stock editable', async () => {
    const ficha = await crear();
    const router = TestBed.inject(Router); spyOn(router, 'navigate').and.resolveTo(true);
    api.crearProducto.and.resolveTo({ id: 'servicio', codigo: 'S-1', nombre: 'Consultoría', tipo: 'SERVICIO' } as never);
    ficha.componentInstance.formulario.patchValue({ codigo: 'S-1', nombre: 'Consultoría', unidad: 'HUR', precioLista: '999999999999.123456' });
    await ficha.componentInstance.guardar.ejecutar();
    const datos = api.crearProducto.calls.mostRecent().args[0];
    expect(datos.tipo).toBe('SERVICIO'); expect(datos.unidad).toBe('HUR');
    expect(datos.precioLista).toBe('999999999999.123456');
    expect('controlaStock' in datos).toBeFalse();
  });
  it('el bien preselecciona NIU y excluye ZZ', async () => {
    ruta.snapshot.data.tipo = 'BIEN';
    const ficha = await crear();
    expect(ficha.componentInstance.controles.unidad.value).toBe('NIU');
    expect(ficha.componentInstance.opcionesUnidad().map(u => u.valor)).not.toContain('ZZ');
  });
});
