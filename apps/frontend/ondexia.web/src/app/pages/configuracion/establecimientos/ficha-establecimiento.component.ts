import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { accionConEstado } from '../../../shared/components/comunes/boton/estado-accion';
import { ConfiguracionApiService, mensajeDeError } from '../../../nucleo/configuracion.api.service';
import { AvisosService } from '../../../shared/services/avisos.service';
import { repartirFallo } from '../../../shared/formularios/fallo-de-formulario';
import { ErrorCampoComponent } from '../../../shared/components/comunes/error-campo/error-campo.component';

/**
 * Ficha de un establecimiento. Es donde se edita.
 *
 * <p>Existe porque editar un registro que ya existe merece URL propia: se
 * comparte por enlace, se recarga y se abre en otra pestaña. Crear no la
 * necesita —no hay nada que enlazar todavía— y por eso el alta va en un modal
 * sobre el listado.
 *
 * <h2>El código SUNAT no se edita</h2>
 *
 * <p>Identifica al anexo ante SUNAT y ya está impreso en las series que cuelgan
 * de él. El backend tampoco lo acepta al editar, así que se muestra como texto y
 * no como campo deshabilitado: un campo apagado sugiere que en alguna
 * circunstancia se podría.
 *
 * <h2>Se carga del listado, no de un endpoint propio</h2>
 *
 * <p>No hay {@code GET /establecimientos/&#123;id&#125;}. Se pide el listado y
 * se busca el id, que para un puñado de locales no cuesta nada. Si algún día una
 * empresa tiene cientos, esto pide un endpoint propio — pero añadirlo hoy sería
 * un cambio de contrato para ahorrar unos kilobytes.
 */
@Component({
  selector: 'app-ficha-establecimiento',
  imports: [
    EncabezadoPaginaComponent,
    ReactiveFormsModule,
    RouterModule,
    BotonComponent,
    ErrorCampoComponent,
  ],
  templateUrl: './ficha-establecimiento.component.html',
})
export class FichaEstablecimientoComponent {
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

  formulario = this.constructorFormulario.nonNullable.group({
    nombre: ['', [Validators.required]],
    direccion: ['', [Validators.required]],
    ubigeo: ['', [Validators.pattern(/^$|^\d{6}$/)]],
  });

  constructor() {
    const id = this.ruta.snapshot.paramMap.get('id');
    if (!id) {
      void this.router.navigate(['/configuracion/establecimientos']);
      return;
    }
    this.id.set(id);
    void this.cargar();
  }

  get controles() {
    return this.formulario.controls;
  }

  get esMatriz(): boolean {
    return this.codigo() === '0000';
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const todos = await this.api.establecimientos();
      const establecimiento = todos.find((e) => e.id === this.id());

      if (!establecimiento) {
        // Un id que no está en la lista del usuario. Se dice así y no «no
        // existe»: puede existir en otra empresa, y afirmarlo sería contar algo
        // que esta pantalla no tiene por qué saber.
        this.error.set('Este establecimiento no está entre los de la empresa en la que trabajas.');
        return;
      }

      this.codigo.set(establecimiento.codigo);
      this.nombreCargado.set(establecimiento.nombre);
      this.activo.set(establecimiento.activa);
      this.formulario.patchValue({
        nombre: establecimiento.nombre,
        direccion: establecimiento.direccion,
        ubigeo: establecimiento.ubigeo ?? '',
      });
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudo cargar el establecimiento.'));
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
      const guardado = await this.api.actualizarEstablecimiento(this.id(), {
        nombre: valores.nombre,
        direccion: valores.direccion,
        ubigeo: valores.ubigeo || null,
      });

      // Se repuebla con lo que devolvió el servidor: recorta espacios y
      // normaliza, y mostrar la versión sin normalizar haría creer que se guardó
      // otra cosa.
      this.nombreCargado.set(guardado.nombre);
      this.formulario.patchValue({
        nombre: guardado.nombre,
        direccion: guardado.direccion,
        ubigeo: guardado.ubigeo ?? '',
      });
      this.formulario.markAsPristine();

      this.avisos.exito(`${this.codigo()} · ${guardado.nombre}`, 'Establecimiento guardado');
    } catch (fallo: unknown) {
      repartirFallo(fallo, this.formulario, this.avisos, 'No se pudieron guardar los cambios.');
      throw fallo;
    }
  });
}
