import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { AccionDeFila, ColumnaTabla, TablaDatosComponent } from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { ESTADOS_SUNAT, ResumenDocumentoApi, TipoDocumentoVenta, VentasApiService } from '../../../nucleo/ventas.api.service';
import { mensajeDeError } from '../../../nucleo/errores';
import { ContextoService } from '../../../shared/services/contexto.service';

type Vista = 'NV' | 'BOLETA' | 'FACTURA' | 'NOTA_CREDITO';

const VISTAS: Record<Vista, { titulo: string; descripcion: string; plural: string; singular: string; permisoEmitir: string }> = {
  NV: {
    titulo: 'Notas de venta',
    descripcion: 'Documento interno del mostrador. No es comprobante de pago y no se declara a SUNAT; puede canjearse por boleta o factura.',
    plural: 'notas de venta',
    singular: 'nota de venta',
    permisoEmitir: 'ventas.nota_venta:registrar',
  },
  BOLETA: {
    titulo: 'Boletas',
    descripcion: 'Registradas desde el punto de venta. Quedan pendientes hasta que SUNAT las acepte.',
    plural: 'boletas',
    singular: 'boleta',
    permisoEmitir: 'ventas.comprobante:emitir',
  },
  FACTURA: {
    titulo: 'Facturas',
    descripcion: 'Registradas desde el punto de venta a clientes con RUC. Quedan pendientes hasta que SUNAT las acepte.',
    plural: 'facturas',
    singular: 'factura',
    permisoEmitir: 'ventas.comprobante:emitir',
  },
  NOTA_CREDITO: {
    titulo: 'Notas de crédito',
    descripcion:
      'Lo que anula o corrige una boleta o una factura ya aceptada. Se emiten desde la ficha del comprobante, no desde aquí.',
    plural: 'notas de crédito',
    singular: 'nota de crédito',
    // Ninguno: una nota de crédito nace de un comprobante concreto, así que
    // esta pantalla no ofrece «nueva». Pedirle a alguien que elija el
    // comprobante desde una lista vacía sería el camino largo al mismo sitio.
    permisoEmitir: '',
  },
};

/** El listado de lo emitido, uno por tipo; la ruta dice cuál. */
@Component({
  selector: 'app-lista-documentos',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent, BotonComponent],
  templateUrl: './lista-documentos.component.html',
})
export class ListaDocumentosComponent {
  private readonly api = inject(VentasApiService);
  private readonly contexto = inject(ContextoService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly vista: Vista = (this.ruta.snapshot.data['tipo'] as Vista) ?? 'NV';
  readonly textos = VISTAS[this.vista];

  readonly puedeEmitir = computed(
    () => this.textos.permisoEmitir !== '' && this.contexto.puede(this.textos.permisoEmitir)
  );

  readonly columnas: ColumnaTabla[] = [
    { campo: 'numero', titulo: 'Número', ordenable: true, ancho: 'w-40', principal: true },
    { campo: 'fecha', titulo: 'Fecha', ordenable: true, ancho: 'w-40' },
    { campo: 'cliente', titulo: 'Cliente' },
    { campo: 'total', titulo: 'Total', formato: 'importe', ordenable: true, ancho: 'w-32' },
    {
      campo: 'estado',
      titulo: 'Estado',
      ancho: 'w-40',
      formato: 'insignia',
      tono: (r) => {
        switch (r['codigoEstado']) {
          case 'EMITIDO':
          case 'ACEPTADO':
            return 'exito';
          case 'PENDIENTE':
          case 'EN_COLA':
            return 'aviso';
          case 'RECHAZADO':
          case 'ERROR_ENVIO':
            return 'error';
          default:
            return 'neutro';
        }
      },
    },
  ];
  readonly accionesDeFila: AccionDeFila[] = [{ id: 'ver', etiqueta: 'Ver', icono: 'ver' }];

  readonly registros = signal<Record<string, unknown>[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  private tipos = new Map<string, TipoDocumentoVenta>();

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const documentos: ResumenDocumentoApi[] =
        this.vista === 'NV'
          ? await this.api.notasDeVenta()
          : this.vista === 'NOTA_CREDITO'
            ? await this.api.notasDeCredito()
            : await this.api.comprobantes(this.vista);
      this.tipos = new Map(documentos.map((d) => [d.id, d.tipo]));
      this.registros.set(
        documentos.map((d) => ({
          id: d.id,
          numero: d.numeroCompleto,
          fecha: new Date(d.emitidoEn).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' }),
          cliente: d.cliente ? `${d.cliente} · ${d.clienteDocumento}` : 'Cliente varios',
          total: d.total,
          // En una boleta o factura el estado que cuenta es el de SUNAT: un
          // rechazo se tiene que ver desde el listado, no al abrir cada una.
          estado: d.estadoSunat && d.estado === 'PENDIENTE'
            ? ESTADOS_SUNAT[d.estadoSunat]
            : d.estado === 'EMITIDO' ? 'Emitido' : d.estado === 'PENDIENTE' ? 'Pendiente de SUNAT' : d.estado === 'CANJEADO' ? 'Canjeado' : 'Anulado',
          codigoEstado: d.estadoSunat && d.estado === 'PENDIENTE' ? d.estadoSunat : d.estado,
        }))
      );
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, `No se pudieron cargar las ${this.textos.plural}.`));
    } finally {
      this.cargando.set(false);
    }
  }

  recargar(): void {
    void this.cargar();
  }

  abrir(registro: Record<string, unknown>): void {
    const id = String(registro['id']);
    void this.router.navigate(['/ventas/documentos', this.tipos.get(id) ?? 'NV', id]);
  }

  ejecutarAccion(evento: { accion: string; registro: Record<string, unknown> }): void {
    if (evento.accion === 'ver') {
      this.abrir(evento.registro);
    }
  }

  nuevaVenta(): void {
    void this.router.navigate(['/ventas/punto-de-venta']);
  }
}
