import { Component, computed, inject, signal } from '@angular/core';
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
  SerieApi,
  TipoDocumento,
  mensajeDeError,
} from '../../../nucleo/configuracion.api.service';
import { AvisosService } from '../../../shared/services/avisos.service';
import { repartirFallo } from '../../../shared/formularios/fallo-de-formulario';
import { ErrorCampoComponent } from '../../../shared/components/comunes/error-campo/error-campo.component';

/**
 * Series de comprobante por establecimiento y tipo de documento.
 *
 * <h2>Lo que esta pantalla no ofrece, y por qué</h2>
 *
 * <p>No se puede editar el correlativo, y no es que falte el campo: el backend
 * tampoco lo acepta. El número lo asigna el sistema al confirmar el documento,
 * dentro de la transacción, y un botón para «corregirlo» acabaría usándose para
 * tapar un error y produciría dos comprobantes con el mismo número (DTE F-02).
 *
 * <p>Quien migra desde otro sistema sí necesita empezar en 4300, y para eso está
 * el número inicial <strong>del alta</strong>: se fija una vez y desaparece.
 *
 * <p>Tampoco se puede eliminar una serie. Nombra a todos los comprobantes que
 * emitió; lo que se hace es desactivarla, y entonces deja de emitir sin perder
 * su numeración — reactivarla continúa donde se quedó, no vuelve a empezar.
 *
 * <h2>Cambios respecto a la maqueta</h2>
 *
 * <p>La columna «Último número» se acompaña del siguiente ya formateado, que es
 * lo que de verdad se quiere saber al mirar esta tabla. Y el tipo de documento
 * sale del catálogo 01 del servidor, no de una lista escrita aquí: la letra de
 * la serie depende del tipo, y tener esa correspondencia en dos sitios es cómo
 * se acaba ofreciendo una combinación que el servidor rechaza.
 */
@Component({
  selector: 'app-series',
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
  templateUrl: './series.component.html',
})
export class SeriesComponent {
  private readonly api = inject(ConfiguracionApiService);
  private readonly avisos = inject(AvisosService);
  private readonly constructorFormulario = inject(FormBuilder);
  readonly accionesDeFila: AccionDeFila[] = [
    {
      id: 'reactivar',
      etiqueta: 'Reactivar',
      icono: 'reactivar',
      disponible: (registro) => registro['activa'] !== true,
    },
    {
      id: 'desactivar',
      etiqueta: 'Desactivar',
      icono: 'desactivar',
      peligrosa: true,
      disponible: (registro) => registro['activa'] === true,
    },
  ];


  readonly columnas: ColumnaTabla[] = [
    { campo: 'serie', titulo: 'Serie', ordenable: true, ancho: 'w-28', principal: true },
    { campo: 'tipoDocumento', titulo: 'Tipo de comprobante', ordenable: true },
    { campo: 'establecimiento', titulo: 'Establecimiento', ordenable: true },
    { campo: 'ultimoNumero', titulo: 'Último emitido', formato: 'cantidad', ancho: 'w-32' },
    { campo: 'siguiente', titulo: 'Siguiente', ancho: 'w-40' },
    {
      campo: 'estado',
      titulo: 'Estado',
      ancho: 'w-32',
      formato: 'insignia',
      tono: (registro) => (registro['activa'] === true ? 'exito' : 'neutro'),
    },
  ];

  readonly registros = signal<Record<string, unknown>[]>([]);
  readonly establecimientos = signal<Establecimiento[]>([]);
  readonly tipos = signal<TipoDocumento[]>([]);
  readonly cargando = signal(true);

  /** Solo el fallo al cargar el listado. El resto va a avisos. */
  readonly error = signal<string | null>(null);

  readonly modalAbierto = signal(false);
  readonly confirmacionAbierta = signal(false);

  private aDesactivar: SerieApi | null = null;
  readonly opcionesTipo = computed<OpcionDesplegable[]>(() => [
    { valor: '', etiqueta: 'Elige un tipo…' },
    ...this.tipos().map((tipo) => ({ valor: tipo.codigo, etiqueta: tipo.nombre })),
  ]);

  /**
   * Establecimientos activos.
   *
   * <p>Sin opción vacía: SUNAT relaciona la serie con el anexo que emite, así
   * que es obligatorio. Y sin los desactivados, porque una serie nueva atada a
   * un local que ya no emite nace inutilizable.
   */
  readonly opcionesEstablecimiento = computed<OpcionDesplegable[]>(() =>
    this.establecimientos()
      .filter((e) => e.activa)
      .map((e) => ({ valor: e.id, etiqueta: `${e.codigo} · ${e.nombre}` }))
  );

  private originales: SerieApi[] = [];

  formulario = this.constructorFormulario.nonNullable.group({
    sucursalId: ['', [Validators.required]],
    tipoDocumento: ['', [Validators.required]],
    serie: ['', [Validators.required, Validators.pattern(/^[A-Za-z][A-Za-z0-9]{3}$/)]],
    numeroInicial: [0, [Validators.min(0), Validators.max(99999999)]],
  });

