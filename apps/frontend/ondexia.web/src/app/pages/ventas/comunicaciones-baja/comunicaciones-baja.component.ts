import { Component, computed, inject, signal } from '@angular/core';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { accionConEstado } from '../../../shared/components/comunes/boton/estado-accion';
import { ComunicacionDeBajaApi, ESTADOS_SUNAT, VentasApiService } from '../../../nucleo/ventas.api.service';
import { mensajeDeError } from '../../../nucleo/errores';
import { AvisosService } from '../../../shared/services/avisos.service';
import { ContextoService } from '../../../shared/services/contexto.service';

/**
 * Las comunicaciones de baja y en qué punto está cada una (doc 13 §6).
 *
 * <p>No hay «nueva» aquí: una baja se pide desde la factura concreta, igual que
 * la nota de crédito. Esta pantalla es para seguirlas, porque su respuesta no
 * llega en el momento —SUNAT devuelve un ticket y el veredicto viene después—.
 *
 * <p>El servidor sincroniza al listar, así que «Actualizar» es literalmente
 * volver a pedir: no hay nada que orquestar desde el navegador.
 */
@Component({
  selector: 'app-comunicaciones-baja',
  imports: [EncabezadoPaginaComponent, BotonComponent],
  templateUrl: './comunicaciones-baja.component.html',
})
export class ComunicacionesBajaComponent {
  private readonly api = inject(VentasApiService);
  private readonly avisos = inject(AvisosService);
  private readonly contexto = inject(ContextoService);

  readonly comunicaciones = signal<ComunicacionDeBajaApi[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly puedeEnviar = computed(() => this.contexto.puede('ventas.comunicacion_baja:enviar'));

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.comunicaciones.set(await this.api.comunicacionesDeBaja());
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar las comunicaciones de baja.'));
    } finally {
      this.cargando.set(false);
    }
  }

  readonly actualizar = accionConEstado(async () => this.cargar());

  readonly reintentar = accionConEstado(async (): Promise<void> => {
    const id = this.reintentando();
    if (!id) {
      return;
    }
    try {
      await this.api.reintentarBaja(id);
      await this.cargar();
      this.avisos.exito('La comunicación volvió a la cola con un número nuevo del día.');
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo volver a enviar.'));
      throw fallo;
    } finally {
      this.reintentando.set(null);
    }
  });

  /** Cuál se está reenviando: el botón vive en cada fila y la acción es una. */
  readonly reintentando = signal<string | null>(null);

  pedirReintento(id: string): void {
    this.reintentando.set(id);
    void this.reintentar.ejecutar();
  }

  texto(estado: string): string {
    return ESTADOS_SUNAT[estado as keyof typeof ESTADOS_SUNAT] ?? estado;
  }

  tono(estado: string): string {
    switch (estado) {
      case 'ACEPTADO':
        return 'text-success-600';
      case 'RECHAZADO':
      case 'ERROR_ENVIO':
        return 'text-error-500';
      default:
        return 'text-warning-600';
    }
  }

  fecha(dia: string): string {
    return new Date(dia + 'T00:00:00').toLocaleDateString('es-PE', { dateStyle: 'medium' });
  }
}
