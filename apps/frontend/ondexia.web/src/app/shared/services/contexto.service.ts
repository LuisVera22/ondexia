import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CONFIGURACION } from '../../nucleo/configuracion';
import { EmpresaResumen, RespuestaContexto } from '../../nucleo/contexto.api';
import { EmpresaActivaService } from '../../nucleo/empresa-activa.service';
import { OpcionContexto } from '../components/comunes/selector-contexto/selector-contexto.component';

/**
 * Contexto de trabajo activo: quién eres, sobre qué empresa y qué puedes hacer.
 *
 * Ya no inventa nada: todo sale de `GET /api/v1/contexto`. La API interna del
 * servicio se conserva —`empresas`, `empresaActiva`, `cambiarEmpresa`…— para
 * que los componentes que ya la usaban no cambien.
 *
 * El establecimiento sigue determinando la serie del comprobante y el almacén
 * que descarga existencias (documento 04 §3.1), así que vive aquí y no en el
 * encabezado.
 */
@Injectable({ providedIn: 'root' })
export class ContextoService {
  private readonly http = inject(HttpClient);
  private readonly configuracion = inject(CONFIGURACION);
  private readonly empresaActivaGlobal = inject(EmpresaActivaService);

  private readonly _contexto = signal<RespuestaContexto | null>(null);
  private readonly _cargando = signal(false);

  readonly cargando = this._cargando.asReadonly();
  readonly usuario = computed(() => this._contexto()?.usuario ?? null);
  readonly cuenta = computed(() => this._contexto()?.cuenta ?? null);
  readonly permisos = computed(() => this._contexto()?.permisos ?? []);

  readonly empresas = computed<OpcionContexto[]>(() =>
    (this._contexto()?.empresas ?? []).map(comoOpcion)
  );

  readonly empresaActiva = computed<OpcionContexto | null>(() => {
    const activa = this._contexto()?.empresaActiva;
    return activa ? comoOpcion(activa) : null;
  });

  /**
   * Los establecimientos que el usuario alcanza en la empresa activa.
   *
   * Los da el servidor ya recortados a su asignación. Antes se derivaban del
   * campo `sucursalId` de la empresa activa, que es la ASIGNACIÓN y no el
   * catálogo: con valor significaba «solo esta» y a null «todas», pero cuáles
   * eran todas la respuesta no lo decía. El resultado era el contrario del
   * correcto — quien alcanzaba todos los establecimientos se quedaba sin
   * selector, y quien estaba acotado a uno era el único que veía algo.
   */
  readonly establecimientos = computed<OpcionContexto[]>(() =>
    (this._contexto()?.establecimientos ?? []).map((establecimiento) => ({
      id: establecimiento.id,
      nombre: establecimiento.nombre,
      detalle: establecimiento.codigo,
    }))
  );

  private readonly _establecimientoElegido = signal<OpcionContexto | null>(null);

  readonly establecimientoActivo = computed<OpcionContexto | null>(
    () => this._establecimientoElegido() ?? this.establecimientos()[0] ?? null
  );

  /**
   * Serie que corresponde al establecimiento activo.
   *
   * El código llega ahora en el contexto, así que se lee del dato en crudo y no
   * troceando el texto que se pinta en el desplegable. Con lo segundo, cambiar
   * el formato de esa etiqueta —un guion en vez del punto medio— habría
   * cambiado en silencio la serie de los comprobantes.
   *
   * Sigue devolviendo '0000', el de la casa matriz, cuando todavía no hay
   * establecimiento activo: es el único valor que SUNAT garantiza que existe.
   */
  readonly prefijoSerie = computed(() => {
    const activo = this.establecimientoActivo();
    const codigo = this._contexto()?.establecimientos?.find(
      (establecimiento) => establecimiento.id === String(activo?.id)
    )?.codigo;
    return codigo && /^\d{4}$/.test(codigo) ? codigo : '0000';
  });

  puede(permiso: string): boolean {
    return this.permisos().includes(permiso);
  }

  /**
   * Primera llamada después de entrar. Sin esto no hay nada que mostrar.
   *
   * Si el usuario tiene una sola empresa, la API la elige por él y la respuesta
   * ya trae los permisos. Si tiene varias y no se mandó cabecera, vuelve la
   * lista con los permisos vacíos: hay que elegir antes de poder hacer nada.
   */
  /**
   * Carga el contexto una sola vez, y espera si ya hay una carga en marcha.
   *
   * <p>Lo necesita la guarda de permisos: las guardas deciden antes de que se
   * construya el marco de la aplicación, que es quien llama a {@link #cargar}.
   * Sin esperar aquí, la primera navegación consultaría una lista de permisos
   * vacía y rebotaría al escritorio.
   *
   * <p>La promesa se comparte por el mismo motivo que en la renovación del
   * token: varias guardas resolviéndose a la vez lanzarían varias consultas
   * idénticas, y la última en volver pisaría a las demás.
   */
  async asegurarCargado(): Promise<void> {
    if (this._contexto()) {
      return;
    }

    this.cargaEnCurso ??= this.cargar().finally(() => {
      this.cargaEnCurso = null;
    });

    await this.cargaEnCurso;
  }

  private cargaEnCurso: Promise<void> | null = null;

  async cargar(): Promise<void> {
    this._cargando.set(true);
    try {
      const contexto = await firstValueFrom(
        this.http.get<RespuestaContexto>(`${this.configuracion.api}/api/v1/contexto`)
      );
      this._contexto.set(contexto);

      // Se propaga al servicio que lee el interceptor, para que la siguiente
      // petición ya viaje con la cabecera correcta.
      this.empresaActivaGlobal.fijar(contexto.empresaActiva?.id ?? null);
      this._establecimientoElegido.set(null);
    } finally {
      this._cargando.set(false);
    }
  }

  /**
   * Cambia de empresa y vuelve a preguntar.
   *
   * La recarga no es opcional: los permisos son POR EMPRESA. Cambiar sin releer
   * dejaría el menú mostrando lo que se podía hacer en la empresa anterior, y
   * cada clic terminaría en un 403.
   */
  async cambiarEmpresa(empresa: OpcionContexto): Promise<void> {
    this.empresaActivaGlobal.fijar(String(empresa.id));
    await this.cargar();
  }

  cambiarEstablecimiento(establecimiento: OpcionContexto): void {
    this._establecimientoElegido.set(establecimiento);
  }

  limpiar(): void {
    this._contexto.set(null);
    this._establecimientoElegido.set(null);
    this.empresaActivaGlobal.fijar(null);
  }
}

function comoOpcion(empresa: EmpresaResumen): OpcionContexto {
  return {
    id: empresa.id,
    nombre: empresa.nombreComercial || empresa.razonSocial,
    detalle: `RUC ${empresa.ruc}`,
  };
}
