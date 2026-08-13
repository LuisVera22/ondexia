import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';
import {
  AlmacenApi,
  ConfiguracionApiService,
  Establecimiento,
  mensajeDeError,
} from '../../../nucleo/configuracion.api.service';

/**
 * Almacenes: dónde están físicamente las existencias.
 *
 * <h2>Tres cosas que la maqueta prometía y no existen</h2>
 *
 * <p>La columna <em>Responsable</em> se retiró: no hay tal campo en el esquema,
 * y asignar responsables exige el módulo de usuarios (Entrega 4). Lo mismo
 * <em>Productos</em>, que necesita el inventario.
 *
 * <p>Y el texto decía que «cada establecimiento tiene su almacén principal
 * creado automáticamente». No ocurre: el alta crea la casa matriz como
 * establecimiento, no como almacén. Prometerlo aquí haría que un cliente diera
 * por hecho que puede vender sin crear ninguno.
 */
@Component({
  selector: 'app-almacenes',
  imports: [
    EncabezadoPaginaComponent,
    TablaDatosComponent,
    ConfirmacionComponent,
    ReactiveFormsModule,
  ],
  templateUrl: './almacenes.component.html',
})
export class AlmacenesComponent {
  private readonly api = inject(ConfiguracionApiService);
  private readonly constructorFormulario = inject(FormBuilder);

  readonly columnas: ColumnaTabla[] = [
    { campo: 'codigo', titulo: 'Código', ordenable: true, ancho: 'w-32' },
    { campo: 'nombre', titulo: 'Almacén', ordenable: true },
    { campo: 'establecimiento', titulo: 'Establecimiento' },
    { campo: 'estado', titulo: 'Estado', ancho: 'w-28' },
  ];

  readonly registros = signal<Record<string, unknown>[]>([]);
  readonly establecimientos = signal<Establecimiento[]>([]);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);

  readonly formularioAbierto = signal(false);
  readonly enEdicion = signal<AlmacenApi | null>(null);
  readonly confirmacionAbierta = signal(false);

  private aDesactivar: AlmacenApi | null = null;
  private originales: AlmacenApi[] = [];

  formulario = this.constructorFormulario.nonNullable.group({
    codigo: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9-]{1,20}$/)]],
    nombre: ['', [Validators.required]],
    sucursalId: [''],
  });

  constructor() {
    void this.cargar();
  }

  get controles() {
    return this.formulario.controls;
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      // En paralelo: la lista de establecimientos alimenta el desplegable y
      // traduce el identificador a un nombre en la tabla. Encadenarlas dobla la
      // espera sin ganar nada.
      const [almacenes, establecimientos] = await Promise.all([
        this.api.almacenes(),
        this.api.establecimientos(),
      ]);

      this.originales = almacenes;
      this.establecimientos.set(establecimientos);

      const porId = new Map(establecimientos.map((e) => [e.id, e]));

      this.registros.set(
        almacenes.map((a) => ({
          id: a.id,
          codigo: a.codigo,
          nombre: a.nombre,
          // Un almacén sin establecimiento es legítimo —mercadería en
          // tránsito— así que se dice, no se deja en blanco.
          establecimiento: a.sucursalId
            ? (porId.get(a.sucursalId)?.nombre ?? 'Establecimiento desconocido')
            : 'Sin asignar',
          estado: a.activo ? 'Activo' : 'Inactivo',
          activo: a.activo,
        }))
      );
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar los almacenes.'));
    } finally {
      this.cargando.set(false);
    }
  }

  abrirNuevo(): void {
    this.enEdicion.set(null);
    this.formulario.reset({ codigo: '', nombre: '', sucursalId: '' });
    this.controles.codigo.enable();
    this.formularioAbierto.set(true);
  }

  abrirEdicion(fila: Record<string, unknown>): void {
    const almacen = this.originales.find((a) => a.id === fila['id']);
    if (!almacen) {
      return;
    }

    this.enEdicion.set(almacen);
    this.formulario.reset({
      codigo: almacen.codigo,
      nombre: almacen.nombre,
      sucursalId: almacen.sucursalId ?? '',
    });
    // El código identifica al almacén en los movimientos de stock ya
    // registrados. El backend tampoco lo acepta al editar.
    this.controles.codigo.disable();
    this.formularioAbierto.set(true);
  }

  cerrarFormulario(): void {
    this.formularioAbierto.set(false);
    this.enEdicion.set(null);
  }

  async guardar(): Promise<void> {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.guardando.set(true);
    this.error.set(null);

    const valores = this.formulario.getRawValue();
    const datos = { nombre: valores.nombre, sucursalId: valores.sucursalId || null };

    try {
      const editando = this.enEdicion();
      if (editando) {
        await this.api.actualizarAlmacen(editando.id, datos);
      } else {
        await this.api.crearAlmacen({ ...datos, codigo: valores.codigo });
      }
      this.cerrarFormulario();
      await this.cargar();
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudo guardar el almacén.'));
    } finally {
      this.guardando.set(false);
    }
  }

  pedirDesactivacion(fila: Record<string, unknown>): void {
    const almacen = this.originales.find((a) => a.id === fila['id']);
    if (!almacen) {
      return;
    }
    this.aDesactivar = almacen;
    this.confirmacionAbierta.set(true);
  }

  async desactivar(): Promise<void> {
    if (!this.aDesactivar) {
      return;
    }
    this.confirmacionAbierta.set(false);
    this.error.set(null);

    try {
      await this.api.desactivarAlmacen(this.aDesactivar.id);
      await this.cargar();
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudo desactivar el almacén.'));
    } finally {
      this.aDesactivar = null;
    }
  }
}
