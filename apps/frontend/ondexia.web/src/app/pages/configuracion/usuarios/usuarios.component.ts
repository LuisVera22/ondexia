import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, AccionDeFila,
  ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';
import { ModalComponent } from '../../../shared/components/comunes/modal/modal.component';
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
  UsuarioApi,
  mensajeDeError,
} from '../../../nucleo/configuracion.api.service';
import { AvisosService } from '../../../shared/services/avisos.service';
import { repartirFallo } from '../../../shared/formularios/fallo-de-formulario';
import { ErrorCampoComponent } from '../../../shared/components/comunes/error-campo/error-campo.component';

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
    ModalComponent,
    BotonComponent,
    DesplegableComponent,
    ReactiveFormsModule,
    ErrorCampoComponent,
  ],
  templateUrl: './usuarios.component.html',
})
export class UsuariosComponent {
  private readonly api = inject(ConfiguracionApiService);
  private readonly avisos = inject(AvisosService);
  private readonly router = inject(Router);
  private readonly constructorFormulario = inject(FormBuilder);
  readonly accionesDeFila: AccionDeFila[] = [
    { id: 'ver', etiqueta: 'Ver el acceso', icono: 'ver' },
    {
      id: 'reactivar',
      etiqueta: 'Reactivar',
      icono: 'reactivar',
      disponible: (registro) => registro['activo'] !== true,
    },
    {
      id: 'desactivar',
      etiqueta: 'Desactivar',
      icono: 'desactivar',
      peligrosa: true,
      disponible: (registro) => registro['activo'] === true,
    },
    { id: 'retirar', etiqueta: 'Quitar el acceso', icono: 'retirar', peligrosa: true },
  ];


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

  /**
   * Solo el fallo al cargar el listado, que se pinta en lugar de la tabla.
   *
   * <p>El resultado de invitar, desactivar o retirar va a un aviso. Esto no: no
   * es respuesta a nada que el usuario haya pulsado, y no hay tabla detrás que
   * explique por qué está vacía.
   */
  readonly error = signal<string | null>(null);

  readonly modalAbierto = signal(false);
  readonly confirmacionAbierta = signal(false);
  readonly accionPendiente = signal<AccionPendiente>(null);

  private objetivo: UsuarioApi | null = null;
  private originales: UsuarioApi[] = [];

  readonly opcionesRol = computed<OpcionDesplegable[]>(() => [
    { valor: '', etiqueta: 'Elige un rol…' },
    ...this.roles().map((rol) => ({ valor: rol.id, etiqueta: rol.nombre })),
  ]);

  /**
   * El alcance, con los establecimientos ACTIVOS.
   *
   * <p>Los desactivados se quedan fuera: acotar a alguien a un local que ya no
   * emite le deja sin poder trabajar, y hasta ahora aparecían en la lista porque
   * el endpoint devuelve activos e inactivos.
   */
  readonly opcionesAlcance = computed<OpcionDesplegable[]>(() => [
    { valor: '', etiqueta: 'Todos los establecimientos' },
    ...this.establecimientos()
      .filter((e) => e.activa)
      .map((e) => ({ valor: e.id, etiqueta: `${e.codigo} · ${e.nombre}` })),
  ]);

  formulario = this.constructorFormulario.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    nombre: ['', [Validators.required]],
    apellido: ['', [Validators.required]],
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

  /**
   * Vuelve a traer el listado, a peticion del usuario.
   *
   * <p>Existe porque {@code cargar} es privado y la plantilla no lo alcanza.
   * No es lo mismo que recargar la pagina: no se pierde el orden, ni la
   * pagina en la que se estaba, ni lo escrito en el buscador.
   */
  recargar(): void {
    void this.cargar();
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
    this.formulario.reset({ email: '', nombre: '', apellido: '', rolId: '', sucursalId: '' });
    this.modalAbierto.set(true);
  }

  cerrarModal(): void {
    this.modalAbierto.set(false);
  }

