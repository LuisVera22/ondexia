import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { accionConEstado } from '../../../shared/components/comunes/boton/estado-accion';
import {
  DesplegableComponent,
  OpcionDesplegable,
} from '../../../shared/components/comunes/desplegable/desplegable.component';
import {
  ConfiguracionApiService,
  Establecimiento,
  mensajeDeError,
} from '../../../nucleo/configuracion.api.service';
import { AvisosService } from '../../../shared/services/avisos.service';
import { repartirFallo } from '../../../shared/formularios/fallo-de-formulario';
import { ErrorCampoComponent } from '../../../shared/components/comunes/error-campo/error-campo.component';
import { AccionesGuardadoComponent } from '../../../shared/components/comunes/acciones-guardado/acciones-guardado.component';
import { seguirCambios } from '../../../shared/formularios/cambios';
import { ConCambiosSinGuardar } from '../../../shared/formularios/salida-con-cambios.guard';

/**
 * Ficha de un almacén. Es donde se edita.
 *
 * <h2>El código no se edita</h2>
 *
 * <p>Identifica al almacén en los movimientos de mercadería ya registrados, y el
 * backend tampoco lo acepta al editar. Va como texto y no como campo apagado: un
 * campo apagado sugiere que en alguna circunstancia se podría.
 *
 * <p>Se carga del listado filtrando por id, como el resto de fichas de
 * configuración: no hay {@code GET /{id}} y para un puñado de almacenes no
 * merece un cambio de contrato.
 */
@Component({
  selector: 'app-ficha-almacen',
  imports: [
    EncabezadoPaginaComponent,
    ReactiveFormsModule,
    RouterModule,
    DesplegableComponent,
    ErrorCampoComponent,
    AccionesGuardadoComponent,
  ],
  templateUrl: './ficha-almacen.component.html',
})
export class FichaAlmacenComponent implements ConCambiosSinGuardar {
  private readonly api = inject(ConfiguracionApiService);
  private readonly avisos = inject(AvisosService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly constructorFormulario = inject(FormBuilder);

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly id = signal('');
  readonly codigo = signal('');
  readonly nombreCargado = signal('');
  readonly activo = signal(true);
  readonly establecimientos = signal<Establecimiento[]>([]);

  /** El que tenía asignado al cargar. Señal, para que el computed reaccione. */
  private readonly asignado = signal<string | null>(null);

  /**
   * Activos, más el asignado aunque esté desactivado.
   *
   * <p>Filtrarlo a secas escondería el valor vigente si el local se desactivó
   * después, y el desplegable se vería como si nadie hubiera elegido nada.
   */
  readonly opcionesEstablecimiento = computed<OpcionDesplegable[]>(() => {
    const actual = this.asignado();
    return [
      { valor: '', etiqueta: 'Sin asignar' },
      ...this.establecimientos()
        .filter((e) => e.activa || e.id === actual)
        .map((e) => ({
          valor: e.id,
          etiqueta: `${e.codigo} · ${e.nombre}`,
          detalle: e.activa ? undefined : 'Desactivado',
        })),
    ];
  });

  formulario = this.constructorFormulario.nonNullable.group({
    nombre: ['', [Validators.required]],
    sucursalId: [''],
  });

  /**
   * Se declara aqui y no dentro de {@code cargar} porque se suscribe a los
   * cambios del formulario, y eso necesita el contexto de inyeccion del campo
   * para darse de baja cuando la pantalla se destruye.
   */
  readonly cambios = seguirCambios(this.formulario);

  constructor() {
    const id = this.ruta.snapshot.paramMap.get('id');
    if (!id) {
      void this.router.navigate(['/almacen/almacenes']);
      return;
    }
    this.id.set(id);
    void this.cargar();
  }

  get controles() {
    return this.formulario.controls;
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const [almacenes, establecimientos] = await Promise.all([
        this.api.almacenes(),
        this.api.establecimientos(),
      ]);

      const almacen = almacenes.find((a) => a.id === this.id());
      if (!almacen) {
        this.error.set('Este almacén no está entre los de la empresa en la que trabajas.');
        return;
      }

      this.establecimientos.set(establecimientos);
      this.codigo.set(almacen.codigo);
      this.nombreCargado.set(almacen.nombre);
      this.activo.set(almacen.activo);
      this.asignado.set(almacen.sucursalId);
      this.formulario.patchValue({
        nombre: almacen.nombre,
        sucursalId: almacen.sucursalId ?? '',
      });
      // Lo que se acaba de cargar es el punto de partida: a partir de aqui,
      // cualquier diferencia es un cambio del usuario.
      this.cambios.fijarBase();
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudo cargar el almacén.'));
    } finally {
      this.cargando.set(false);
    }
  }

  readonly guardar = accionConEstado(async () => {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      throw new Error('Formulario incompleto');
    }

    const valores = this.formulario.getRawValue();

    try {
      const guardado = await this.api.actualizarAlmacen(this.id(), {
        nombre: valores.nombre,
        sucursalId: valores.sucursalId || null,
      });

      this.nombreCargado.set(guardado.nombre);
      this.formulario.patchValue({
        nombre: guardado.nombre,
        sucursalId: guardado.sucursalId ?? '',
      });
      this.cambios.fijarBase();

      this.avisos.exito(`${this.codigo()} · ${guardado.nombre}`, 'Almacén guardado');
    } catch (fallo: unknown) {
      repartirFallo(fallo, this.formulario, this.avisos, 'No se pudieron guardar los cambios.');
      throw fallo;
    }
  });

  hayCambiosSinGuardar(): boolean {
    return this.cambios.hayCambios();
  }
}
