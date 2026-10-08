import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { validarImporteCatalogo, presentarImporteCatalogo } from '../../../nucleo/importe-catalogo';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import {
  DesplegableComponent,
  OpcionDesplegable,
} from '../../../shared/components/comunes/desplegable/desplegable.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { accionConEstado } from '../../../shared/components/comunes/boton/estado-accion';
import { ModalComponent } from '../../../shared/components/comunes/modal/modal.component';
import { ErrorCampoComponent } from '../../../shared/components/comunes/error-campo/error-campo.component';
import { AccionesGuardadoComponent } from '../../../shared/components/comunes/acciones-guardado/acciones-guardado.component';
import { seguirCambios } from '../../../shared/formularios/cambios';
import { ConCambiosSinGuardar } from '../../../shared/formularios/salida-con-cambios.guard';
import { repartirFallo } from '../../../shared/formularios/fallo-de-formulario';
import {
  AlmacenApiService,
  CatalogosProducto,
  DisponibilidadApi,
  ExistenciaApi,
  MovimientoApi,
  ProductoApi,
  TipoProducto,
} from '../../../nucleo/almacen.api.service';
import { AlmacenApi, ConfiguracionApiService, Establecimiento } from '../../../nucleo/configuracion.api.service';
import { mensajeDeError } from '../../../nucleo/errores';
import { AvisosService } from '../../../shared/services/avisos.service';
import { ContextoService } from '../../../shared/services/contexto.service';

type Pestana = 'general' | 'locales' | 'existencias';

/** Una fila de la pestaña de locales: el establecimiento con su disponibilidad. */
export interface FilaLocal {
  readonly sucursalId: string;
  readonly codigo: string;
  readonly nombre: string;
  readonly disponible: boolean;
  readonly precio: string | null;
}

/**
 * Ficha de un producto: datos generales, en qué locales se vende y cuánto hay.
 *
 * <p>Las pestañas de presentaciones, precios por tipo y kardex valorizado de la
 * maqueta no existen en el primer producto (doc 12 §2.2) y se retiraron: una
 * pestaña que promete algo que no se guarda enseña al usuario a no confiar en
 * la ficha. Lo que sí hay es el libro de movimientos, sin costos: cuánto entró
 * y salió, y por qué.
 */
