import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CONFIGURACION } from './configuracion';

/**
 * Cliente del módulo de configuración.
 *
 * <p>Escrito a mano, igual que {@code contexto.api.ts}. La intención sigue
 * siendo generarlo desde el {@code openapi.yaml} que el backend exporta en cada
 * build; mientras eso no exista, estos tipos son el único sitio donde una
 * divergencia entre backend y frontend puede pasar inadvertida.
 */

export interface Empresa {
  readonly id: string;
  /** No se puede cambiar: identifica al contribuyente en los comprobantes emitidos. */
  readonly ruc: string;

  // ── Lo que dice SUNAT. Nada de esto se edita ──────────────────────────────
  //
  // No es una convención de la interfaz: el `PUT` no acepta estos campos, así
  // que un cuerpo que los traiga los pierde. Para cambiarlos hay que volver a
  // consultar el padrón (`verificarEmpresa`).

  readonly razonSocial: string;
  readonly domicilioFiscal: string;
  readonly ubigeo: string | null;
  readonly distrito: string | null;
  readonly provincia: string | null;
  readonly departamento: string | null;

  /**
   * `null` significa **nunca se comprobó**, que no es lo mismo que «está mal».
   *
   * Las empresas dadas de alta antes de que existiera la consulta del padrón lo
   * tienen vacío, y la pantalla necesita distinguirlo para ofrecer comprobarlo
   * en vez de acusar.
   */
  readonly estado: string | null;
  readonly condicion: string | null;
  readonly verificadoEn: string | null;
  readonly tipoSocietario: string | null;
  readonly esAgenteRetencion: boolean;
  readonly esBuenContribuyente: boolean;

  // ── Lo nuestro ────────────────────────────────────────────────────────────

  readonly nombreComercial: string | null;
  readonly cuentaDetracciones: string | null;

  readonly modoSunat: string;
  readonly activa: boolean;

  /**
   * Qué campos acepta el `PUT`, según el servidor.
   *
   * Se usa en lugar de repetir la lista aquí: dos implementaciones de «qué es
   * editable» acabarían discrepando, y la que manda es la del backend.
   */
  readonly editable: readonly string[];
}

/** Lo único editable: ni razón social, ni domicilio, ni ubigeo. */
export interface DatosEmpresa {
  readonly nombreComercial: string | null;
  readonly cuentaDetracciones: string | null;
}

/** Lo que hace falta para dar de alta una empresa: la firma y lo nuestro. */
export interface AltaDeEmpresa {
  readonly atestacion: string;
  readonly nombreComercial: string | null;
  readonly cuentaDetracciones: string | null;
}

/**
 * Cuántas empresas admite el plan y cuántas hay.
 *
 * `maxEmpresas` nulo es **sin límite** —el plan a demanda—, no cero.
 */
export interface CupoDeEmpresas {
  readonly maxEmpresas: number | null;
  readonly empresasUsadas: number;
  readonly cabeOtra: boolean;
  /** Por qué no cabe, con las cifras dentro. Nulo si cabe. */
  readonly motivo: string | null;
}

export interface Establecimiento {
  readonly id: string;
  /** Los cuatro dígitos que asigna SUNAT. No se puede cambiar. */
  readonly codigo: string;
  readonly nombre: string;
  readonly direccion: string;
  readonly ubigeo: string | null;
  readonly activa: boolean;
}

export interface DatosEstablecimiento {
  readonly nombre: string;
  readonly direccion: string;
  readonly ubigeo: string | null;
}

export interface AlmacenApi {
  readonly id: string;
  /** Corto y en mayúsculas: se teclea en cada movimiento de mercadería. */
  readonly codigo: string;
  readonly nombre: string;
  /** null: hay almacenes que no cuelgan de ningún establecimiento. */
  readonly sucursalId: string | null;
  readonly activo: boolean;
}

export interface DatosAlmacen {
  readonly nombre: string;
  readonly sucursalId: string | null;
}

// ── Series ─────────────────────────────────────────────────────────────────

export interface TipoDocumento {
  /** Código del catálogo 01 de SUNAT: '01', '03', '07', '08', '09'. */
  readonly codigo: string;
  readonly nombre: string;
}

