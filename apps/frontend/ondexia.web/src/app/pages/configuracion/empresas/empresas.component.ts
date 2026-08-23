import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import {
  AccionDeFila,
  ColumnaTabla,
  TablaDatosComponent,
} from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import {
  ConfiguracionApiService,
  CupoDeEmpresas,
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
 *
 * <h2>El botón de alta depende del plan, y se consulta</h2>
 *
 * <p>Aquí decía que no había botón porque el alta «se hace desde la
 * administración de la cuenta». Allí tampoco estaba: no existía {@code POST} en
 * ninguna parte, así que una cuenta del plan de dos empresas no podía crear la
 * segunda por ningún camino.
 *
 * <p>Ahora está, y su estado sale de {@code GET /empresas/cupo}. Se pide el cupo
 * en lugar de deducirlo de la longitud del listado, y la diferencia importa: el
 * listado son las empresas que <em>este usuario</em> alcanza, y el límite es de
 * las de la cuenta. Alguien con acceso a una de tres vería sitio donde no lo
 * hay.
 */
@Component({
  selector: 'app-empresas',
  imports: [EncabezadoPaginaComponent, TablaDatosComponent, BotonComponent],
  templateUrl: './empresas.component.html',
})
export class EmpresasComponent {
  private readonly api = inject(ConfiguracionApiService);
  private readonly contexto = inject(ContextoService);
  private readonly router = inject(Router);
  readonly accionesDeFila: AccionDeFila[] = [
    { id: 'ver', etiqueta: 'Ver', icono: 'ver' },
  ];


  readonly columnas: ColumnaTabla[] = [
    { campo: 'ruc', titulo: 'RUC', ordenable: true, ancho: 'w-36' },
    { campo: 'razonSocial', titulo: 'Razón social', ordenable: true, principal: true },
    { campo: 'nombreComercial', titulo: 'Nombre comercial' },
    { campo: 'domicilioFiscal', titulo: 'Domicilio fiscal' },
    {
      campo: 'estado',
      titulo: 'Estado',
      ancho: 'w-32',
      formato: 'insignia',
      // Tres casos y no dos: la empresa sobre la que se trabaja se distingue
      // de las demás activas, que es justo lo que se venía a ver en esta lista.
      tono: (registro) =>
        registro['activa'] !== true ? 'neutro' : registro['enUso'] === true ? 'marca' : 'exito',
    },
  ];

  readonly registros = signal<Record<string, unknown>[]>([]);
  readonly cargando = signal(true);

  /**
   * El cupo del plan. Nulo mientras se pide, o si falló.
   *
   * <p>Su fallo no bloquea el listado: son dos peticiones independientes y no
   * poder decir cuántas empresas caben no es motivo para no enseñar las que hay.
   * El botón simplemente no aparece.
   */
  readonly cupo = signal<CupoDeEmpresas | null>(null);

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
    void this.cargarCupo();
  }

  private async cargarCupo(): Promise<void> {
    try {
      this.cupo.set(await this.api.cupoDeEmpresas());
    } catch {
      // Sin cupo no se ofrece el alta. Un boton que lleva a una pantalla que va
      // a rechazar la operacion es peor que no tener boton.
      this.cupo.set(null);
    }
  }

  /**
   * Vuelve a traer el listado, a peticion del usuario.
   *
   * <p>Existe porque {@code cargar} es privado y la plantilla no lo alcanza.
   * No es lo mismo que recargar la pagina: no se pierde el orden, ni la
   * pagina en la que se estaba, ni lo escrito en el buscador.
   */
  recargar(): void {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      // Se refresca tambien el cupo: si se acaba de registrar una empresa, el
      // numero de usadas cambio y el boton tiene que reflejarlo.
      void this.cargarCupo();
      const empresas = await this.api.empresas();
      const activaId = this.contexto.empresaActiva()?.id;

      this.registros.set(
        empresas.map((empresa: Empresa) => ({
          id: empresa.id,
          ruc: empresa.ruc,
          razonSocial: empresa.razonSocial,
          nombreComercial: empresa.nombreComercial ?? '—',
          domicilioFiscal: empresa.domicilioFiscal,
          // La empresa sobre la que se está trabajando se dice aquí y no con una
          // insignia en la columna de acciones, que es donde estaba: un dato que
          // se lee no pertenece a la columna de lo que se pulsa, y allí obligaba
          // a ensanchar esa columna hasta partir la insignia en dos líneas.
          estado:
            (empresa.activa ? 'Activa' : 'Inactiva') +
            (String(empresa.id) === String(activaId) ? ' · en uso' : ''),
          // El booleano viaja aparte del texto porque de él sale el color del
          // punto: deducirlo de la cadena obligaría a compararla con «Activa»,
          // y bastaría reescribir esa palabra para que el punto dejara de
          // funcionar sin que nada avisara.
          activa: empresa.activa,
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

  ejecutarAccion(evento: { accion: string; registro: Record<string, unknown> }): void {
    if (evento.accion === 'ver') {
      this.abrirFicha(evento.registro);
    }
  }
}
