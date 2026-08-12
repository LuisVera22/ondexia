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
   * Hoy solo puede ser uno o ninguno, y no es una simplificación: el contexto
   * trae la asignación del usuario, no el catálogo de la empresa. Un
   * `sucursalId` con valor significa «solo esta»; a null significa «todas», y
   * cuáles son todas es algo que esta respuesta no dice.
   *
   * Se resuelve con el listado de establecimientos de la Entrega 1. Mientras
   * tanto se muestra lo que se sabe con certeza en vez de rellenar con
   * suposiciones.
   */
  readonly establecimientos = computed<OpcionContexto[]>(() => {
    const activa = this._contexto()?.empresaActiva;
    if (!activa?.sucursalId) {
      return [];
    }
    return [{ id: activa.sucursalId, nombre: activa.sucursalNombre ?? 'Establecimiento' }];
  });

  private readonly _establecimientoElegido = signal<OpcionContexto | null>(null);

  readonly establecimientoActivo = computed<OpcionContexto | null>(
    () => this._establecimientoElegido() ?? this.establecimientos()[0] ?? null
  );

  /**
   * Serie que corresponde al establecimiento activo.
   *
   * Devuelve '0000' mientras no se conozca el código real del establecimiento,
   * que es el de la casa matriz. El código no viene en el contexto —el nombre
   * sí— y adivinarlo a partir del nombre sería inventar un dato con efecto
   * tributario.
   */
  readonly prefijoSerie = computed(() => {
    const codigo = this.establecimientoActivo()?.detalle?.split('·')[0]?.trim();
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