export interface SerieApi {
  readonly id: string;
  readonly sucursalId: string;
  readonly tipoDocumento: string;
  readonly tipoDocumentoNombre: string;
  readonly serie: string;
  /** Último número EMITIDO. Una serie nueva vale 0. */
  readonly ultimoNumero: number;
  /** Ya formateado por el backend: `F001-00001234`. */
  readonly siguienteNumero: string;
  readonly activa: boolean;
}

export interface DatosSerie {
  readonly sucursalId: string;
  readonly tipoDocumento: string;
  readonly serie: string;
  /** Solo al dar de alta, para quien migra desde otro sistema. */
  readonly numeroInicial: number;
}

// ── Usuarios ───────────────────────────────────────────────────────────────

export interface UsuarioApi {
  /** El identificador de la ASIGNACIÓN, no el de la persona. */
  readonly asignacionId: string;
  readonly usuarioId: string;
  readonly email: string;
  readonly nombre: string;
  readonly activo: boolean;
  /** Existe en nuestra base pero aún no completó su registro en Cognito. */
  readonly invitado: boolean;
  readonly rolId: string;
  readonly rolNombre: string;
  readonly sucursalId: string | null;
  readonly sucursalNombre: string | null;
  readonly todosLosEstablecimientos: boolean;
}

export interface RolAsignable {
  readonly id: string;
  readonly codigo: string;
  readonly nombre: string;
  readonly descripcion: string | null;
  readonly delSistema: boolean;
}

// ── Roles ──────────────────────────────────────────────────────────────────

export interface RolApi {
  readonly id: string;
  readonly codigo: string;
  readonly nombre: string;
  readonly descripcion: string | null;
  /** Los predefinidos no se editan ni se borran: se duplican. */
  readonly delSistema: boolean;
  readonly cantidadPermisos: number;
  readonly enUso: boolean;
}

/**
 * El catálogo llega como árbol de tres niveles y con los nombres ya traducidos.
 *
 * La jerarquía es **conjuntiva**: para poder consultar productos hacen falta el
 * módulo, el submódulo y la función. Apagar el módulo deja fuera todo lo que
 * cuelga de él.
 *
 * Antes había aquí un mapa de códigos a nombres escrito a mano. Se quitó porque
 * degradaba en silencio: al añadir un módulo nadie se acordaba de ampliarlo, la
 * pantalla mostraba `almacen.tipo_precio` en crudo y no fallaba nada.
 */
export interface FuncionApi {
  readonly id: string;
  readonly accion: string;
  /** «Consultar», «Anular»… ya en castellano. */
  readonly nombre: string;
  readonly codigo: string;
}

export interface SubmoduloApi {
  readonly id: string;
  readonly codigo: string;
  readonly nombre: string;
  readonly funciones: FuncionApi[];
}

export interface ModuloApi {
  readonly id: string;
  readonly codigo: string;
  readonly nombre: string;
  readonly descripcion: string | null;
  readonly submodulos: SubmoduloApi[];
}

// ── Comprobantes ───────────────────────────────────────────────────────────

// ── Identidad visual ───────────────────────────────────────────────────────

export interface LogoApi {
  /** `logo_principal`, `logo_ticket` o `simbolo`. */
  readonly logo: string;
  readonly nombre: string;
  /** null = sin archivo cargado. */
  readonly url: string | null;
  /** Para avisar antes de subir, en vez de dejar que el usuario espere y falle. */
  readonly maximoBytes: number;
}

export interface AutorizacionDeSubida {
  /** Destino del PUT. Lleva la firma dentro; no se le añade cabecera de sesión. */
  readonly url: string;
  readonly clave: string;
  readonly validaSegundos: number;
}

export interface TipoComprobanteApi {
  readonly codigo: string;
  readonly nombre: string;
  readonly emite: boolean;
  /** Por qué un interruptor no se deja apagar. */
  readonly seriesActivas: number;
}