@Component({
  selector: 'app-ficha-producto',
  imports: [
    EncabezadoPaginaComponent,
    ReactiveFormsModule,
    RouterModule,
    DesplegableComponent,
    BotonComponent,
    ModalComponent,
    ErrorCampoComponent,
    AccionesGuardadoComponent,
  ],
  templateUrl: './ficha-producto.component.html',
})
export class FichaProductoComponent implements ConCambiosSinGuardar {
  private readonly api = inject(AlmacenApiService);
  private readonly configuracion = inject(ConfiguracionApiService);
  private readonly contexto = inject(ContextoService);
  private readonly avisos = inject(AvisosService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly constructorFormulario = inject(FormBuilder);

  readonly esNuevo = signal(false);
  readonly id = signal('');
  readonly pestana = signal<Pestana>('general');
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly producto = signal<ProductoApi | null>(null);
  readonly catalogos = signal<CatalogosProducto>({ unidades: [], afectaciones: [] });
  readonly establecimientos = signal<Establecimiento[]>([]);
  readonly almacenes = signal<AlmacenApi[]>([]);
  readonly disponibilidad = signal<DisponibilidadApi[]>([]);
  readonly existencias = signal<ExistenciaApi[]>([]);
  readonly movimientos = signal<MovimientoApi[]>([]);

  readonly puedeEditar = computed(() => this.contexto.puede('almacen.producto:editar'));
  readonly puedeAjustar = computed(() => this.contexto.puede('almacen.stock:ajustar'));

  readonly tipo = signal<TipoProducto>(this.ruta.snapshot.data?.['tipo'] ?? 'BIEN');
  readonly esServicio = computed(() => this.tipo() === 'SERVICIO');
  readonly rutaListado = computed(() => this.esServicio() ? '/almacen/servicios' : '/almacen/bienes');
  readonly pestanas = computed(() => [
    { clave: 'general', nombre: 'Datos generales' },
    { clave: 'locales', nombre: 'Disponibilidad por local' },
    ...(!this.esServicio() ? [{ clave: 'existencias', nombre: 'Existencias' }] : []),
  ]);
  readonly opcionesTipo: OpcionDesplegable[] = [
    { valor: 'BIEN', etiqueta: 'Bien' }, { valor: 'SERVICIO', etiqueta: 'Servicio' },
  ];

  readonly opcionesUnidad = computed<OpcionDesplegable[]>(() =>
    this.catalogos().unidades.filter(u => this.esServicio() ? ['ZZ', 'HUR', 'DAY'].includes(u.codigo) : u.codigo !== 'ZZ').map((u) => ({ valor: u.codigo, etiqueta: u.nombre, detalle: u.codigo }))
  );
  readonly opcionesAfectacion = computed<OpcionDesplegable[]>(() =>
    this.catalogos().afectaciones.map((a) => ({ valor: a.codigo, etiqueta: a.nombre }))
  );
  readonly opcionesAlmacen = computed<OpcionDesplegable[]>(() =>
    this.almacenes()
      .filter((a) => a.activo)
      .map((a) => ({ valor: a.id, etiqueta: `${a.codigo} · ${a.nombre}` }))
  );

  /** Cada establecimiento activo, con lo que el servidor sabe de él. Sin fila: no disponible. */
  readonly locales = computed<FilaLocal[]>(() => {
    const porSucursal = new Map(this.disponibilidad().map((d) => [d.sucursalId, d]));
    return this.establecimientos()
      .filter((e) => e.activa || porSucursal.has(e.id))
      .map((e) => {
        const fila = porSucursal.get(e.id);
        return {
          sucursalId: e.id,
          codigo: e.codigo,
          nombre: e.nombre,
          disponible: fila?.disponible ?? false,
          precio: fila?.precio ?? null,
        };
      });
  });

  readonly stockTotal = computed(() => this.existencias().reduce((suma, e) => suma + Number(e.cantidad), 0));

  formulario = this.constructorFormulario.nonNullable.group({
    codigo: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9-]{1,30}$/)]],
    nombre: ['', [Validators.required, Validators.maxLength(300)]],
    descripcion: [''],
    tipo: [this.tipo(), [Validators.required]],
    unidad: [this.esServicio() ? 'ZZ' : 'NIU', [Validators.required]],
    afectacion: ['GRAVADO', [Validators.required]],
    precioLista: ['0', [Validators.required, validarImporteCatalogo]],
  });

  readonly cambios = seguirCambios(this.formulario);

  // ── Modal de disponibilidad ────────────────────────────────────────────
  readonly modalLocal = signal(false);
  readonly localEnEdicion = signal<FilaLocal | null>(null);
  formularioLocal = this.constructorFormulario.group({
    disponible: [true],
    precio: [null as string | null, [validarImporteCatalogo]],
  });

  // ── Modal de ajuste ────────────────────────────────────────────────────
  readonly modalAjuste = signal(false);
  formularioAjuste = this.constructorFormulario.nonNullable.group({
    almacenId: ['', [Validators.required]],
    cantidad: [0, [Validators.required, Validators.min(0)]],
    motivo: [''],
  });

  constructor() {
    this.formulario.controls.tipo.valueChanges.pipe(takeUntilDestroyed()).subscribe(tipo => {
      this.tipo.set(tipo);
      const unidad = this.formulario.controls.unidad;
      if (!this.opcionesUnidad().some(opcion => opcion.valor === unidad.value)) {
        unidad.setValue(tipo === 'SERVICIO' ? 'ZZ' : 'NIU');
      }
      if (tipo === 'SERVICIO') {
        this.pestana.set('general'); this.modalAjuste.set(false);
      } else if (!this.esNuevo()) {
        void this.cargarAlmacenes().catch(fallo => this.error.set(mensajeDeError(fallo, 'No se pudieron cargar los almacenes.')));
      }
    });
    const id = this.ruta.snapshot.paramMap.get('id');
    if (!id) {
      void this.router.navigate([this.rutaListado()]);
      return;
    }
    this.esNuevo.set(id === 'nuevo');
    this.id.set(id);
    void this.cargar();
  }

  get controles() {
    return this.formulario.controls;
  }

  get titulo(): string {
    return this.esNuevo() ? (this.esServicio() ? 'Nuevo servicio' : 'Nuevo bien') : this.producto()?.nombre || 'Producto';
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const [catalogos, establecimientos] = await Promise.all([
        this.api.catalogos(),
        this.configuracion.establecimientos(),
      ]);
      this.catalogos.set(catalogos);
      this.establecimientos.set(establecimientos);

      if (this.esNuevo()) {
        if (!this.esServicio()) await this.cargarAlmacenes();
        this.formulario.controls.codigo.enable();
        this.cambios.fijarBase();
        return;
      }

      const producto = await this.api.producto(this.id());
      this.aplicar(producto);
      if (!this.esServicio()) await this.cargarAlmacenes();
      // El código no se cambia: identifica al producto en cada comprobante ya emitido.
      this.formulario.controls.codigo.disable();
      await this.cargarDetalle();
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudo cargar el producto.'));
    } finally {
      this.cargando.set(false);
    }
  }

  private aplicar(producto: ProductoApi): void {
    this.producto.set(producto);
    this.tipo.set(producto.tipo);
    this.formulario.patchValue({
      codigo: producto.codigo,
      nombre: producto.nombre,
      descripcion: producto.descripcion ?? '',
      unidad: producto.unidad,
      afectacion: producto.afectacion,
      precioLista: producto.precioLista,
      tipo: producto.tipo,
    }, { emitEvent: false });
    this.cambios.fijarBase();
  }

  private async cargarAlmacenes(): Promise<void> {
    this.almacenes.set(await this.configuracion.almacenes());
  }

  private async cargarDetalle(): Promise<void> {
    const [disponibilidad, existencias, movimientos] = await Promise.all([
      this.api.disponibilidad(this.id()),
      !this.esServicio() && this.contexto.puede('almacen.stock:consultar') ? this.api.existencias(this.id()) : Promise.resolve([]),
      !this.esServicio() && this.contexto.puede('almacen.stock:consultar') ? this.api.movimientos(this.id()) : Promise.resolve([]),
    ]);
    this.disponibilidad.set(disponibilidad);
    this.existencias.set(existencias);
    this.movimientos.set(movimientos);
  }

  readonly guardar = accionConEstado(async () => {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      throw new Error('Formulario incompleto');
    }
    const valores = this.formulario.getRawValue();
    const datos = {
      nombre: valores.nombre,
      descripcion: valores.descripcion || null,
      unidad: valores.unidad,
      afectacion: valores.afectacion,
      precioLista: valores.precioLista,
      tipo: valores.tipo,
    };
    try {
      if (this.esNuevo()) {
        const activo = this.contexto.establecimientoActivo();
        if (!activo) {
          throw new Error('Falta elegir un establecimiento en la barra superior antes de crear el producto.');
        }
        const creado = await this.api.crearProducto({
          ...datos,
          codigo: valores.codigo,
          sucursalId: String(activo.id),
        });
        this.avisos.exito(
          `${creado.codigo} · ${creado.nombre}. Se ofrece en ${activo.nombre}; los demás locales se activan en la ficha.`,
          'Producto registrado'
        );
        this.cambios.fijarBase();
        await this.router.navigate([creado.tipo === 'SERVICIO' ? '/almacen/servicios' : '/almacen/bienes', creado.id]);
        return;
      }
      const guardado = await this.api.actualizarProducto(this.id(), datos);
      this.aplicar(guardado);
      await this.cargarDetalle();
      this.cambios.fijarBase();
      await this.router.navigate([this.rutaListado(), guardado.id]);
      this.avisos.exito(`${guardado.codigo} · ${guardado.nombre}`, 'Producto guardado');
    } catch (fallo: unknown) {
      repartirFallo(fallo, this.formulario, this.avisos, 'No se pudieron guardar los cambios.');
      throw fallo;
    }
  });

  async cambiarEstado(activo: boolean): Promise<void> {
    try {
      this.aplicar(await this.api.cambiarEstadoProducto(this.id(), activo));
      this.avisos.exito(activo ? 'El producto vuelve a estar activo.' : 'El producto quedó inactivo.');
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo cambiar el estado.'));
    }
  }

  // ── Disponibilidad ─────────────────────────────────────────────────────

  editarLocal(fila: FilaLocal): void {
    this.localEnEdicion.set(fila);
    this.formularioLocal.reset({ disponible: fila.disponible, precio: fila.precio });
    this.modalLocal.set(true);
  }

  readonly guardarLocal = accionConEstado(async () => {
    const fila = this.localEnEdicion();
    if (!fila) {
      return;
    }
    const valores = this.formularioLocal.getRawValue();
    if (this.formularioLocal.invalid) { this.formularioLocal.markAllAsTouched(); throw new Error('Precio inválido'); }
    const precio = valores.precio === null || valores.precio === '' ? null : valores.precio;
    try {
      await this.api.fijarDisponibilidad(this.id(), fila.sucursalId, {
        disponible: valores.disponible ?? false,
        precio,
      });
      this.modalLocal.set(false);
      this.disponibilidad.set(await this.api.disponibilidad(this.id()));
      this.avisos.exito(`${fila.codigo} · ${fila.nombre}`, 'Disponibilidad guardada');
    } catch (fallo: unknown) {
      repartirFallo(fallo, this.formularioLocal, this.avisos, 'No se pudo guardar la disponibilidad.');
      throw fallo;
    }
  });

  // ── Existencias ────────────────────────────────────────────────────────

  abrirAjuste(almacenId?: string): void {
    const almacen = almacenId ?? this.opcionesAlmacen()[0]?.valor ?? '';
    const actual = this.existencias().find((e) => e.almacenId === almacen);
    this.formularioAjuste.reset({ almacenId: String(almacen), cantidad: Number(actual?.cantidad ?? 0), motivo: '' });
    this.modalAjuste.set(true);
  }

  readonly ajustar = accionConEstado(async () => {
    if (this.formularioAjuste.invalid) {
      this.formularioAjuste.markAllAsTouched();
      throw new Error('Formulario incompleto');
    }
    const valores = this.formularioAjuste.getRawValue();
    try {
      const resultado = await this.api.ajustarExistencias(this.id(), {
        almacenId: valores.almacenId,
        cantidad: Number(valores.cantidad),
        motivo: valores.motivo || null,
      });
      this.modalAjuste.set(false);
      await this.cargarDetalle();
      this.avisos.exito(
        `${this.nombreDeAlmacen(resultado.almacenId)}: ${this.cantidad(resultado.cantidad)}`,
        'Existencias ajustadas'
      );
    } catch (fallo: unknown) {
      repartirFallo(fallo, this.formularioAjuste, this.avisos, 'No se pudo ajustar.');
      throw fallo;
    }
  });

  nombreDeAlmacen(almacenId: string): string {
    const almacen = this.almacenes().find((a) => a.id === almacenId);
    return almacen ? `${almacen.codigo} · ${almacen.nombre}` : 'Almacén';
  }

  cantidad(valor: number): string {
    return Number(valor).toLocaleString('es-PE', { maximumFractionDigits: 6 });
  }

  importe(valor: string | null): string {
    return presentarImporteCatalogo(valor);
  }

  fecha(instante: string): string {
    return new Date(instante).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' });
  }

  tipoDeMovimiento(tipo: MovimientoApi['tipo']): string {
    switch (tipo) {
      case 'AJUSTE':
        return 'Ajuste por conteo';
      case 'INGRESO':
        return 'Ingreso';
      case 'VENTA':
        return 'Venta';
      case 'DEVOLUCION':
        return 'Devolución';
      case 'TRASLADO':
        return 'Traslado';
    }
  }

  hayCambiosSinGuardar(): boolean {
    return this.cambios.hayCambios();
  }
}
