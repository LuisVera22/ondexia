import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';
import {
  ConfiguracionApiService,
  Establecimiento,
  mensajeDeError,
} from '../../../nucleo/configuracion.api.service';

/**
 * Establecimientos anexos de la empresa.
 *
 * <p>No se limitan por plan (documento 04 §2): cobrar por local empujaría al
 * cliente a registrar uno solo y declarar mal el código de establecimiento en
 * comprobantes y guías de remisión.
 *
 * <h2>Dos cosas cambiaron al conectarla</h2>
 *
 * <p>La columna «Distrito» desapareció: lo que se guarda es el <em>ubigeo</em>,
 * que es el código de seis dígitos que usa SUNAT, y traducirlo a un nombre de
 * distrito necesita un catálogo que todavía no existe. Mostrar un nombre
 * inventado en una pantalla tributaria sería peor que mostrar el código.
 *
 * <p>Y «Eliminar» pasó a ser «Desactivar». El backend nunca borra: un
 * establecimiento aparece en los comprobantes ya emitidos, y borrarlo dejaría
 * documentos apuntando a nada.
 */
@Component({
  selector: 'app-establecimientos',
  imports: [
    EncabezadoPaginaComponent,
    TablaDatosComponent,
    ConfirmacionComponent,
    ReactiveFormsModule,
  ],
  templateUrl: './establecimientos.component.html',
})
export class EstablecimientosComponent {
  private readonly api = inject(ConfiguracionApiService);
  private readonly constructorFormulario = inject(FormBuilder);

  readonly columnas: ColumnaTabla[] = [
    { campo: 'codigo', titulo: 'Código SUNAT', ordenable: true, ancho: 'w-32' },
    { campo: 'nombre', titulo: 'Establecimiento', ordenable: true },
    { campo: 'direccion', titulo: 'Dirección' },
    { campo: 'ubigeo', titulo: 'Ubigeo', ancho: 'w-28' },
    { campo: 'estado', titulo: 'Estado', ancho: 'w-28' },
  ];

  readonly registros = signal<Record<string, unknown>[]>([]);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);

  readonly formularioAbierto = signal(false);
  readonly enEdicion = signal<Establecimiento | null>(null);

  readonly confirmacionAbierta = signal(false);
  private aDesactivar: Establecimiento | null = null;

  formulario = this.constructorFormulario.nonNullable.group({
    codigo: ['', [Validators.required, Validators.pattern(/^\d{4}$/)]],
    nombre: ['', [Validators.required]],
    direccion: ['', [Validators.required]],
    ubigeo: ['', [Validators.pattern(/^$|^\d{6}$/)]],
  });

  private originales: Establecimiento[] = [];

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
      this.originales = await this.api.establecimientos();
      this.registros.set(
        this.originales.map((e) => ({
          id: e.id,
          codigo: e.codigo,
          nombre: e.nombre,
          direccion: e.direccion,
          ubigeo: e.ubigeo ?? '—',
          estado: e.activa ? 'Activo' : 'Inactivo',
          // La casa matriz no se desactiva: sin ella la empresa no puede
          // emitir, y SUNAT la exige siempre.
          esMatriz: e.codigo === '0000',
          activa: e.activa,
        }))
      );
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar los establecimientos.'));
    } finally {
      this.cargando.set(false);
    }
  }

  abrirNuevo(): void {
    this.enEdicion.set(null);
    this.formulario.reset({ codigo: '', nombre: '', direccion: '', ubigeo: '' });
    this.controles.codigo.enable();
    this.formularioAbierto.set(true);
  }

  abrirEdicion(fila: Record<string, unknown>): void {
    const establecimiento = this.originales.find((e) => e.id === fila['id']);
    if (!establecimiento) {
      return;
    }

    this.enEdicion.set(establecimiento);
    this.formulario.reset({
      codigo: establecimiento.codigo,
      nombre: establecimiento.nombre,
      direccion: establecimiento.direccion,
      ubigeo: establecimiento.ubigeo ?? '',
    });
    // El código identifica al anexo ante SUNAT y ya está impreso en las series
    // que cuelgan de él. El backend tampoco lo acepta al editar.
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
    const datos = {
      nombre: valores.nombre,
      direccion: valores.direccion,
      ubigeo: valores.ubigeo || null,
    };

    try {
      const editando = this.enEdicion();
      if (editando) {
        await this.api.actualizarEstablecimiento(editando.id, datos);
      } else {
        await this.api.crearEstablecimiento({ ...datos, codigo: valores.codigo });
      }
      this.cerrarFormulario();
      await this.cargar();
    } catch (fallo: unknown) {
      // El 409 de código duplicado llega aquí con su mensaje del servidor, que
      // dice qué código choca. Es mejor que cualquier texto genérico de aquí.
      this.error.set(mensajeDeError(fallo, 'No se pudo guardar el establecimiento.'));
    } finally {
      this.guardando.set(false);
    }
  }

  pedirDesactivacion(fila: Record<string, unknown>): void {
    const establecimiento = this.originales.find((e) => e.id === fila['id']);
    if (!establecimiento) {
      return;
    }
    this.aDesactivar = establecimiento;
    this.confirmacionAbierta.set(true);
  }

  async desactivar(): Promise<void> {
    if (!this.aDesactivar) {
      return;
    }
    this.confirmacionAbierta.set(false);
    this.error.set(null);

    try {
      await this.api.desactivarEstablecimiento(this.aDesactivar.id);
      await this.cargar();
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudo desactivar el establecimiento.'));
    } finally {
      this.aDesactivar = null;
    }
  }
}
