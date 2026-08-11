import { Injectable, computed, signal } from '@angular/core';
import { OpcionContexto } from '../components/comunes/selector-contexto/selector-contexto.component';

/**
 * Contexto de trabajo activo: empresa y establecimiento.
 *
 * Vive en un servicio y no en el encabezado porque no es solo un dato de
 * presentación: el establecimiento determina la serie del comprobante y el
 * almacén que descarga existencias (documento 04 §3.1). Los editores de
 * documento tendrán que leerlo, no solo el menú.
 *
 * Los datos son de ejemplo mientras no exista backend. Al conectarlo se
 * reemplaza la carga inicial y ningún consumidor cambia.
 */
@Injectable({ providedIn: 'root' })
export class ContextoService {
  private readonly _empresas = signal<OpcionContexto[]>([
    { id: 1, nombre: 'Wirbi S.A.C.', detalle: 'RUC 20512345678' },
    { id: 2, nombre: 'Comercial Andina S.A.C.', detalle: 'RUC 20587654321' },
  ]);

  private readonly _establecimientos = signal<OpcionContexto[]>([
    { id: 1, nombre: 'Principal', detalle: '0000 · Av. Javier Prado 1234' },
    { id: 2, nombre: 'Miraflores', detalle: '0001 · Av. Larco 456' },
    { id: 3, nombre: 'Depósito Ate', detalle: '0002 · Carretera Central Km 8' },
  ]);

  private readonly _empresaActiva = signal<OpcionContexto | null>(this._empresas()[0]);
  private readonly _establecimientoActivo = signal<OpcionContexto | null>(
    this._establecimientos()[0]
  );

  readonly empresas = this._empresas.asReadonly();
  readonly establecimientos = this._establecimientos.asReadonly();
  readonly empresaActiva = this._empresaActiva.asReadonly();
  readonly establecimientoActivo = this._establecimientoActivo.asReadonly();

  /** Serie que corresponde al establecimiento activo, según su código. */
  readonly prefijoSerie = computed(() => {
    const codigo = this._establecimientoActivo()?.detalle?.split('·')[0]?.trim() ?? '0000';
    return codigo;
  });

  cambiarEmpresa(empresa: OpcionContexto): void {
    this._empresaActiva.set(empresa);
    // Al cambiar de empresa el establecimiento anterior deja de ser válido:
    // pertenece a otro RUC. Se vuelve al primero de la nueva empresa.
    this._establecimientoActivo.set(this._establecimientos()[0] ?? null);
  }

  cambiarEstablecimiento(establecimiento: OpcionContexto): void {
    this._establecimientoActivo.set(establecimiento);
  }
}
