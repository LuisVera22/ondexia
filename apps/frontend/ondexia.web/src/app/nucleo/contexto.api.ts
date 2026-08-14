/**
 * Espejo del contrato de `GET /api/v1/contexto`.
 *
 * Escrito a mano por ahora. Estos tipos salen del `openapi.yaml` que el backend
 * ya exporta durante su build (ExportarContratoIT), y la intención es
 * generarlos — mientras eso no exista, este archivo es el único sitio donde una
 * divergencia entre backend y frontend puede colarse sin que nadie avise.
 *
 * Los nombres se conservan tal cual los emite la API, en la mezcla de español e
 * inglés que impone el contrato. Renombrarlos aquí obligaría a mantener una
 * tabla de traducción que solo añade sitios donde equivocarse.
 */

export interface RespuestaContexto {
  readonly usuario: UsuarioResumen;
  readonly cuenta: CuentaResumen;
  /** null cuando el usuario tiene varias empresas y todavía no ha elegido. */
  readonly empresaActiva: EmpresaResumen | null;
  readonly empresas: readonly EmpresaResumen[];
  /** Vacío mientras no haya empresa activa: los permisos son por empresa. */
  readonly permisos: readonly string[];
}

export interface UsuarioResumen {
  readonly id: string;
  readonly nombre: string;
  readonly email: string;
}

export interface CuentaResumen {
  readonly id: string;
  /** Gobierna la suscripción y el alta de empresas. No es un rol de la matriz. */
  readonly esAdministrador: boolean;
  /** ACTIVA, EN_PRUEBA, SUSPENDIDA o CANCELADA. Decide qué anuncio se pinta. */
  readonly estadoSuscripcion: string | null;
  /**
   * Si la cuenta no puede escribir. Llega calculado del servidor a propósito: la
   * regla de qué estados escriben vive allí, y replicarla aquí haría que un
   * cambio en el servidor dejara la interfaz mintiendo.
   */
  readonly soloLectura: boolean;
}

export interface EmpresaResumen {
  readonly id: string;
  readonly ruc: string;
  readonly razonSocial: string;
  readonly nombreComercial: string;
  readonly modoSunat: string;
  readonly rol: string;
  /** null significa que el usuario alcanza TODAS las sucursales de la empresa. */
  readonly sucursalId: string | null;
  readonly sucursalNombre: string | null;
}
