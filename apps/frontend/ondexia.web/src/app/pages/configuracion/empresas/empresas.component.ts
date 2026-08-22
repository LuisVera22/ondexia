import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import {
  ColumnaTabla,
  TablaDatosComponent,
} from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import {
  ConfiguracionApiService,
  Empresa,
  mensajeDeError,
} from '../../../nucleo/configuracion.api.service';
import { ContextoService } from '../../../shared/services/contexto.service';

/**
 * Las empresas que el usuario alcanza, como listado.
 *
 * <p>Antes esta entrada del menú abría directamente el formulario de la empresa
 * activa. Con una sola empresa daba igual; con dos, la pantalla decía «Datos de
 * la empresa» sin decir de cuál, y la única forma de ver la otra era cambiarla
 * en el selector de contexto — que es una acción con consecuencias, porque
 * cambia los permisos y todo lo que se ve en el resto del sistema.
 *
 * <h2>Se listan las asignaciones, no las empresas de la cuenta</h2>
 *
 * <p>Es la misma regla que el selector de contexto, y la impone el servidor.
 * Dos usuarios de la misma cuenta pueden alcanzar empresas distintas; listar
 * aquí las de la cuenta entera enseñaría lo que aquel selector oculta.
 *
 * <h2>La lista no sale del contexto, aunque lo parezca</h2>
 *
 * <p>{@code ContextoService.empresas()} trae los mismos identificadores y
 * costaría cero, pero solo el nombre y el RUC: ni el domicilio, ni el modo
 * SUNAT, ni si la empresa está activa. Media tabla quedaría vacía.
 */
@Component({
  selector: 'app-empresas',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent],
  templateUrl: './empresas.component.html',
})
export class EmpresasComponent {
  private readonly api = inject(ConfiguracionApiService);
  private readonly contexto = inject(ContextoService);
  private readonly router = inject(Router);

  readonly columnas: ColumnaTabla[] = [
    { campo: 'ruc', titulo: 'RUC', ordenable: true, ancho: 'w-36' },
    { campo: 'razonSocial', titulo: 'Razón social', ordenable: true },
    { campo: 'nombreComercial', titulo: 'Nombre comercial' },
    { campo: 'domicilioFiscal', titulo: 'Domicilio fiscal' },
    { campo: 'estado', titulo: 'Estado', ancho: 'w-28' },
  ];

  readonly registros = signal<Record<string, unknown>[]>([]);
  readonly cargando = signal(true);

  /**
   * El fallo al cargar el listado, que se pinta en lugar de la tabla.
   *
   * <p>No va a un aviso flotante: un aviso deja la tabla vacía debajo sin
   * explicar por qué está vacía, y a los cuatro segundos desaparece dejando una
   * pantalla que parece decir «no tienes empresas».
   */
  readonly error = signal<string | null>(null);

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const empresas = await this.api.empresas();
      const activaId = this.contexto.empresaActiva()?.id;

      this.registros.set(
        empresas.map((empresa: Empresa) => ({
          id: empresa.id,
          ruc: empresa.ruc,
          razonSocial: empresa.razonSocial,
          nombreComercial: empresa.nombreComercial ?? '—',
          domicilioFiscal: empresa.domicilioFiscal,
          estado: empresa.activa ? 'Activa' : 'Inactiva',
          // La empresa sobre la que se está trabajando: es la única que se
          // puede editar, y conviene que se vea antes de entrar a la ficha.
          enUso: String(empresa.id) === String(activaId),
        }))
      );
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar las empresas.'));
    } finally {
      this.cargando.set(false);
    }
  }

  abrirFicha(fila: Record<string, unknown>): void {
    void this.router.navigate(['/configuracion/empresas', fila['id']]);
  }
}
