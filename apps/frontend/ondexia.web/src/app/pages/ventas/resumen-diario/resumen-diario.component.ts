import { Component } from '@angular/core';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { TablaDatosComponent, AccionDeFila,
  ColumnaTabla } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { EstadoComprobante } from '../../../shared/components/comunes/estado-comprobante/estado-comprobante.component';

/** Extiende Record para poder pasarse a tabla-datos: una interface con
 * nombre no recibe firma de indice implicita en TypeScript. */
interface Resumen extends Record<string, unknown> {
  id: number;
  correlativo: string;
  fechaReferencia: string;
  boletas: number;
  total: number;
  ticket: string;
  estado: EstadoComprobante;
  estadoTexto: string;
}

/**
 * Resumen diario de boletas.
 *
 * Es el flujo asíncrono en dos tiempos del DTE (F-03): el envío no devuelve
 * el CDR sino un ticket que se consulta después. Por eso la interfaz
 * muestra el ticket y el estado por separado — el usuario tiene que poder
 * entender que «enviado» todavía no significa «aceptado».
 */
@Component({
  selector: 'app-resumen-diario',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent],
  templateUrl: './resumen-diario.component.html',
})
export class ResumenDiarioComponent {
  readonly accionesDeFila: AccionDeFila[] = [
    {
      id: 'ticket',
      etiqueta: 'Consultar el ticket',
      icono: 'reactivar',
      disponible: (registro) => registro['estado'] === 'ENVIADO',
    },
  ];

  columnas: ColumnaTabla[] = [
    { campo: 'correlativo', titulo: 'Resumen', ordenable: true, ancho: 'w-44', principal: true },
    { campo: 'fechaReferencia', titulo: 'Fecha de las boletas', ordenable: true, ancho: 'w-44' },
    { campo: 'boletas', titulo: 'Boletas', formato: 'cantidad', ancho: 'w-28' },
    { campo: 'total', titulo: 'Total declarado', formato: 'importe', ancho: 'w-40' },
    { campo: 'ticket', titulo: 'Ticket SUNAT', ancho: 'w-44' },
    // El estado estaba metido en la columna de acciones, con la insignia de
    // colores. Un estado no es una accion: se lee, no se pulsa, y ahi obligaba a
    // ensanchar una columna que solo deberia contener el boton del menu.
    { campo: 'estadoTexto', titulo: 'Estado', ancho: 'w-32' },
  ];

  registros: Resumen[] = [
    { id: 1, correlativo: 'RC-20260806-1', fechaReferencia: '06/08/2026', boletas: 2, total: 246.3, ticket: '', estado: 'PENDIENTE', estadoTexto: 'Pendiente' },
    { id: 2, correlativo: 'RC-20260805-1', fechaReferencia: '05/08/2026', boletas: 14, total: 1842.7, ticket: '20260805094512', estado: 'ENVIADO', estadoTexto: 'Enviado' },
    { id: 3, correlativo: 'RC-20260804-1', fechaReferencia: '04/08/2026', boletas: 9, total: 976.4, ticket: '20260804091203', estado: 'ACEPTADO', estadoTexto: 'Aceptado' },
  ];

  get boletasPendientes(): number {
    return this.registros.filter((r) => r.estado === 'PENDIENTE').reduce((s, r) => s + r.boletas, 0);
  }

  get hayEnProceso(): boolean {
    return this.registros.some((r) => r.estado === 'ENVIADO');
  }

  ejecutarAccion(_evento: { accion: string; registro: Record<string, unknown> }): void {
    // Sin efecto todavia: esta pantalla trabaja con datos de ejemplo.
  }
}