  /** Abrir a alguien lleva a la ficha de su acceso, que es donde se edita. */
  abrirFicha(fila: Record<string, unknown>): void {
    void this.router.navigate(['/configuracion/usuarios', fila['id']]);
  }

  /**
   * Invita a la persona y cierra el modal.
   *
   * <p>El aviso dice que queda como invitada, y no es un detalle de cortesía:
   * es la respuesta a «le di de alta y no puede entrar». Su cuenta de acceso la
   * crea ella al registrarse; el alta solo reserva el sitio.
   */
  readonly invitar = accionConEstado(async () => {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      throw new Error('Formulario incompleto');
    }

    const valores = this.formulario.getRawValue();

    try {
      await this.api.invitarUsuario({
        email: valores.email,
        nombre: valores.nombre,
        apellido: valores.apellido,
        rolId: valores.rolId,
        sucursalId: valores.sucursalId || null,
      });

      this.cerrarModal();
      await this.cargar();
      this.avisos.exito(
        `${valores.nombre} figura como invitada hasta que cree su cuenta con ${valores.email}`,
        'Usuario agregado'
      );
    } catch (fallo: unknown) {
      // El modal se queda abierto: el correo repetido es el fallo habitual, y
      // el usuario va a querer corregir ese campo, no teclear todo otra vez.
      repartirFallo(fallo, this.formulario, this.avisos, 'No se pudo agregar al usuario.');
      throw fallo;
    }
  });

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

  readonly confirmar = accionConEstado(async () => {
    const usuario = this.objetivo;
    const accion = this.accionPendiente();
    if (!usuario || !accion) {
      return;
    }

    try {
      if (accion === 'retirar') {
        await this.api.retirarUsuario(usuario.asignacionId);
      } else {
        await this.api.cambiarEstadoUsuario(usuario.asignacionId, false);
      }

      this.confirmacionAbierta.set(false);
      this.objetivo = null;
      this.accionPendiente.set(null);
      await this.cargar();

      this.avisos.exito(
        accion === 'retirar'
          ? `${usuario.nombre} ya no ve esta empresa`
          : `${usuario.nombre} no puede entrar a ninguna empresa de la cuenta`,
        accion === 'retirar' ? 'Acceso retirado' : 'Persona desactivada'
      );
    } catch (fallo: unknown) {
      // Aquí llegan las reglas que la pantalla no puede anticipar: el último
      // administrador de la cuenta, y uno mismo si la fila fuera la propia. El
      // diálogo se queda abierto para que el mensaje se lea junto a la acción
      // que lo provocó.
      this.avisos.error(mensajeDeError(fallo, 'No se pudo completar la acción.'));
      throw fallo;
    }
  });

  cancelarConfirmacion(): void {
    this.confirmacionAbierta.set(false);
    this.objetivo = null;
    this.accionPendiente.set(null);
  }

  /**
   * Reactivar no destruye nada y se deshace igual: sin diálogo.
   *
   * <p>Método normal y no {@code accionConEstado}: es un botón por fila, y una
   * sola señal de estado compartida pondría el indicador de carga en todas las
   * filas a la vez. Los botones de fila con estado propio piden un estado por
   * registro, y eso es otro trabajo.
   */
  async reactivar(fila: Record<string, unknown>): Promise<void> {
    try {
      await this.api.cambiarEstadoUsuario(String(fila['id']), true);
      await this.cargar();
      this.avisos.exito(`${fila['nombre']} vuelve a poder entrar`, 'Persona reactivada');
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo reactivar a la persona.'));
    }
  }

  ejecutarAccion(evento: { accion: string; registro: Record<string, unknown> }): void {
    if (evento.accion === 'ver') {
      this.abrirFicha(evento.registro);
    } else if (evento.accion === 'desactivar') {
      this.pedirDesactivacion(evento.registro);
    } else if (evento.accion === 'reactivar') {
      void this.reactivar(evento.registro);
    } else if (evento.accion === 'retirar') {
      this.pedirRetirada(evento.registro);
    }
  }
}
