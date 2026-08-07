import { Component, Input, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { PageBreadcrumbComponent } from '../../common/page-breadcrumb/page-breadcrumb.component';
import { BuscadorEntidadComponent, OpcionEntidad } from '../buscador-entidad/buscador-entidad.component';
import { EditorLineasComponent, LineaDocumento, TotalesDocumento } from '../editor-lineas/editor-lineas.component';
import { ConfirmacionComponent } from '../confirmacion/confirmacion.component';

/** Naturaleza del tercero según el documento: cliente en ventas, proveedor en compras. */
export type TipoTercero = 'cliente' | 'proveedor';

export interface ConfiguracionDocumento {
  /** Título de la pantalla. */
  titulo: string;
  /** Ruta del listado al que se vuelve. */
  rutaListado: string;

  tipoTercero: TipoTercero;
  etiquetaTercero: string;

  /** Un comprobante electrónico consume serie y correlativo; un documento interno no. */
  esComprobanteElectronico: boolean;
  /** Serie asignada, cuando aplica. */
  serie?: string;
  /** Siguiente correlativo, informativo: lo asigna el servidor al emitir. */
  siguienteNumero?: string;

  /** Solo con RUC se puede emitir factura; con DNI procede boleta. */
  exigeRuc: boolean;

  /** Texto del botón principal. */
  textoAccion: string;
  /** Advertencia mostrada antes de confirmar, cuando la acción es irreversible. */
  advertenciaAccion?: string;

  /** Fecha de vencimiento, aplicable a cotizaciones y ventas al crédito. */
  usaVencimiento: boolean;
  /** Documento de referencia, obligatorio en notas de crédito y débito. */
  usaReferencia: boolean;
  /** Motivo del catálogo SUNAT, obligatorio en notas de crédito. */
  motivos?: { codigo: string; nombre: string }[];
}

/**
 * Editor de documento — el patrón P3 del plan de vistas.
 *
 * Lo comparten catorce pantallas: cotización, preventa, boleta, factura,
 * nota de crédito, notas de pedido y compra, órdenes, liquidación y guías.
 * Comparten cerca del 80 %: elegir tercero, buscar producto, agregar línea,
 * calcular IGV según afectación y totalizar.
 *
 * Construirlo una vez y parametrizarlo es la decisión que más tiempo ahorra
 * en este frontend; duplicarlo por tipo de documento es el error más caro
 * que admite (plan de vistas §7).
 */
@Component({
  selector: 'app-editor-documento',
  imports: [
    FormsModule,
    RouterModule,
    PageBreadcrumbComponent,
    BuscadorEntidadComponent,
    EditorLineasComponent,
    ConfirmacionComponent,
  ],
  templateUrl: './editor-documento.component.html',
})
export class EditorDocumentoComponent {
  @Input({ required: true }) configuracion!: ConfiguracionDocumento;

  terceroSeleccionado: OpcionEntidad | null = null;
  fechaEmision = '';
  fechaVencimiento = '';
  moneda = 'PEN';
  formaPago = 'Contado';
  observaciones = '';
  motivoSeleccionado = '';
  documentoReferencia = '';

  lineas: LineaDocumento[] = [];
  totales: TotalesDocumento | null = null;

  confirmacionAbierta = false;
  intentoEnvio = false;

  private contadorLineas = 0;

  /** Catálogo de ejemplo. Al llegar el backend se reemplaza por una búsqueda real. */
  opcionesTercero: OpcionEntidad[] = [];
  opcionesProducto: OpcionEntidad[] = [];

  private readonly TERCEROS_CLIENTE: OpcionEntidad[] = [
    { id: 1, titulo: 'Distribuidora Andina S.A.C.', detalle: 'RUC 20512345678', extremo: 'Mayorista' },
    { id: 2, titulo: 'Comercial El Sol E.I.R.L.', detalle: 'RUC 20587654321', extremo: 'Distribuidor' },
    { id: 3, titulo: 'Rosa Quispe Mamani', detalle: 'DNI 45678912', extremo: 'Público' },
    { id: 5, titulo: 'Constructora Pacífico S.A.', detalle: 'RUC 20456789123', extremo: 'Mayorista' },
  ];

  private readonly TERCEROS_PROVEEDOR: OpcionEntidad[] = [
    { id: 1, titulo: 'Cementos Pacasmayo S.A.A.', detalle: 'RUC 20100113610', extremo: 'Crédito 30 d' },
    { id: 2, titulo: 'Corporación Aceros Arequipa S.A.', detalle: 'RUC 20100136741', extremo: 'Crédito 45 d' },
    { id: 3, titulo: 'Ferretería Central S.A.C.', detalle: 'RUC 20524089107', extremo: 'Contado' },
  ];

  private readonly PRODUCTOS: OpcionEntidad[] = [
    { id: 1, titulo: 'Cemento Portland Tipo I 42.5 kg', detalle: 'CEM-001 · BOL · stock 428', extremo: 'S/ 32.50' },
    { id: 2, titulo: 'Cemento Portland Tipo V 42.5 kg', detalle: 'CEM-005 · BOL · stock 96', extremo: 'S/ 38.90' },
    { id: 3, titulo: 'Fierro corrugado 1/2" x 9 m', detalle: 'FIE-012 · UND · stock 1204', extremo: 'S/ 48.00' },
    { id: 5, titulo: 'Ladrillo King Kong 18 huecos', detalle: 'LAD-018 · UND · stock 15600', extremo: 'S/ 1.20' },
    { id: 6, titulo: 'Alambre negro nº 16', detalle: 'ALA-016 · KG · sin stock', extremo: 'S/ 6.80', deshabilitada: true },
  ];

  ngOnInit(): void {
    const hoy = new Date();
    this.fechaEmision = hoy.toISOString().slice(0, 10);
    this.opcionesTercero =
      this.configuracion.tipoTercero === 'cliente' ? this.TERCEROS_CLIENTE : this.TERCEROS_PROVEEDOR;
    this.opcionesProducto = this.PRODUCTOS;
  }

  buscarTercero(termino: string): void {
    const base =
      this.configuracion.tipoTercero === 'cliente' ? this.TERCEROS_CLIENTE : this.TERCEROS_PROVEEDOR;
    this.opcionesTercero = base.filter(
      (o) =>
        o.titulo.toLowerCase().includes(termino.toLowerCase()) ||
        (o.detalle ?? '').toLowerCase().includes(termino.toLowerCase())
    );
  }

  buscarProducto(termino: string): void {
    this.opcionesProducto = this.PRODUCTOS.filter(
      (o) =>
        o.titulo.toLowerCase().includes(termino.toLowerCase()) ||
        (o.detalle ?? '').toLowerCase().includes(termino.toLowerCase())
    );
  }

  elegirTercero(opcion: OpcionEntidad): void {
    this.terceroSeleccionado = opcion;
  }

  agregarProducto(opcion: OpcionEntidad): void {
    this.contadorLineas += 1;
    const precio = Number((opcion.extremo ?? '0').replace(/[^\d.]/g, '')) || 0;
    const unidad = (opcion.detalle ?? '').split('·')[1]?.trim() ?? 'NIU';

    this.lineas = [
      ...this.lineas,
      {
        id: 'l' + this.contadorLineas,
        productoId: opcion.id,
        codigo: (opcion.detalle ?? '').split('·')[0]?.trim(),
        descripcion: opcion.titulo,
        unidad,
        cantidad: 1,
        // El precio del catálogo incluye IGV; el editor trabaja con el valor sin impuesto.
        valorUnitario: Number((precio / 1.18).toFixed(6)),
        descuento: 0,
        afectacion: 'GRAVADO',
      },
    ];
  }

  /** El documento con RUC obligatorio no admite un tercero identificado con DNI. */
  get terceroEsValido(): boolean {
    if (!this.terceroSeleccionado) {
      return false;
    }
    if (!this.configuracion.exigeRuc) {
      return true;
    }
    return (this.terceroSeleccionado.detalle ?? '').includes('RUC');
  }

  get errorTercero(): string {
    if (this.intentoEnvio && !this.terceroSeleccionado) {
      return `Seleccione un ${this.configuracion.etiquetaTercero.toLowerCase()}.`;
    }
    if (this.terceroSeleccionado && !this.terceroEsValido) {
      return 'Este documento requiere un tercero con RUC. Con DNI corresponde emitir boleta.';
    }
    return '';
  }

  get puedeEmitir(): boolean {
    const referenciaOk = !this.configuracion.usaReferencia || Boolean(this.documentoReferencia && this.motivoSeleccionado);
    return this.terceroEsValido && this.lineas.length > 0 && referenciaOk;
  }

  intentarAccion(): void {
    this.intentoEnvio = true;
    if (!this.puedeEmitir) {
      return;
    }
    if (this.configuracion.advertenciaAccion) {
      this.confirmacionAbierta = true;
      return;
    }
    this.ejecutar();
  }

  ejecutar(): void {
    this.confirmacionAbierta = false;
  }
}
