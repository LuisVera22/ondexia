import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import {
  AccionDeFila,
  ColumnaTabla,
  TablaDatosComponent,
} from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';
import { ModalComponent } from '../../../shared/components/comunes/modal/modal.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { accionConEstado } from '../../../shared/components/comunes/boton/estado-accion';
import {
  DesplegableComponent,
  OpcionDesplegable,
} from '../../../shared/components/comunes/desplegable/desplegable.component';
import { ErrorCampoComponent } from '../../../shared/components/comunes/error-campo/error-campo.component';
import {
  CajaApi,
  FORMAS_DE_PAGO,
  FormaDePago,
  ImportesPorForma,
  SesionCajaApi,
  VentasApiService,
} from '../../../nucleo/ventas.api.service';
import { CajaActivaService } from '../../../nucleo/caja-activa.service';
import { mensajeDeError } from '../../../nucleo/errores';
import { ContextoService } from '../../../shared/services/contexto.service';
import { AvisosService } from '../../../shared/services/avisos.service';
import { repartirFallo } from '../../../shared/formularios/fallo-de-formulario';

/** Una fila del arqueo, ya calculada para pintarla. */
export interface LineaDeArqueo {
  readonly forma: string;
  readonly declarado: number;
  readonly calculado: number;
  readonly diferencia: number;
}

/**
 * Cajas: los puntos de cobro de cada establecimiento y sus turnos (doc 12 §3.4).
 *
 * <p>Una venta ocurre siempre dentro de una sesión de caja. Aquí se abre la
 * caja con lo que hay en el cajón y se cierra declarando lo que se contó; el
 * sistema calcula lo que debería haber y muestra la diferencia. No corrige
 * nada: la diferencia queda registrada tal cual.
 *
 * <p>La lista ya viene recortada por el servidor a lo que el usuario alcanza:
 * quien está acotado a un establecimiento no ve las cajas de otro.
 */
@Component({
  selector: 'app-cajas',
  imports: [
    EncabezadoPaginaComponent,
    TablaDatosComponent,
    ConfirmacionComponent,
    ModalComponent,
    BotonComponent,
    DesplegableComponent,
    ErrorCampoComponent,
    ReactiveFormsModule,
  ],
  templateUrl: './cajas.component.html',
})
export class CajasComponent {
  private readonly api = inject(VentasApiService);
  private readonly cajaActiva = inject(CajaActivaService);
  private readonly contexto = inject(ContextoService);
  private readonly avisos = inject(AvisosService);
  private readonly constructorFormulario = inject(FormBuilder);

  readonly formasDePago = FORMAS_DE_PAGO;

  readonly puedeRegistrar = computed(() => this.contexto.puede('ventas.caja:registrar'));

  readonly columnas: ColumnaTabla[] = [
    { campo: 'codigo', titulo: 'Código', ordenable: true, ancho: 'w-28' },
    { campo: 'nombre', titulo: 'Caja', ordenable: true, principal: true },
    { campo: 'establecimiento', titulo: 'Establecimiento' },
    { campo: 'apertura', titulo: 'Sesión' },
    {
      campo: 'estado',
      titulo: 'Estado',
      ancho: 'w-32',
      formato: 'insignia',
      tono: (registro) =>
        registro['abierta'] === true ? 'exito' : registro['activa'] === true ? 'neutro' : 'aviso',
    },
  ];

  /**
   * Cada acción con su condición, y ninguna sin permiso: ofrecer «Abrir» a
   * quien no puede abrir es ofrecer una puerta cerrada.
   */
  readonly accionesDeFila: AccionDeFila[] = [
    {
      id: 'abrir',
      etiqueta: 'Abrir caja',
      disponible: (r) =>
        r['activa'] === true && r['abierta'] !== true && this.contexto.puede('ventas.caja:abrir'),
    },
    {
      id: 'cerrar',
      etiqueta: 'Cerrar caja',
      disponible: (r) => r['abierta'] === true && this.contexto.puede('ventas.caja:cerrar'),
    },
    {
      id: 'renombrar',
      etiqueta: 'Cambiar nombre',
      icono: 'editar',
      disponible: () => this.contexto.puede('ventas.caja:editar'),
    },
    {
      id: 'reactivar',
      etiqueta: 'Reactivar',
      icono: 'reactivar',
      disponible: (r) => r['activa'] !== true && this.contexto.puede('ventas.caja:desactivar'),
    },
    {
      id: 'desactivar',
      etiqueta: 'Desactivar',
      icono: 'desactivar',
      peligrosa: true,
      disponible: (r) =>
        r['activa'] === true && r['abierta'] !== true && this.contexto.puede('ventas.caja:desactivar'),
    },
  ];

