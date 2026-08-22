import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';
import { ModalComponent } from '../../../shared/components/comunes/modal/modal.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { accionConEstado } from '../../../shared/components/comunes/boton/estado-accion';
import {
  ConfiguracionApiService,
  ModuloApi,
  RolApi,
  SubmoduloApi,
  mensajeDeError,
} from '../../../nucleo/configuracion.api.service';
import { AvisosService } from '../../../shared/services/avisos.service';
import { repartirFallo } from '../../../shared/formularios/fallo-de-formulario';

/**
 * Roles y permisos, con la matriz en tres niveles.
 *
 * <h2>Módulo → submódulo → función</h2>
 *
 * <p>Primero se da acceso al módulo —Almacén, Compras, Ventas, Configuración—,
 * luego a los submódulos que correspondan, y al final a las funciones concretas:
 * consultar, registrar, editar, anular.
 *
 * <p>La autorización es <strong>conjuntiva</strong>: hacen falta los tres. Eso es
 * lo que convierte el interruptor de módulo en una puerta de verdad — apagar
 * «Almacén» deja fuera sus treinta y tantas casillas de una vez.
 *
 * <p>Y las <strong>borra</strong>, no las deja dormidas: al apagar el módulo se
 * desmarca lo de dentro aquí mismo, y el servidor poda al guardar. Volver a
 * encenderlo lo devuelve vacío. Es la lectura menos sorprendente —lo que se ve
 * marcado es exactamente lo que autoriza— y por eso el desmarcado ocurre a la
 * vista y no en silencio al guardar.
 *
 * <h2>Nada se deniega en silencio</h2>
 *
 * <p>Ese diseño tiene un modo de fallo caro: una casilla marcada que aun así no
 * autoriza, porque falta un eslabón de arriba. Se cierra por dos lados.
 *
 * <p>Aquí, apagando visiblemente lo que cuelga de un padre cerrado —la fila se
 * pinta en gris, las casillas se deshabilitan y se dice por qué—. Y en el
 * servidor, que al guardar <em>poda</em> lo que no tenga sus padres, de modo que
 * ese estado incoherente no llega a existir en la base.
 *
 * <h2>El catálogo viene del servidor, incluidos los nombres</h2>
 *
 * <p>Antes había aquí un mapa de código a nombre escrito a mano. Se quitó: al
 * añadir un módulo nadie se acordaba de ampliarlo, la pantalla mostraba
 * {@code almacen.tipo_precio} en crudo y no fallaba nada. Ahora un módulo nuevo
 * aparece solo.
 */
@Component({
  selector: 'app-roles',
  imports: [
    EncabezadoPaginaComponent,
    ConfirmacionComponent,
    ModalComponent,
    BotonComponent,
    ReactiveFormsModule,
  ],
  templateUrl: './roles.component.html',
})
export class RolesComponent {
  private readonly api = inject(ConfiguracionApiService);
  private readonly avisos = inject(AvisosService);
  private readonly constructorFormulario = inject(FormBuilder);

  readonly roles = signal<RolApi[]>([]);
  readonly catalogo = signal<ModuloApi[]>([]);
  readonly cargando = signal(true);

  /** Solo el fallo al cargar. El resultado de cada acción va a un aviso. */
  readonly error = signal<string | null>(null);

  /** Rol cuya matriz se está viendo. Null = ninguno seleccionado. */
  readonly seleccionado = signal<RolApi | null>(null);
  readonly marcados = signal<Set<string>>(new Set());

  /** Módulos desplegados. Cerrados de inicio: cuatro títulos caben de un vistazo. */
  readonly desplegados = signal<Set<string>>(new Set());

  readonly duplicandoDe = signal<RolApi | null>(null);
  readonly renombrando = signal<RolApi | null>(null);
  readonly confirmacionAbierta = signal(false);

  private aEliminar: RolApi | null = null;

  formularioDuplicado = this.constructorFormulario.nonNullable.group({
    nombre: ['', [Validators.required]],
  });

  formularioRenombrado = this.constructorFormulario.nonNullable.group({
    nombre: ['', [Validators.required]],
    descripcion: [''],
  });

  /** Solo cuentan las funciones: es lo que el usuario entiende por «permisos». */
  readonly totalFunciones = computed(() => {
    const marcados = this.marcados();
    return this.catalogo()
      .flatMap((m) => m.submodulos)
      .flatMap((s) => s.funciones)
      .filter((f) => marcados.has(f.id)).length;
  });

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const [roles, catalogo] = await Promise.all([
        this.api.roles(),
        this.api.catalogoPermisos(),
      ]);
      this.roles.set(roles);
      this.catalogo.set(catalogo);

