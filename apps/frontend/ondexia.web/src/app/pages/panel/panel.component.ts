import { Component, computed, inject, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { EncabezadoPaginaComponent } from '../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { BotonComponent } from '../../shared/components/comunes/boton/boton.component';
import { accionConEstado } from '../../shared/components/comunes/boton/estado-accion';
import { PanelApi, PanelApiService } from '../../nucleo/panel.api.service';
import { ESTADOS_SUNAT, EstadoSunat } from '../../nucleo/ventas.api.service';
import { mensajeDeError } from '../../nucleo/errores';

/**
 * La portada: tres cifras y una lista.
 *
 * <h2>Qué se quitó, y por qué</h2>
 *
 * <p>Aquí había siete indicadores con variación respecto al periodo anterior,
 * un gráfico de barras por semana, mes y año, una banda de avisos y un saludo
 * por la hora del día. Ninguna cifra salía de la base: estaban escritas en el
 * componente. Un panel que inventa es peor que no tener panel, porque quien lo
 * mira decide con él.
 *
 * <p>Lo que queda responde a lo que alguien pregunta al abrir la aplicación por
 * la mañana en una tienda (doc 12 §7.2): si su caja está abierta, cuánto lleva
 * vendido hoy, y si hay algo atascado ante SUNAT. El gráfico vuelve cuando haya
 * meses que comparar.
 *
 * <h2>Ausente no es cero</h2>
 *
 * <p>El servidor devuelve `null` en el bloque que el usuario no puede consultar
 * y un cero cuando puede y no hay nada. La pantalla los distingue: lo primero
 * no se pinta, lo segundo sí, porque «hoy no has vendido» es información.
 *
 * <h2>La fecha es la del servidor</h2>
 *
 * <p>Y no la del navegador. Un equipo con la hora corrida —o alguien
 * consultando desde otro huso— vería «hoy» en un día distinto al que la venta
 * lleva impresa.
 */
@Component({
  selector: 'app-panel',
  imports: [RouterModule, EncabezadoPaginaComponent, BotonComponent],
  templateUrl: './panel.component.html',
})
export class PanelComponent {
  private readonly api = inject(PanelApiService);

  readonly datos = signal<PanelApi | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.datos.set(await this.api.consultar());
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar las cifras del día.'));
    } finally {
      this.cargando.set(false);
    }
  }

  readonly actualizar = accionConEstado(async () => this.cargar());

  /** «lunes 8 de septiembre de 2026» a partir del día que dijo el servidor. */
  readonly fechaLarga = computed(() => {
    const fecha = this.datos()?.fecha;
    if (!fecha) {
      return '';
    }
    // Se parte la cadena en vez de `new Date(fecha)`: el constructor
    // interpreta «2026-09-08» como medianoche UTC y en Lima eso es el día 7.
    const [anio, mes, dia] = fecha.split('-').map(Number);
    return new Intl.DateTimeFormat('es-PE', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    }).format(new Date(anio, mes - 1, dia));
  });

  readonly cajas = computed(() => this.datos()?.cajasAbiertas ?? null);
  readonly ventas = computed(() => this.datos()?.ventas ?? null);
  readonly porAtender = computed(() => this.datos()?.comprobantesPorAtender ?? null);

  /** True cuando el usuario no alcanza ninguno de los tres bloques. */
  readonly sinNadaQueVer = computed(
    () => !this.cargando() && !this.cajas() && !this.ventas() && !this.porAtender()
  );

  importe(valor: number): string {
    return new Intl.NumberFormat('es-PE', {
      style: 'currency',
      currency: 'PEN',
      minimumFractionDigits: 2,
    }).format(valor);
  }

  hora(instante: string): string {
    return new Intl.DateTimeFormat('es-PE', {
      timeZone: 'America/Lima',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(new Date(instante));
  }

  nombreDeEstado(estado: string): string {
    return ESTADOS_SUNAT[estado as EstadoSunat] ?? estado;
  }

  /**
   * La ficha del documento, que es donde se reintenta o se da de baja. El tipo
   * llega del servidor como código del catálogo 01, que es lo que la ruta pide.
   */
  rutaDelComprobante(tipo: string, documentoId: string): string[] {
    return ['/ventas/documentos', tipo, documentoId];
  }

  /**
   * Un comprobante en cola está en camino; uno rechazado o con error de envío
   * espera a alguien. Solo el segundo grupo se pinta como aviso.
   */
  exigeAtencion(estado: string): boolean {
    return estado === 'RECHAZADO' || estado === 'ERROR_ENVIO';
  }
}