  readonly registros = signal<Record<string, unknown>[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly modalNueva = signal(false);
  readonly modalRenombrar = signal(false);
  readonly modalApertura = signal(false);
  readonly modalCierre = signal(false);
  readonly confirmacionAbierta = signal(false);

  /** La caja sobre la que actúa el modal abierto, si hay alguno. */
  readonly seleccionada = signal<CajaApi | null>(null);

  /** El resultado del último cierre, para mostrar el arqueo. */
  readonly arqueo = signal<LineaDeArqueo[] | null>(null);
  readonly arqueoCuadra = computed(
    () => (this.arqueo() ?? []).every((linea) => linea.diferencia === 0)
  );

  private originales: CajaApi[] = [];

  /** Los establecimientos que el usuario alcanza, tal como los da el contexto. */
  readonly opcionesEstablecimiento = computed<OpcionDesplegable[]>(() =>
    this.contexto.establecimientos().map((e) => ({
      valor: String(e.id),
      etiqueta: `${e.detalle ?? ''} · ${e.nombre}`.replace(/^ · /, ''),
    }))
  );

  formularioNueva = this.constructorFormulario.nonNullable.group({
    codigo: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9-]{1,20}$/)]],
    nombre: ['', [Validators.required]],
    sucursalId: ['', [Validators.required]],
  });

  formularioRenombrar = this.constructorFormulario.nonNullable.group({
    nombre: ['', [Validators.required]],
  });

  formularioApertura = this.constructorFormulario.nonNullable.group({
    montoInicial: [0, [Validators.required, Validators.min(0)]],
  });

  /** Un control por forma de pago; lo que se deja vacío se declara como cero. */
  formularioCierre = this.constructorFormulario.group({
    EFECTIVO: [null as number | null, [Validators.min(0)]],
    TARJETA: [null as number | null, [Validators.min(0)]],
    TRANSFERENCIA: [null as number | null, [Validators.min(0)]],
    BILLETERA_DIGITAL: [null as number | null, [Validators.min(0)]],
  });

  constructor() {
    void this.cargar();
  }

  recargar(): void {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const cajas = await this.api.cajas();
      this.originales = cajas;
      // La barra superior comparte esta lista: no tiene por qué pedirla otra vez.
      this.cajaActiva.reemplazar(cajas);

      const nombreDeLocal = new Map(
        this.contexto.establecimientos().map((e) => [String(e.id), e.nombre])
      );
      this.registros.set(
        cajas.map((caja) => ({
          id: caja.id,
          codigo: caja.codigo,
          nombre: caja.nombre,
          establecimiento: nombreDeLocal.get(caja.sucursalId) ?? 'Establecimiento',
          apertura: caja.sesionAbierta
            ? `Abierta ${formatearHora(caja.sesionAbierta.abiertaEn)} con ${formatearImporte(caja.sesionAbierta.montoInicial)}`
            : '—',
          estado: !caja.activa ? 'Inactiva' : caja.sesionAbierta ? 'Abierta' : 'Cerrada',
          activa: caja.activa,
          abierta: caja.sesionAbierta !== null,
        }))
      );
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar las cajas.'));
    } finally {
      this.cargando.set(false);
    }
  }

  private cajaDe(fila: Record<string, unknown>): CajaApi | null {
    return this.originales.find((c) => c.id === fila['id']) ?? null;
  }

  // ── Alta ────────────────────────────────────────────────────────────────

  abrirNueva(): void {
    // El establecimiento activo de la barra, como valor inicial: es el sitio
    // donde el usuario está trabajando y casi siempre el que quiere.
    const activo = this.contexto.establecimientoActivo();
    this.formularioNueva.reset({ codigo: '', nombre: '', sucursalId: activo ? String(activo.id) : '' });
    this.modalNueva.set(true);
  }

  readonly crear = accionConEstado(async () => {
    if (this.formularioNueva.invalid) {
      this.formularioNueva.markAllAsTouched();
      throw new Error('Formulario incompleto');
    }
    const valores = this.formularioNueva.getRawValue();
    try {
      const creada = await this.api.crearCaja(valores);
      this.modalNueva.set(false);
      await this.cargar();
      this.avisos.exito(`${creada.codigo} · ${creada.nombre}`, 'Caja registrada');
    } catch (fallo: unknown) {
      repartirFallo(fallo, this.formularioNueva, this.avisos, 'No se pudo registrar la caja.');
      throw fallo;
    }
  });

  // ── Nombre y estado ─────────────────────────────────────────────────────

  pedirRenombrar(fila: Record<string, unknown>): void {
    const caja = this.cajaDe(fila);
    if (!caja) {
      return;
    }
    this.seleccionada.set(caja);
    this.formularioRenombrar.reset({ nombre: caja.nombre });
    this.modalRenombrar.set(true);
  }

  readonly renombrar = accionConEstado(async () => {
    const caja = this.seleccionada();
    if (!caja) {
      return;
    }
    if (this.formularioRenombrar.invalid) {
      this.formularioRenombrar.markAllAsTouched();
      throw new Error('Formulario incompleto');
    }
    try {
      await this.api.renombrarCaja(caja.id, this.formularioRenombrar.getRawValue().nombre);
      this.modalRenombrar.set(false);
      await this.cargar();
      this.avisos.exito('El nombre de la caja se actualizó.');
    } catch (fallo: unknown) {
      repartirFallo(fallo, this.formularioRenombrar, this.avisos, 'No se pudo cambiar el nombre.');
      throw fallo;
    }
  });

  pedirDesactivacion(fila: Record<string, unknown>): void {
    const caja = this.cajaDe(fila);
    if (!caja) {
      return;
    }
    this.seleccionada.set(caja);
    this.confirmacionAbierta.set(true);
  }

  readonly desactivar = accionConEstado(async () => {
    const caja = this.seleccionada();
    if (!caja) {
      return;
    }
    try {
      await this.api.cambiarEstadoCaja(caja.id, false);
      this.confirmacionAbierta.set(false);
      this.seleccionada.set(null);
      await this.cargar();
      this.avisos.exito(`${caja.nombre} ya no admite aperturas.`, 'Caja desactivada');
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo desactivar la caja.'));
      throw fallo;
    }
  });

  cancelarDesactivacion(): void {
    this.confirmacionAbierta.set(false);
    this.seleccionada.set(null);
  }

  async reactivar(fila: Record<string, unknown>): Promise<void> {
    try {
      await this.api.cambiarEstadoCaja(String(fila['id']), true);
      await this.cargar();
      this.avisos.exito(`${fila['nombre']} vuelve a admitir aperturas.`, 'Caja reactivada');
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo reactivar la caja.'));
    }
  }

  // ── Sesiones ────────────────────────────────────────────────────────────

  pedirApertura(fila: Record<string, unknown>): void {
    const caja = this.cajaDe(fila);
    if (!caja) {
      return;
    }
    this.seleccionada.set(caja);
    this.formularioApertura.reset({ montoInicial: 0 });
    this.modalApertura.set(true);
  }

  readonly abrir = accionConEstado(async () => {
    const caja = this.seleccionada();
    if (!caja) {
      return;
    }
    if (this.formularioApertura.invalid) {
      this.formularioApertura.markAllAsTouched();
      throw new Error('Formulario incompleto');
    }
    try {
      const sesion = await this.api.abrirCaja(
        caja.id,
        Number(this.formularioApertura.getRawValue().montoInicial)
      );
      this.modalApertura.set(false);
      await this.cargar();
      this.avisos.exito(
        `${caja.nombre} abierta con ${formatearImporte(sesion.montoInicial)}.`,
        'Caja abierta'
      );
    } catch (fallo: unknown) {
      repartirFallo(fallo, this.formularioApertura, this.avisos, 'No se pudo abrir la caja.');
      throw fallo;
    }
  });

  pedirCierre(fila: Record<string, unknown>): void {
    const caja = this.cajaDe(fila);
    if (!caja?.sesionAbierta) {
      return;
    }
    this.seleccionada.set(caja);
    this.formularioCierre.reset();
    this.arqueo.set(null);
    this.modalCierre.set(true);
  }

  /** Lo declarado, solo con lo que se rellenó: el servidor toma lo demás como cero. */
  declaradoDelFormulario(): ImportesPorForma {
    const valores = this.formularioCierre.getRawValue();
    const declarado: ImportesPorForma = {};
    for (const forma of FORMAS_DE_PAGO) {
      const valor = valores[forma.codigo];
      if (valor !== null && valor !== undefined && String(valor) !== '') {
        declarado[forma.codigo] = Number(valor);
      }
    }
    return declarado;
  }

  readonly cerrar = accionConEstado(async () => {
    const caja = this.seleccionada();
    if (!caja?.sesionAbierta) {
      return;
    }
    if (this.formularioCierre.invalid) {
      this.formularioCierre.markAllAsTouched();
      throw new Error('Formulario incompleto');
    }
    try {
      const cerrada = await this.api.cerrarSesion(
        caja.sesionAbierta.id,
        this.declaradoDelFormulario()
      );
      this.arqueo.set(lineasDeArqueo(cerrada));
      await this.cargar();
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo cerrar la caja.'));
      throw fallo;
    }
  });

  cerrarModalCierre(): void {
    this.modalCierre.set(false);
    this.arqueo.set(null);
    this.seleccionada.set(null);
  }

  get nombreSeleccionada(): string {
    return this.seleccionada()?.nombre ?? '';
  }

  get montoInicialSeleccionada(): string {
    return formatearImporte(this.seleccionada()?.sesionAbierta?.montoInicial ?? 0);
  }

  importe(valor: number): string {
    return formatearImporte(valor);
  }

  controlDeCierre(forma: FormaDePago) {
    return this.formularioCierre.controls[forma];
  }

  ejecutarAccion(evento: { accion: string; registro: Record<string, unknown> }): void {
    switch (evento.accion) {
      case 'abrir':
        this.pedirApertura(evento.registro);
        break;
      case 'cerrar':
        this.pedirCierre(evento.registro);
        break;
      case 'renombrar':
        this.pedirRenombrar(evento.registro);
        break;
      case 'desactivar':
        this.pedirDesactivacion(evento.registro);
        break;
      case 'reactivar':
        void this.reactivar(evento.registro);
        break;
    }
  }
}

