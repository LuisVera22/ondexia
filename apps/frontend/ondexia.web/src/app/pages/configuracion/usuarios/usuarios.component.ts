import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';
import {
  ConfiguracionApiService,
  Establecimiento,
  RolAsignable,
  UsuarioApi,
  mensajeDeError,
} from '../../../nucleo/configuracion.api.service';

/** Qué está a punto de confirmarse. Null = no hay nada pendiente. */
type AccionPendiente = 'desactivar' | 'retirar' | null;

/**
 * Quién entra en la empresa, con qué rol y hasta dónde.
 *
 * <h2>El alta no crea la contraseña, y la pantalla lo dice</h2>
 *
 * <p>Aquí se da de alta a la persona y se le asigna rol y alcance; su cuenta de
 * acceso la crea ella misma registrándose, y se vincula en su primer ingreso.
 * Mientras eso no pase figura como <strong>invitada</strong>.
 *
 * <p>Esa etiqueta no es decorativa: es la respuesta a «le di de alta y no puede
 * entrar», que sin ella se diagnostica mirando la base. La maqueta original no
 * la tenía porque daba por hecho que el alta creaba el acceso, y no puede — la
 * Lambda no tiene salida a internet para hablar con Cognito.
 *
 * <h2>Dos acciones que no se ofrecen sobre uno mismo</h2>
 *
 * <p>Desactivarse y retirarse el acceso. El backend las rechaza igualmente; aquí
 * ni siquiera aparece el botón, porque ofrecer algo que siempre falla es peor
 * que no ofrecerlo.
 */
@Component({
  selector: 'app-usuarios',
  imports: [
    EncabezadoPaginaComponent,
    TablaDatosComponent,
    ConfirmacionComponent,
    ReactiveFormsModule,
  ],
  templateUrl: './usuarios.component.html',
})
export class UsuariosComponent {
  private readonly api = inject(ConfiguracionApiService);
  private readonly constructorFormulario = inject(FormBuilder);

  readonly columnas: ColumnaTabla[] = [
    { campo: 'nombre', titulo: 'Nombre', ordenable: true },
    { campo: 'email', titulo: 'Correo', ordenable: true },
    { campo: 'rol', titulo: 'Rol', ordenable: true, ancho: 'w-40' },
    { campo: 'alcance', titulo: 'Alcance', ancho: 'w-48' },
    { campo: 'estado', titulo: 'Estado', ancho: 'w-32' },
  ];