  constructor() {
    void this.cargar();
  }

  get controles() {
    return this.formulario.controls;
  }

  /**
   * La letra que SUNAT exige según el tipo elegido. Se muestra como ayuda en vez
   * de forzarla en el campo: escribirla es parte de reconocer la serie propia, y
   * un prefijo fijo hace dudar de si hay que teclearla o no.
   */
  get letraEsperada(): string {
    switch (this.controles.tipoDocumento.value) {
      case '01':
        return 'F';
      case '03':
        return 'B';
      case '07':
      case '08':
        return 'F o B';
      case '09':
        return 'T';
      default:
        return '';
    }
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
      const [series, establecimientos, tipos] = await Promise.all([
        this.api.series(),
        this.api.establecimientos(),
        this.api.tiposDocumento(),
      ]);

      this.originales = series;
      this.establecimientos.set(establecimientos);
      this.tipos.set(tipos);

      const porId = new Map(establecimientos.map((e) => [e.id, e]));

      this.registros.set(
        series.map((s) => ({
          id: s.id,
          serie: s.serie,
          tipoDocumento: s.tipoDocumentoNombre,
          establecimiento:
            porId.get(s.sucursalId)?.nombre ?? 'Establecimiento desconocido',
          ultimoNumero: s.ultimoNumero,
          // Solo tiene sentido si la serie va a emitir. En una desactivada, el
          // «siguiente» sería un número que nadie va a usar.
          siguiente: s.activa ? s.siguienteNumero : '—',
          estado: s.activa ? 'Activa' : 'Inactiva',
          activa: s.activa,
        }))
      );
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar las series.'));
    } finally {
      this.cargando.set(false);
    }
  }

  abrirNueva(): void {
    this.formulario.reset({
      sucursalId: this.establecimientos()[0]?.id ?? '',
      tipoDocumento: '',
      serie: '',
      numeroInicial: 0,
    });
    this.modalAbierto.set(true);
  }

  cerrarModal(): void {
    this.modalAbierto.set(false);
  }

  /**
   * Crea la serie y cierra el modal.
   *
   * <p>No hay edición de series, y por eso esta pantalla no tiene ficha: una
   * serie con su correlativo no se corrige, se desactiva y se abre otra. El
   * número emitido ya está en documentos entregados.
   */
  readonly crear = accionConEstado(async () => {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      throw new Error('Formulario incompleto');
    }

    const valores = this.formulario.getRawValue();

    try {
      const creada = await this.api.crearSerie({
        sucursalId: valores.sucursalId,
        tipoDocumento: valores.tipoDocumento,
        serie: valores.serie,
        numeroInicial: valores.numeroInicial ?? 0,
      });

      this.cerrarModal();
      await this.cargar();
      // siguienteNumero llega ya formateado por el backend («F001-00000001»),
      // que es exactamente lo que el usuario verá en el próximo comprobante.
      this.avisos.exito(`El próximo comprobante será ${creada.siguienteNumero}`, 'Serie creada');
    } catch (fallo: unknown) {
      // El servidor explica bien los dos casos previsibles: la letra que no
      // corresponde al tipo, y el tipo que la empresa tiene deshabilitado en
      // Configuración › Comprobantes. El modal se queda abierto para corregir.
      repartirFallo(fallo, this.formulario, this.avisos, 'No se pudo crear la serie.');
      throw fallo;
    }
  });

  pedirDesactivacion(fila: Record<string, unknown>): void {
    const serie = this.originales.find((s) => s.id === fila['id']);
    if (!serie) {
      return;
    }
    this.aDesactivar = serie;
    this.confirmacionAbierta.set(true);
  }

  readonly desactivar = accionConEstado(async () => {
    const serie = this.aDesactivar;
    if (!serie) {
      return;
    }

    try {
      await this.api.cambiarEstadoSerie(serie.id, false);
      this.confirmacionAbierta.set(false);
      this.aDesactivar = null;
      await this.cargar();
      this.avisos.exito(`${serie.serie} ya no se ofrece al emitir`, 'Serie desactivada');
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo desactivar la serie.'));
      throw fallo;
    }
  });

  cancelarDesactivacion(): void {
    this.confirmacionAbierta.set(false);
    this.aDesactivar = null;
  }

  get serieADesactivar(): string {
    return this.aDesactivar?.serie ?? '';
  }

  /** Reactivar no necesita confirmación: no destruye nada y se deshace igual. */
  async reactivar(fila: Record<string, unknown>): Promise<void> {
    try {
      await this.api.cambiarEstadoSerie(String(fila['id']), true);
      await this.cargar();
      this.avisos.exito(`${fila['serie']} vuelve a ofrecerse al emitir`, 'Serie reactivada');
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo reactivar la serie.'));
    }
  }

  ejecutarAccion(evento: { accion: string; registro: Record<string, unknown> }): void {
    if (evento.accion === 'desactivar') {
      this.pedirDesactivacion(evento.registro);
    } else if (evento.accion === 'reactivar') {
      void this.reactivar(evento.registro);
    }
  }
}
