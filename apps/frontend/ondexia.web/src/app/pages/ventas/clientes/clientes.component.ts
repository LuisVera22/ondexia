import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import {
  AccionDeFila,
  ColumnaTabla,
  OrdenTabla,
  TablaDatosComponent,
} from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import {
  DesplegableComponent,
  OpcionDesplegable,
} from '../../../shared/components/comunes/desplegable/desplegable.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { ClienteApi, VentasApiService } from '../../../nucleo/ventas.api.service';
import { mensajeDeError } from '../../../nucleo/errores';
import { ContextoService } from '../../../shared/services/contexto.service';

/**
 * Los adquirentes identificados (doc 12 §4.3). El «cliente sin documento» de la
 * boleta no está aquí: es la ausencia de cliente en la venta.
 */
@Component({
  selector: 'app-clientes',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent, FormsModule, DesplegableComponent, BotonComponent],
  templateUrl: './clientes.component.html',
})
export class ClientesComponent {
  private readonly api = inject(VentasApiService);
  private readonly contexto = inject(ContextoService);
  private readonly router = inject(Router);

  readonly opcionesTipo: OpcionDesplegable[] = [
    { valor: '', etiqueta: 'Todos' },
    { valor: 'RUC', etiqueta: 'RUC' },
    { valor: 'DNI', etiqueta: 'DNI' },
    { valor: 'OTRO', etiqueta: 'Carné o pasaporte' },
  ];

  termino = '';
  tipoFiltro = '';

  orden: OrdenTabla | null = { campo: 'nombre', direccion: 'asc' };
  pagina = 1;
  readonly tamanoPagina = 10;
  readonly accionesDeFila: AccionDeFila[] = [{ id: 'ver', etiqueta: 'Ver', icono: 'ver' }];

  readonly columnas: ColumnaTabla[] = [
    { campo: 'tipoDocumento', titulo: 'Tipo', ancho: 'w-28' },
    { campo: 'numeroDocumento', titulo: 'Documento', ordenable: true, ancho: 'w-36' },
    { campo: 'nombre', titulo: 'Cliente', ordenable: true, principal: true },
    { campo: 'correo', titulo: 'Correo' },
    {
      campo: 'estado',
      titulo: 'Estado',
      ancho: 'w-32',
      formato: 'insignia',
      tono: (registro) =>
        registro['activo'] !== true ? 'neutro' : registro['verificado'] === true ? 'exito' : 'aviso',
    },
  ];

  readonly puedeRegistrar = computed(() => this.contexto.puede('ventas.cliente:registrar'));

  private readonly todos = signal<ClienteApi[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.todos.set(await this.api.clientes());
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar los clientes.'));
    } finally {
      this.cargando.set(false);
    }
  }

  recargar(): void {
    void this.cargar();
  }

  get registros(): Record<string, unknown>[] {
    const termino = this.termino.trim().toLowerCase();
    return this.todos()
      .filter((c) => {
        const coincideTermino =
          !termino || c.nombre.toLowerCase().includes(termino) || c.numeroDocumento.includes(termino);
        const coincideTipo =
          !this.tipoFiltro ||
          (this.tipoFiltro === 'OTRO'
            ? c.tipoDocumento !== 'RUC' && c.tipoDocumento !== 'DNI'
            : c.tipoDocumento === this.tipoFiltro);
        return coincideTermino && coincideTipo;
      })
      .map((c) => ({
        id: c.id,
        tipoDocumento: c.tipoDocumentoNombre,
        numeroDocumento: c.numeroDocumento,
        nombre: c.nombre,
        correo: c.correo ?? '',
        // Un RUC sin verificar se dice: la factura de la iteracion 4 lo volvera
        // a comprobar, y conviene que no sorprenda entonces.
        estado: !c.activo
          ? 'Inactivo'
          : c.tipoDocumento === 'RUC' && !c.verificadoEn
            ? 'RUC sin verificar'
            : 'Activo',
        activo: c.activo,
        verificado: c.tipoDocumento !== 'RUC' || c.verificadoEn !== null,
      }));
  }

  get hayFiltrosActivos(): boolean {
    return Boolean(this.termino || this.tipoFiltro);
  }

  limpiarFiltros(): void {
    this.termino = '';
    this.tipoFiltro = '';
  }

  abrirFicha(registro: Record<string, unknown>): void {
    void this.router.navigate(['/ventas/clientes', registro['id']]);
  }

  nuevo(): void {
    void this.router.navigate(['/ventas/clientes', 'nuevo']);
  }

  ejecutarAccion(evento: { accion: string; registro: Record<string, unknown> }): void {
    if (evento.accion === 'ver') {
      this.abrirFicha(evento.registro);
    }
  }
}