  readonly registros = signal<Record<string, unknown>[]>([]);
  readonly establecimientos = signal<Establecimiento[]>([]);
  readonly roles = signal<RolAsignable[]>([]);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);

  readonly formularioAbierto = signal(false);
  readonly enEdicion = signal<UsuarioApi | null>(null);
  readonly confirmacionAbierta = signal(false);
  readonly accionPendiente = signal<AccionPendiente>(null);

  private objetivo: UsuarioApi | null = null;
  private originales: UsuarioApi[] = [];

  formulario = this.constructorFormulario.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    nombre: ['', [Validators.required]],
    rolId: ['', [Validators.required]],
    sucursalId: [''],
  });

  constructor() {
    void this.cargar();
  }

  get controles() {
    return this.formulario.controls;
  }

  get tituloConfirmacion(): string {
    return this.accionPendiente() === 'retirar'
      ? '¿Retirar el acceso a esta empresa?'
      : '¿Desactivar a esta persona?';
  }

  get mensajeConfirmacion(): string {
    if (this.accionPendiente() === 'retirar') {
      return `${this.objetivo?.nombre ?? 'La persona'} dejará de ver esta empresa. Sigue existiendo en la cuenta y conserva las demás empresas a las que tenga acceso.`;
    }
    return `${this.objetivo?.nombre ?? 'La persona'} no podrá entrar a ninguna empresa de la cuenta, aunque su sesión siga abierta. Es la única forma de cortar el acceso de inmediato.`;
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const [usuarios, establecimientos, roles] = await Promise.all([
        this.api.usuarios(),
        this.api.establecimientos(),
        this.api.rolesAsignables(),
      ]);

      this.originales = usuarios;
      this.establecimientos.set(establecimientos);
      this.roles.set(roles);

      this.registros.set(
        usuarios.map((u) => ({
          id: u.asignacionId,
          nombre: u.nombre,
          email: u.email,
          rol: u.rolNombre,
          alcance: u.todosLosEstablecimientos
            ? 'Todos los establecimientos'
            : (u.sucursalNombre ?? 'Establecimiento desconocido'),
          // Tres estados, no dos. «Invitado» es el que explica por qué alguien
          // activo todavía no puede entrar.
          estado: !u.activo ? 'Inactivo' : u.invitado ? 'Invitado' : 'Activo',
          activo: u.activo,
          invitado: u.invitado,
        }))
      );
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar los usuarios.'));
    } finally {
      this.cargando.set(false);
    }
  }

  abrirNuevo(): void {
    this.enEdicion.set(null);
    this.formulario.reset({ email: '', nombre: '', rolId: '', sucursalId: '' });
    this.controles.email.enable();
    this.controles.nombre.enable();
    this.formularioAbierto.set(true);
  }

  abrirEdicion(fila: Record<string, unknown>): void {
    const usuario = this.originales.find((u) => u.asignacionId === fila['id']);
    if (!usuario) {
      return;
    }

    this.enEdicion.set(usuario);
    this.formulario.reset({
      email: usuario.email,
      nombre: usuario.nombre,
      rolId: usuario.rolId,
      sucursalId: usuario.sucursalId ?? '',
    });
    // El correo y el nombre son de la persona, no de su acceso a esta empresa:
    // cambiarlos aquí los cambiaría en todas. Esta pantalla edita la asignación.
    this.controles.email.disable();
    this.controles.nombre.disable();
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
    const sucursalId = valores.sucursalId || null;

    try {
      const editando = this.enEdicion();
      if (editando) {
        await this.api.reasignarUsuario(editando.asignacionId, {
          rolId: valores.rolId,
          sucursalId,
        });
      } else {
        await this.api.invitarUsuario({
          email: valores.email,
          nombre: valores.nombre,
          rolId: valores.rolId,
          sucursalId,
        });
      }
      this.cerrarFormulario();
      await this.cargar();
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudo guardar el usuario.'));
    } finally {
      this.guardando.set(false);
    }
  }

  pedirDesactivacion(fila: Record<string, unknown>): void {
    this.prepararConfirmacion(fila, 'desactivar');
  }

  pedirRetirada(fila: Record<string, unknown>): void {
    this.prepararConfirmacion(fila, 'retirar');
  }

  private prepararConfirmacion(fila: Record<string, unknown>, accion: AccionPendiente): void {
    const usuario = this.originales.find((u) => u.asignacionId === fila['id']);
    if (!usuario) {
      return;
    }
    this.objetivo = usuario;
    this.accionPendiente.set(accion);
    this.confirmacionAbierta.set(true);
  }

  async confirmar(): Promise<void> {
    const usuario = this.objetivo;
    const accion = this.accionPendiente();
    if (!usuario || !accion) {
      return;
    }

    this.confirmacionAbierta.set(false);
    this.error.set(null);

    try {
      if (accion === 'retirar') {
        await this.api.retirarUsuario(usuario.asignacionId);
      } else {
        await this.api.cambiarEstadoUsuario(usuario.asignacionId, false);
      }
      await this.cargar();
    } catch (fallo: unknown) {
      // Aquí llegan las reglas que la pantalla no puede anticipar: el último
      // administrador de la cuenta, y uno mismo si la fila fuera la propia.
      this.error.set(mensajeDeError(fallo, 'No se pudo completar la acción.'));
    } finally {
      this.objetivo = null;
      this.accionPendiente.set(null);
    }
  }

  cancelarConfirmacion(): void {
    this.confirmacionAbierta.set(false);
    this.objetivo = null;
    this.accionPendiente.set(null);
  }

  /** Reactivar no destruye nada y se deshace igual: sin diálogo. */
  async reactivar(fila: Record<string, unknown>): Promise<void> {
    this.error.set(null);
    try {
      await this.api.cambiarEstadoUsuario(String(fila['id']), true);
      await this.cargar();
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudo reactivar a la persona.'));
    }
  }
}
