import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';

/**
 * Comunicación de baja — el patrón P7 del plan de vistas.
 *
 * A diferencia del editor de documento, opera sobre un conjunto de
 * comprobantes seleccionados, no sobre uno solo: es una lista con selección
 * múltiple, motivo y confirmación.
 *
 * Solo procede dentro de los 7 días calendario desde la emisión. Pasado ese
 * plazo, la nota de crédito es el único mecanismo disponible.
 */
@Component({
  selector: 'app-comunicacion-baja',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent, ConfirmacionComponent, FormsModule],
  templateUrl: './comunicacion-baja.component.html',
})
export class ComunicacionBajaComponent {
  columnas: ColumnaTabla[] = [
    { campo: 'numero', titulo: 'Comprobante', ordenable: true, ancho: 'w-40' },
    { campo: 'cliente', titulo: 'Cliente', ordenable: true },
    { campo: 'fecha', titulo: 'Emisión', ordenable: true, ancho: 'w-32' },
    { campo: 'diasRestantes', titulo: 'Días restantes', formato: 'cantidad', ancho: 'w-36' },
    { campo: 'total', titulo: 'Total', formato: 'importe', ancho: 'w-32' },
  ];

  /** Solo aparecen los comprobantes que todavía están dentro del plazo. */
  registros = [
    { id: 302, numero: 'F001-000123', cliente: 'Distribuidora Andina S.A.C.', fecha: '05/08/2026', diasRestantes: 6, total: 1284.5 },
    { id: 303, numero: 'F001-000122', cliente: 'Constructora Pacífico S.A.', fecha: '04/08/2026', diasRestantes: 5, total: 8940.0 },
    { id: 305, numero: 'F001-000120', cliente: 'Comercial El Sol E.I.R.L.', fecha: '01/08/2026', diasRestantes: 2, total: 415.8 },
  ];

  seleccionados: unknown[] = [];
  motivo = '';
  confirmacionAbierta = false;
  intentoEnvio = false;

  get puedeComunicar(): boolean {
    return this.seleccionados.length > 0 && this.motivo.trim().length >= 5;
  }

  get errorMotivo(): string {
    if (this.intentoEnvio && this.motivo.trim().length < 5) {
      return 'Describa el motivo de la baja. Viaja en la comunicación a SUNAT.';
    }
    return '';
  }

  intentar(): void {
    this.intentoEnvio = true;
    if (this.puedeComunicar) {
      this.confirmacionAbierta = true;
    }
  }

  comunicar(): void {
    this.registros = this.registros.filter((r) => !this.seleccionados.includes(r.id));
    this.seleccionados = [];
    this.motivo = '';
    this.intentoEnvio = false;
    this.confirmacionAbierta = false;
  }
}
