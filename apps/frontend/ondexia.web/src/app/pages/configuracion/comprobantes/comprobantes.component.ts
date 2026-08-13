import { Component, inject, signal } from '@angular/core';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import {
  ConfiguracionApiService,
  TipoComprobanteApi,
  mensajeDeError,
} from '../../../nucleo/configuracion.api.service';

/**
 * Qué tipos de comprobante emite la empresa.
 *
 * <h2>Solo esto, y a propósito</h2>
 *
 * <p>La maqueta original prometía además plantillas de impresión, numeración por
 * defecto y el estado de cada comprobante ante SUNAT. Nada de eso está: emitir,
 * anular y consultar el estado son del módulo de ventas y llegan con C1 (plan 07
 * §1.1, donde esta pantalla figura como <em>parcial</em>).
 *
 * <p>Dejar los controles pintados «para completarlos luego» haría que el cliente
 * los configure y descubra después que no hacían nada.
 *
 * <h2>Lo que sí hace, hace efecto</h2>
 *
 * <p>Apagar un tipo impide crear series de ese tipo, y el servidor lo rechaza.
 * Una pantalla de configuración que no cambia el comportamiento de nada es una
 * pantalla que se rellena para nada.
 *
 * <p>Por lo mismo, un tipo con series activas no se deja apagar: la pantalla
 * diría una cosa y el sistema haría otra.
 */
@Component({
  selector: 'app-comprobantes',
  imports: [EncabezadoPaginaComponent],
  templateUrl: './comprobantes.component.html',
})
export class ComprobantesComponent {
  private readonly api = inject(ConfiguracionApiService);

  readonly tipos = signal<TipoComprobanteApi[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  /** Código del tipo cuyo interruptor está en vuelo, para deshabilitarlo. */
  readonly enCurso = signal<string | null>(null);

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.tipos.set(await this.api.tiposComprobante());
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar los tipos de comprobante.'));
    } finally {
      this.cargando.set(false);
    }
  }

  /**
   * Un tipo con series vivas no se puede apagar. Se deshabilita el interruptor
   * en vez de dejar que falle: el motivo cabe en la propia fila, y así se lee
   * antes de intentarlo en lugar de después.
   */
  bloqueado(tipo: TipoComprobanteApi): boolean {
    return tipo.emite && tipo.seriesActivas > 0;
  }

  async alternar(tipo: TipoComprobanteApi): Promise<void> {
    if (this.bloqueado(tipo)) {
      return;
    }

    this.enCurso.set(tipo.codigo);
    this.error.set(null);
    try {
      await this.api.cambiarEstadoTipoComprobante(tipo.codigo, !tipo.emite);
      await this.cargar();
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudo cambiar el tipo de comprobante.'));
    } finally {
      this.enCurso.set(null);
    }
  }
}
