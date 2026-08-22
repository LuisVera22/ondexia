import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';
import { ModalComponent } from '../../../shared/components/comunes/modal/modal.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { accionConEstado } from '../../../shared/components/comunes/boton/estado-accion';
import {
  DesplegableComponent,
  OpcionDesplegable,
} from '../../../shared/components/comunes/desplegable/desplegable.component';
import {
  AlmacenApi,
  ConfiguracionApiService,
  Establecimiento,
  mensajeDeError,
} from '../../../nucleo/configuracion.api.service';
import { AvisosService } from '../../../shared/services/avisos.service';
import { repartirFallo } from '../../../shared/formularios/fallo-de-formulario';
import { ErrorCampoComponent } from '../../../shared/components/comunes/error-campo/error-campo.component';

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
    ModalComponent,
    BotonComponent,
    DesplegableComponent,
    ReactiveFormsModule,
    ErrorCampoComponent,
  ],
  templateUrl: './almacenes.component.html',
})
export class AlmacenesComponent {
  private readonly api = inject(ConfiguracionApiService);
  private readonly avisos = inject(AvisosService);
  private readonly router = inject(Router);
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
  /** Solo el fallo al cargar el listado. El resto va a avisos. */
  readonly error = signal<string | null>(null);

  readonly modalAbierto = signal(false);
  readonly confirmacionAbierta = signal(false);

  private aDesactivar: AlmacenApi | null = null;
  private originales: AlmacenApi[] = [];

  /**
   * Establecimientos activos, más «Sin asignar» primero.
   *
   * <p>Los desactivados quedan fuera: un almacén que cuelga de un local que ya
   * no emite no tiene a dónde mover mercadería. Hasta ahora aparecían porque el
   * endpoint devuelve activos e inactivos.
   */
  readonly opcionesEstablecimiento = computed<OpcionDesplegable[]>(() => [
    // Primera y explícita: no asignar es una decisión válida —la mercadería en
    // tránsito no pertenece a ningún local— y no un descuido.
    { valor: '', etiqueta: 'Sin asignar' },
    ...this.establecimientos()
      .filter((e) => e.activa)
      .map((e) => ({ valor: e.id, etiqueta: `${e.codigo} · ${e.nombre}` })),
  ]);

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
    this.formulario.reset({ codigo: '', nombre: '', sucursalId: '' });
    this.modalAbierto.set(true);
  }

  cerrarModal(): void {
    this.modalAbierto.set(false);
  }

  /** Abrir un almacén lleva a su ficha, que es donde se edita. */
  abrirFicha(fila: Record<string, unknown>): void {
    void this.router.navigate(['/almacen/almacenes', fila['id']]);
  }

  /**
   * Crea el almacén y cierra el modal.
   *
   * <p>Solo el caso de alta: el código se elige aquí y en la ficha ya no se
   * puede cambiar, así que este formulario no necesita habilitarlo y
   * deshabilitarlo según el caso como hacía el anterior.
   */
  readonly crear = accionConEstado(async () => {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      throw new Error('Formulario incompleto');
    }

    const valores = this.formulario.getRawValue();

    try {
      const creado = await this.api.crearAlmacen({
        codigo: valores.codigo,
        nombre: valores.nombre,
        sucursalId: valores.sucursalId || null,
      });

      this.cerrarModal();
      await this.cargar();
      this.avisos.exito(`${creado.codigo} · ${creado.nombre}`, 'Almacén creado');
    } catch (fallo: unknown) {
      // El código repetido es el fallo habitual, y el modal se queda abierto
      // para corregir ese campo sin teclear el resto otra vez.
      repartirFallo(fallo, this.formulario, this.avisos, 'No se pudo crear el almacén.');
      throw fallo;
    }
  });

  pedirDesactivacion(fila: Record<string, unknown>): void {
    const almacen = this.originales.find((a) => a.id === fila['id']);
    if (!almacen) {
      return;
    }
    this.aDesactivar = almacen;
    this.confirmacionAbierta.set(true);
  }

  get nombreADesactivar(): string {
    return this.aDesactivar?.nombre ?? '';
  }

  readonly desactivar = accionConEstado(async () => {
    const almacen = this.aDesactivar;
    if (!almacen) {
      return;
    }

    try {
      await this.api.desactivarAlmacen(almacen.id);
      this.confirmacionAbierta.set(false);
      this.aDesactivar = null;
      await this.cargar();
      this.avisos.exito(
        `${almacen.nombre} ya no admite movimientos de mercadería`,
        'Almacén desactivado'
      );
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo desactivar el almacén.'));
      throw fallo;
    }
  });

  cancelarDesactivacion(): void {
    this.confirmacionAbierta.set(false);
    this.aDesactivar = null;
  }
}