@Injectable({ providedIn: 'root' })
export class ConfiguracionApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(CONFIGURACION);

  private get base(): string {
    return `${this.config.api}/api/v1/configuracion`;
  }

  /** Las empresas que alcanza el usuario, para el listado. */
  empresas(): Promise<Empresa[]> {
    return firstValueFrom(this.http.get<Empresa[]>(`${this.base}/empresas`));
  }

  /**
   * Ficha de una empresa cualquiera de las suyas, sea o no la activa.
   *
   * Es lo que permite abrir una fila del listado sin cambiar de empresa de
   * trabajo. El servidor comprueba que el id esté entre las asignaciones del
   * usuario y responde 403 si no lo está.
   */
  empresaPorId(id: string): Promise<Empresa> {
    return firstValueFrom(this.http.get<Empresa>(`${this.base}/empresas/${id}`));
  }

  empresa(): Promise<Empresa> {
    return firstValueFrom(this.http.get<Empresa>(`${this.base}/empresa`));
  }

  /**
   * Guarda **la empresa activa**, y por eso no recibe id.
   *
   * No es una limitación del cliente: no existe `PUT /empresas/{id}`. La
   * bitácora archiva cada cambio bajo la empresa activa, así que editar otra
   * dejaría el rastro en el historial equivocado. La ficha de una empresa que
   * no es la activa se muestra en solo lectura por este motivo.
   */
  guardarEmpresa(datos: DatosEmpresa): Promise<Empresa> {
    return firstValueFrom(this.http.put<Empresa>(`${this.base}/empresa`, datos));
  }

  /**
   * Trae del padrón lo que no se puede editar.
   *
   * <p>Sirve para refrescar una empresa cuando su razón social cambia en SUNAT,
   * y para verificar por primera vez una creada en el onboarding antiguo, cuyos
   * datos los tecleó una persona.
   *
   * <p>Opera sobre la empresa **activa**, por lo mismo que `guardarEmpresa`: la
   * bitácora archiva el cambio bajo ella.
   */
  verificarEmpresa(atestacion: string): Promise<Empresa> {
    return firstValueFrom(
      this.http.post<Empresa>(`${this.base}/empresa/verificacion`, { atestacion })
    );
  }

  /** Da de alta una empresa más. Requiere ser administrador de la cuenta. */
  registrarEmpresa(datos: AltaDeEmpresa): Promise<Empresa> {
    return firstValueFrom(this.http.post<Empresa>(`${this.base}/empresas`, datos));
  }

  /**
   * El cupo del plan, para decidir si se enseña el botón de alta.
   *
   * <p>Se consulta antes de ofrecer el formulario. Sin esto, la única forma de
   * saber que no cabe otra empresa es rellenarlo entero y leer el error al
   * enviarlo.
   */
  cupoDeEmpresas(): Promise<CupoDeEmpresas> {
    return firstValueFrom(this.http.get<CupoDeEmpresas>(`${this.base}/empresas/cupo`));
  }

  establecimientos(): Promise<Establecimiento[]> {
    return firstValueFrom(
      this.http.get<Establecimiento[]>(`${this.base}/establecimientos`)
    );
  }

  crearEstablecimiento(
    datos: DatosEstablecimiento & { readonly codigo: string }
  ): Promise<Establecimiento> {
    return firstValueFrom(
      this.http.post<Establecimiento>(`${this.base}/establecimientos`, datos)
    );
  }

  actualizarEstablecimiento(
    id: string,
    datos: DatosEstablecimiento
  ): Promise<Establecimiento> {
    return firstValueFrom(
      this.http.put<Establecimiento>(`${this.base}/establecimientos/${id}`, datos)
    );
  }

  /**
   * Pone o quita de servicio. Nunca borra: el establecimiento aparece en los
   * comprobantes ya emitidos.
   *
   * <p>Era un `DELETE`, y por tanto un camino de ida. Reactivar no restaura
   * nada, porque nada se había perdido: el código, las series y su numeración
   * siguieron ahí.
   */
  cambiarEstadoEstablecimiento(id: string, activa: boolean): Promise<Establecimiento> {
    return firstValueFrom(
      this.http.put<Establecimiento>(`${this.base}/establecimientos/${id}/estado`, { activa })
    );
  }

  // ── Almacenes ────────────────────────────────────────────────────────────
  //
  // Cuelgan de /almacen y no de /configuracion porque los gobierna el permiso
  // `almacen.almacen`: quien administra el inventario los crea, sin necesitar
  // acceso a los datos fiscales de la empresa.

  private get baseAlmacen(): string {
    return `${this.config.api}/api/v1/almacen`;
  }

  almacenes(): Promise<AlmacenApi[]> {
    return firstValueFrom(this.http.get<AlmacenApi[]>(`${this.baseAlmacen}/almacenes`));
  }

  crearAlmacen(datos: DatosAlmacen & { readonly codigo: string }): Promise<AlmacenApi> {
    return firstValueFrom(this.http.post<AlmacenApi>(`${this.baseAlmacen}/almacenes`, datos));
  }

  actualizarAlmacen(id: string, datos: DatosAlmacen): Promise<AlmacenApi> {
    return firstValueFrom(
      this.http.put<AlmacenApi>(`${this.baseAlmacen}/almacenes/${id}`, datos)
    );
  }

  /** Desactiva, no borra: el almacén aparece en cada movimiento de stock que lo tocó. */
  /** Pone o quita de servicio. Nunca borra: el kardex lo sigue referenciando. */
  cambiarEstadoAlmacen(id: string, activo: boolean): Promise<AlmacenApi> {
    return firstValueFrom(
      this.http.put<AlmacenApi>(`${this.baseAlmacen}/almacenes/${id}/estado`, { activo })
    );
  }

  // ── Series ───────────────────────────────────────────────────────────────

  tiposDocumento(): Promise<TipoDocumento[]> {
    return firstValueFrom(
      this.http.get<TipoDocumento[]>(`${this.base}/series/tipos-documento`)
    );
  }

  series(): Promise<SerieApi[]> {
    return firstValueFrom(this.http.get<SerieApi[]>(`${this.base}/series`));
  }

  crearSerie(datos: DatosSerie): Promise<SerieApi> {
    return firstValueFrom(this.http.post<SerieApi>(`${this.base}/series`, datos));
  }

  /**
   * Lo único editable de una serie.
   *
   * No hay forma de cambiar el correlativo, y es deliberado: un endpoint para
   * «corregirlo» acabaría usándose para tapar un error y produciría dos
   * comprobantes con el mismo número.
   */
  cambiarEstadoSerie(id: string, activa: boolean): Promise<SerieApi> {
    return firstValueFrom(
      this.http.put<SerieApi>(`${this.base}/series/${id}/estado`, { activa })
    );
  }

  // ── Usuarios ─────────────────────────────────────────────────────────────

  usuarios(): Promise<UsuarioApi[]> {
    return firstValueFrom(this.http.get<UsuarioApi[]>(`${this.base}/usuarios`));
  }

  rolesAsignables(): Promise<RolAsignable[]> {
    return firstValueFrom(
      this.http.get<RolAsignable[]>(`${this.base}/usuarios/roles-asignables`)
    );
  }

  invitarUsuario(datos: {
    readonly email: string;
    readonly nombre: string;
    /** Solo se usa si la persona aún no existe en la cuenta. */
    readonly apellido: string;
    readonly rolId: string;
    readonly sucursalId: string | null;
  }): Promise<UsuarioApi> {
    return firstValueFrom(this.http.post<UsuarioApi>(`${this.base}/usuarios`, datos));
  }

  reasignarUsuario(
    asignacionId: string,
    datos: { readonly rolId: string; readonly sucursalId: string | null }
  ): Promise<UsuarioApi> {
    return firstValueFrom(
      this.http.put<UsuarioApi>(`${this.base}/usuarios/${asignacionId}`, datos)
    );
  }

  cambiarEstadoUsuario(asignacionId: string, activo: boolean): Promise<UsuarioApi> {
    return firstValueFrom(
      this.http.put<UsuarioApi>(`${this.base}/usuarios/${asignacionId}/estado`, { activo })
    );
  }

  /** Quita el acceso a esta empresa. La persona sigue en la cuenta. */
  retirarUsuario(asignacionId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base}/usuarios/${asignacionId}`));
  }

  // ── Roles ────────────────────────────────────────────────────────────────

  roles(): Promise<RolApi[]> {
    return firstValueFrom(this.http.get<RolApi[]>(`${this.base}/roles`));
  }

  catalogoPermisos(): Promise<ModuloApi[]> {
    return firstValueFrom(this.http.get<ModuloApi[]>(`${this.base}/roles/permisos`));
  }

  permisosDelRol(rolId: string): Promise<string[]> {
    return firstValueFrom(this.http.get<string[]>(`${this.base}/roles/${rolId}/permisos`));
  }

  duplicarRol(rolOrigenId: string, nombre: string): Promise<RolApi> {
    return firstValueFrom(
      this.http.post<RolApi>(`${this.base}/roles/${rolOrigenId}/duplicado`, { nombre })
    );
  }

  renombrarRol(rolId: string, nombre: string, descripcion: string | null): Promise<RolApi> {
    return firstValueFrom(
      this.http.put<RolApi>(`${this.base}/roles/${rolId}`, { nombre, descripcion })
    );
  }

  /** El cuerpo es el estado final de la matriz, no un incremento. */
  cambiarPermisosDelRol(rolId: string, permisoIds: string[]): Promise<string[]> {
    return firstValueFrom(
      this.http.put<string[]>(`${this.base}/roles/${rolId}/permisos`, { permisoIds })
    );
  }

  eliminarRol(rolId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base}/roles/${rolId}`));
  }

  // ── Identidad visual ─────────────────────────────────────────────────────
  //
  // La subida va en tres pasos y el archivo NO pasa por nuestra API: se firma
  // un permiso, el navegador sube directo a S3, y después se confirma. Eso
  // esquiva el límite de 10 MB de la pasarela y funciona aunque la Lambda no
  // tenga salida a internet, porque firmar es un cálculo local.

  logos(): Promise<LogoApi[]> {
    return firstValueFrom(this.http.get<LogoApi[]>(`${this.base}/identidad`));
  }

  autorizarSubidaDeLogo(
    logo: string,
    tipoContenido: string,
    bytes: number
  ): Promise<AutorizacionDeSubida> {
    return firstValueFrom(
      this.http.post<AutorizacionDeSubida>(`${this.base}/identidad/${logo}/subida`, {
        tipoContenido,
        bytes,
      })
    );
  }

  /**
   * Sube el archivo directo al almacén.
   *
   * El `Content-Type` va dentro de la firma, así que tiene que ser exactamente
   * el que se declaró al autorizar; mandar otro da 403 desde S3.
   *
   * Esta petición **no lleva cabecera de sesión**, y es importante: el
   * interceptor solo firma las URL que empiezan por la base de la API, y añadir
   * un `Authorization` aquí rompería la firma de S3 —serían dos mecanismos de
   * autenticación en la misma petición.
   */
  subirArchivo(url: string, archivo: File): Promise<unknown> {
    return firstValueFrom(
      this.http.put(url, archivo, { headers: { 'Content-Type': archivo.type } })
    );
  }

  /** Comprueba contra el almacén qué llegó y guarda la referencia. */
  confirmarLogo(logo: string, clave: string): Promise<LogoApi[]> {
    return firstValueFrom(
      this.http.put<LogoApi[]>(`${this.base}/identidad/${logo}`, { clave })
    );
  }

  /** Aquí sí borra el archivo: «no quiero logo», no «cambié de logo». */
  quitarLogo(logo: string): Promise<LogoApi[]> {
    return firstValueFrom(this.http.delete<LogoApi[]>(`${this.base}/identidad/${logo}`));
  }

  // ── Comprobantes ─────────────────────────────────────────────────────────

  tiposComprobante(): Promise<TipoComprobanteApi[]> {
    return firstValueFrom(
      this.http.get<TipoComprobanteApi[]>(`${this.base}/comprobantes`)
    );
  }

  cambiarEstadoTipoComprobante(codigo: string, emite: boolean): Promise<TipoComprobanteApi> {
    return firstValueFrom(
      this.http.put<TipoComprobanteApi>(`${this.base}/comprobantes/${codigo}`, { emite })
    );
  }
}

/**
 * Se reexporta desde {@code errores.ts}, donde vive ahora.
 *
 * <p>Estaba aquí, y la lista de pantallas que lo importan de este módulo es
 * larga. Se deja el puente en lugar de tocar quince ficheros por un cambio de
 * sitio: lo que cambió es la implementación, no quién la usa.
 */
export { mensajeDeError } from './errores';