      const abierto = this.seleccionado();
      if (abierto) {
        const vigente = roles.find((r) => r.id === abierto.id) ?? null;
        this.seleccionado.set(vigente);
        if (vigente) {
          await this.cargarPermisos(vigente);
        }
      }
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar los roles.'));
    } finally {
      this.cargando.set(false);
    }
  }

  async seleccionar(rol: RolApi): Promise<void> {
    this.seleccionado.set(rol);
    await this.cargarPermisos(rol);
  }

  private async cargarPermisos(rol: RolApi): Promise<void> {
    this.error.set(null);
    try {
      this.marcados.set(new Set(await this.api.permisosDelRol(rol.id)));
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar los permisos del rol.'));
    }
  }

  /** Los predefinidos se ven, no se tocan. */
  get soloLectura(): boolean {
    return this.seleccionado()?.delSistema ?? true;
  }

  estaMarcado(id: string): boolean {
    return this.marcados().has(id);
  }

  // ── Nivel 1: el módulo ───────────────────────────────────────────────────

  /**
   * Apagar el módulo apaga todo lo de dentro, y encenderlo no enciende nada.
   *
   * <p>La asimetría es intencionada. Apagar tiene que arrastrar, porque si no lo
   * hiciera la pantalla mostraría casillas marcadas que el servidor no autoriza.
   * Encender no debe arrastrar: quien abre «Almacén» está empezando a componer,
   * y marcarle sus treinta casillas de golpe le concede cosas que no ha mirado —
   * que es el error caro en la dirección contraria.
   */
  alternarModulo(modulo: ModuloApi): void {
    if (this.soloLectura) {
      return;
    }
    const copia = new Set(this.marcados());

    if (copia.has(modulo.id)) {
      copia.delete(modulo.id);
      for (const submodulo of modulo.submodulos) {
        copia.delete(submodulo.id);
        submodulo.funciones.forEach((f) => copia.delete(f.id));
      }
    } else {
      copia.add(modulo.id);
      // Se despliega para que se vea que ahora hay algo que elegir dentro.
      this.desplegar(modulo.codigo, true);
    }

    this.marcados.set(copia);
  }

  // ── Nivel 2: el submódulo ────────────────────────────────────────────────

  /** Cerrado si su módulo lo está: sin él, nada de dentro autoriza. */
  bloqueadoPorModulo(modulo: ModuloApi): boolean {
    return !this.marcados().has(modulo.id);
  }

  alternarSubmodulo(modulo: ModuloApi, submodulo: SubmoduloApi): void {
    if (this.soloLectura || this.bloqueadoPorModulo(modulo)) {
      return;
    }
    const copia = new Set(this.marcados());

    if (copia.has(submodulo.id)) {
      copia.delete(submodulo.id);
      submodulo.funciones.forEach((f) => copia.delete(f.id));
    } else {
      copia.add(submodulo.id);
    }

    this.marcados.set(copia);
  }

  // ── Nivel 3: la función ──────────────────────────────────────────────────

  bloqueadaPorSubmodulo(modulo: ModuloApi, submodulo: SubmoduloApi): boolean {
    return this.bloqueadoPorModulo(modulo) || !this.marcados().has(submodulo.id);
  }

  alternarFuncion(modulo: ModuloApi, submodulo: SubmoduloApi, funcionId: string): void {
    if (this.soloLectura || this.bloqueadaPorSubmodulo(modulo, submodulo)) {
      return;
    }
    const copia = new Set(this.marcados());
    if (copia.has(funcionId)) {
      copia.delete(funcionId);
    } else {
      copia.add(funcionId);
    }
    this.marcados.set(copia);
  }

  /** Marca o desmarca de golpe todas las funciones de un submódulo. */
  alternarTodasLasFunciones(modulo: ModuloApi, submodulo: SubmoduloApi): void {
    if (this.soloLectura || this.bloqueadaPorSubmodulo(modulo, submodulo)) {
      return;
    }
    const copia = new Set(this.marcados());
    const todas = submodulo.funciones.every((f) => copia.has(f.id));
    submodulo.funciones.forEach((f) => (todas ? copia.delete(f.id) : copia.add(f.id)));
    this.marcados.set(copia);
  }

  // ── Resumen por módulo, para leer la matriz plegada ──────────────────────

  submodulosActivos(modulo: ModuloApi): number {
    return modulo.submodulos.filter((s) => this.marcados().has(s.id)).length;
  }

  funcionesActivas(modulo: ModuloApi): number {
    const marcados = this.marcados();
    return modulo.submodulos.flatMap((s) => s.funciones).filter((f) => marcados.has(f.id)).length;
  }

  estaDesplegado(codigo: string): boolean {
    return this.desplegados().has(codigo);
  }

  alternarDespliegue(codigo: string): void {
    this.desplegar(codigo, !this.estaDesplegado(codigo));
  }

  private desplegar(codigo: string, abierto: boolean): void {
    const copia = new Set(this.desplegados());
    if (abierto) {
      copia.add(codigo);
    } else {
      copia.delete(codigo);
    }
    this.desplegados.set(copia);
  }

  readonly guardarPermisos = accionConEstado(async () => {
    const rol = this.seleccionado();
    if (!rol || rol.delSistema) {
      return;
    }

    try {
      // El servidor devuelve lo que quedó tras podar. Se adopta su respuesta en
      // vez de dar por buena la selección local: si algo se podó, la pantalla
      // debe mostrarlo, no seguir enseñando lo que se mandó.
      const vigentes = await this.api.cambiarPermisosDelRol(rol.id, [...this.marcados()]);
      this.marcados.set(new Set(vigentes));
      await this.cargar();

      // El aviso dice cuántas quedaron, no «guardado»: si el servidor podó
      // funciones por depender de un submódulo sin marcar, el número es la
      // forma de notarlo sin comparar el árbol a ojo.
      // «funciones», sin acento: el plural lo pierde. Con la construccion
      // `función${...'es'}` salia «funciónes».
      const cuantas = vigentes.length;
      this.avisos.exito(
        `${cuantas} ${cuantas === 1 ? 'función vigente' : 'funciones vigentes'} en ${rol.nombre}`,
        'Permisos guardados'
      );
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudieron guardar los permisos.'));
      throw fallo;
    }
  });

  // ── Duplicar ─────────────────────────────────────────────────────────────

  abrirDuplicado(rol: RolApi): void {
    this.duplicandoDe.set(rol);
    this.formularioDuplicado.reset({ nombre: `${rol.nombre} (copia)` });
  }

  cerrarDuplicado(): void {
    this.duplicandoDe.set(null);
  }

  readonly duplicar = accionConEstado(async () => {
    const origen = this.duplicandoDe();
    if (!origen || this.formularioDuplicado.invalid) {
      this.formularioDuplicado.markAllAsTouched();
      throw new Error('Formulario incompleto');
    }

    try {
      const copia = await this.api.duplicarRol(
        origen.id,
        this.formularioDuplicado.getRawValue().nombre
      );
      this.cerrarDuplicado();
      await this.cargar();
      await this.seleccionar(copia);
      this.avisos.exito(
        `${copia.nombre} empieza con los permisos de ${origen.nombre} y ya es editable`,
        'Rol duplicado'
      );
    } catch (fallo: unknown) {
      repartirFallo(fallo, this.formularioDuplicado, this.avisos, 'No se pudo duplicar el rol.');
      throw fallo;
    }
  });

  // ── Renombrar ────────────────────────────────────────────────────────────

  abrirRenombrado(rol: RolApi): void {
    this.renombrando.set(rol);
    this.formularioRenombrado.reset({
      nombre: rol.nombre,
      descripcion: rol.descripcion ?? '',
    });
  }

  cerrarRenombrado(): void {
    this.renombrando.set(null);
  }

  readonly renombrar = accionConEstado(async () => {
    const rol = this.renombrando();
    if (!rol || this.formularioRenombrado.invalid) {
      this.formularioRenombrado.markAllAsTouched();
      throw new Error('Formulario incompleto');
    }

    try {
      const valores = this.formularioRenombrado.getRawValue();
      await this.api.renombrarRol(rol.id, valores.nombre, valores.descripcion || null);
      this.cerrarRenombrado();
      await this.cargar();
      this.avisos.exito(`Ahora se llama ${valores.nombre}`, 'Rol renombrado');
    } catch (fallo: unknown) {
      repartirFallo(fallo, this.formularioRenombrado, this.avisos, 'No se pudo renombrar el rol.');
      throw fallo;
    }
  });

  // ── Eliminar ─────────────────────────────────────────────────────────────

  pedirEliminacion(rol: RolApi): void {
    this.aEliminar = rol;
    this.confirmacionAbierta.set(true);
  }

  get mensajeEliminacion(): string {
    return `El rol «${this.aEliminar?.nombre ?? ''}» se borra y no se recupera. Nadie lo tiene asignado, así que no deja a ninguna persona sin permisos.`;
  }

  readonly eliminar = accionConEstado(async () => {
    const rol = this.aEliminar;
    if (!rol) {
      return;
    }

    try {
      await this.api.eliminarRol(rol.id);
      this.confirmacionAbierta.set(false);
      this.aEliminar = null;

      if (this.seleccionado()?.id === rol.id) {
        this.seleccionado.set(null);
        this.marcados.set(new Set());
      }
      await this.cargar();
      this.avisos.exito(`${rol.nombre} se borró y no se recupera`, 'Rol eliminado');
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo eliminar el rol.'));
      throw fallo;
    }
  });

  cancelarEliminacion(): void {
    this.confirmacionAbierta.set(false);
    this.aEliminar = null;
  }
}
