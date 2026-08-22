import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { accionConEstado } from '../../../shared/components/comunes/boton/estado-accion';
import {
  DesplegableComponent,
  OpcionDesplegable,
} from '../../../shared/components/comunes/desplegable/desplegable.component';
import {
  ConfiguracionApiService,
  Establecimiento,
  RolAsignable,
  mensajeDeError,
} from '../../../nucleo/configuracion.api.service';
import { AvisosService } from '../../../shared/services/avisos.service';
import { repartirFallo } from '../../../shared/formularios/fallo-de-formulario';
import { ErrorCampoComponent } from '../../../shared/components/comunes/error-campo/error-campo.component';

/**
 * Ficha del acceso de una persona a esta empresa.
 *
 * <h2>Esto edita la asignación, no a la persona</h2>
 *
 * <p>El nombre y el correo son de la persona y valen en todas las empresas de la
 * cuenta; cambiarlos aquí los cambiaría en todas. Así que aparecen como texto y
 * lo editable es lo que pertenece a este acceso: el <strong>rol</strong> y el
 * <strong>alcance</strong>.
 *
 * <p>Por eso el título de la pantalla es el nombre de la persona pero lo que se
 * guarda es una reasignación. Es la distinción que la pantalla anterior resolvía
 * deshabilitando campos en un formulario compartido, y que aquí se ve sin
 * explicaciones: lo que no se puede cambiar no es un campo apagado, es texto.
 */
@Component({
  selector: 'app-ficha-usuario',
  imports: [
    EncabezadoPaginaComponent,
    ReactiveFormsModule,
    RouterModule,
    BotonComponent,
    DesplegableComponent,
    ErrorCampoComponent,
  ],
  templateUrl: './ficha-usuario.component.html',
})
export class FichaUsuarioComponent {
  private readonly api = inject(ConfiguracionApiService);
  private readonly avisos = inject(AvisosService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly constructorFormulario = inject(FormBuilder);

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly asignacionId = signal('');
  readonly nombre = signal('');
  readonly email = signal('');
  readonly activo = signal(true);
  readonly invitado = signal(false);

  readonly roles = signal<RolAsignable[]>([]);
  readonly establecimientos = signal<Establecimiento[]>([]);

  /**
   * El establecimiento que tenía asignado al cargar.
   *
   * <p>Señal aparte y no una lectura del formulario: un {@code computed} solo
   * reacciona a señales, y el valor de un control reactivo no lo es. Leyéndolo
   * de ahí, la lista de opciones no se recalcularía nunca por ese motivo — y
   * parecería funcionar, que es lo peor de ese error.
   */
  private readonly sucursalAsignada = signal<string | null>(null);

  readonly opcionesRol = computed<OpcionDesplegable[]>(() => [
    { valor: '', etiqueta: 'Elige un rol…' },
    ...this.roles().map((rol) => ({ valor: rol.id, etiqueta: rol.nombre })),
  ]);

  /**
   * El alcance, con los activos más el asignado si estuviera desactivado.
   *
   * <p>Filtrar a secas escondería el valor vigente cuando el local se desactivó
   * después de la asignación, y el desplegable se vería vacío como si nadie
   * hubiera elegido nada. Se conserva, marcado, para que se vea que hay que
   * cambiarlo — pero no se ofrece ninguno inactivo más.
   */
  readonly opcionesAlcance = computed<OpcionDesplegable[]>(() => {
    const asignado = this.sucursalAsignada();
    return [
      { valor: '', etiqueta: 'Todos los establecimientos' },
      ...this.establecimientos()
        .filter((e) => e.activa || e.id === asignado)
        .map((e) => ({
          valor: e.id,
          etiqueta: `${e.codigo} · ${e.nombre}`,
          detalle: e.activa ? undefined : 'Desactivado: ya no se puede emitir desde aquí',
        })),
    ];
  });

  formulario = this.constructorFormulario.nonNullable.group({
    rolId: ['', [Validators.required]],
    sucursalId: [''],
  });

  constructor() {
    const id = this.ruta.snapshot.paramMap.get('id');
    if (!id) {
      void this.router.navigate(['/configuracion/usuarios']);
      return;
    }
    this.asignacionId.set(id);
    void this.cargar();
  }

  get controles() {
    return this.formulario.controls;
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const [usuarios, roles, establecimientos] = await Promise.all([
        this.api.usuarios(),
        this.api.rolesAsignables(),
        this.api.establecimientos(),
      ]);

      const usuario = usuarios.find((u) => u.asignacionId === this.asignacionId());
      if (!usuario) {
        this.error.set('Este acceso no existe en la empresa en la que trabajas.');
        return;
      }

      this.roles.set(roles);
      this.establecimientos.set(establecimientos);
      this.nombre.set(usuario.nombre);
      this.email.set(usuario.email);
      this.activo.set(usuario.activo);
      this.invitado.set(usuario.invitado);

      this.sucursalAsignada.set(usuario.sucursalId);
      this.formulario.patchValue({
        rolId: usuario.rolId,
        sucursalId: usuario.sucursalId ?? '',
      });
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudo cargar el acceso.'));
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
      await this.api.reasignarUsuario(this.asignacionId(), {
        rolId: valores.rolId,
        sucursalId: valores.sucursalId || null,
      });

      this.formulario.markAsPristine();
      // Se vuelve a cargar para reflejar el nombre del rol y del establecimiento
      // tal como los devuelve el servidor, no como los tenía la pantalla.
      await this.cargar();

      this.avisos.exito(`${this.nombre()} conserva su acceso con el rol elegido`, 'Acceso actualizado');
    } catch (fallo: unknown) {
      // Aquí llegan las reglas que la pantalla no puede anticipar, como quitarle
      // el rol al último administrador de la cuenta.
      repartirFallo(fallo, this.formulario, this.avisos, 'No se pudo actualizar el acceso.');
      throw fallo;
    }
  });
}