const SOLES = new Intl.NumberFormat('es-PE', {
  style: 'currency',
  currency: 'PEN',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatearImporte(valor: number): string {
  return SOLES.format(valor);
}

function formatearHora(instante: string): string {
  const fecha = new Date(instante);
  const hoy = new Date();
  const mismoDia = fecha.toDateString() === hoy.toDateString();
  const hora = fecha.toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' });
  return mismoDia
    ? `a las ${hora}`
    : `el ${fecha.toLocaleDateString('es-PE', { day: '2-digit', month: 'short' })} a las ${hora}`;
}

/** Las cuatro formas, siempre en el mismo orden, con la diferencia calculada aquí también. */
export function lineasDeArqueo(sesion: SesionCajaApi): LineaDeArqueo[] {
  return FORMAS_DE_PAGO.map((forma) => {
    const declarado = Number(sesion.declarado[forma.codigo] ?? 0);
    const calculado = Number(sesion.calculado[forma.codigo] ?? 0);
    const diferencia = sesion.diferencia[forma.codigo];
    return {
      forma: forma.nombre,
      declarado,
      calculado,
      // La del servidor manda; el cálculo local solo cubre una respuesta vieja.
      diferencia: diferencia !== undefined ? Number(diferencia) : declarado - calculado,
    };
  });
}
