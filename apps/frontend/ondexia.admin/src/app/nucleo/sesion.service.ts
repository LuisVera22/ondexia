import { HttpBackend, HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CONFIGURACION } from './configuracion';

interface RespuestaToken {
  access_token: string;
  id_token: string;
  refresh_token?: string;
  expires_in: number;
}

interface Sesion {
  readonly acceso: string;
  readonly refresco: string | null;
  /** Marca de tiempo absoluta, en milisegundos. */
  readonly expiraEn: number;
  readonly correo: string;
  readonly nombre: string;
}

const CLAVE_SESION = 'ondexia.sesion';
const CLAVE_VERIFICADOR = 'ondexia.pkce.verificador';
const CLAVE_ESTADO = 'ondexia.pkce.estado';
const CLAVE_DESTINO = 'ondexia.destino';

/**
 * Sesión del usuario contra Cognito, con el flujo de código y PKCE.
 *
 * <h2>Por qué la interfaz alojada y no una pantalla propia</h2>
 *
 * El pool tiene el segundo factor en OPTIONAL, verificación de correo y
 * recuperación de contraseña. Cada una de esas cosas es una negociación con
 * retos —MFA_SETUP, SOFTWARE_TOKEN_MFA, NEW_PASSWORD_REQUIRED— que habría que
 * implementar y mantener aquí. La contrapartida honesta es que el login no
 * lleva nuestra marca más allá de lo que Cognito permite personalizar.
 *
 * <h2>Dónde viven los tokens, y por qué importa</h2>
 *
 * En `sessionStorage`, no en `localStorage`. Los dos son alcanzables desde
 * JavaScript, así que ninguno protege frente a XSS; la diferencia es que
 * sessionStorage muere al cerrar la pestaña, lo que acota la ventana en un
 * equipo compartido — que en una tienda es el caso normal, no el excepcional.
 *
 * Lo verdaderamente seguro sería una cookie HttpOnly, pero eso exige que el
 * canje del código lo haga un servidor nuestro, y hoy el SPA habla directo con
 * Cognito. Queda anotado como deuda.
 */
@Injectable({ providedIn: 'root' })
export class SesionService {
  private readonly configuracion = inject(CONFIGURACION);

  /**
   * HttpClient sin interceptores. Las llamadas a Cognito no llevan nuestro
   * token —ni deben—, y la de refresco se dispara DESDE el interceptor: pasarla
   * otra vez por la cadena sería una recursión infinita al primer 401.
   */
  private readonly http = new HttpClient(inject(HttpBackend));

  private readonly _sesion = signal<Sesion | null>(leerSesion());

  readonly autenticado = computed(() => this._sesion() !== null);
  readonly usuario = computed(() => {
    const sesion = this._sesion();
    return sesion ? { correo: sesion.correo, nombre: sesion.nombre } : null;
  });

  private renovacionEnCurso: Promise<string | null> | null = null;

  tokenDeAcceso(): string | null {
    return this._sesion()?.acceso ?? null;
  }

  /** Redirige a la pantalla de acceso de Cognito. No retorna: la pestaña navega fuera. */
  iniciar(destino = '/'): Promise<void> {
    return this.navegarACognito('/oauth2/authorize', destino);
  }



  /**
   * Todas las puertas de Cognito comparten los mismos parámetros, PKCE
   * incluido: aunque el usuario entre por «recuperar contraseña», el final del
   * recorrido es siempre un código de autorización que hay que poder canjear.
   */
  private async navegarACognito(ruta: string, destino: string): Promise<void> {
    const verificador = aleatorio(64);
    const estado = aleatorio(16);

    sessionStorage.setItem(CLAVE_VERIFICADOR, verificador);
    sessionStorage.setItem(CLAVE_ESTADO, estado);
    sessionStorage.setItem(CLAVE_DESTINO, destino);

    const parametros = new URLSearchParams({
      response_type: 'code',
      client_id: this.configuracion.cognito.clienteId,
      redirect_uri: this.urlRetorno(),
      scope: 'openid email profile',
      state: estado,
      code_challenge: await retoDesde(verificador),
      code_challenge_method: 'S256',
    });

    location.assign(`${this.configuracion.cognito.dominio}${ruta}?${parametros}`);
  }

  /**
   * Canjea el código por tokens. Devuelve a dónde ir después.
   *
   * La comprobación del `state` no es ceremonia: sin ella, un tercero puede
   * inducir al navegador a completar un flujo que él inició —CSRF de inicio de
   * sesión— y dejar a la víctima trabajando dentro de la cuenta del atacante.
   */
  async completar(codigo: string, estado: string): Promise<string> {
    const esperado = sessionStorage.getItem(CLAVE_ESTADO);
    const verificador = sessionStorage.getItem(CLAVE_VERIFICADOR);
    sessionStorage.removeItem(CLAVE_ESTADO);
    sessionStorage.removeItem(CLAVE_VERIFICADOR);

    if (!esperado || estado !== esperado || !verificador) {
      throw new Error('El retorno de Cognito no corresponde a un inicio de sesión de esta pestaña.');
    }

    const respuesta = await firstValueFrom(
      this.http.post<RespuestaToken>(
        `${this.configuracion.cognito.dominio}/oauth2/token`,
        new URLSearchParams({
          grant_type: 'authorization_code',
          client_id: this.configuracion.cognito.clienteId,
          code: codigo,
          redirect_uri: this.urlRetorno(),
          code_verifier: verificador,
        }).toString(),
        { headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }) }
      )
    );

    this.guardar(respuesta);

    const destino = sessionStorage.getItem(CLAVE_DESTINO) ?? '/';
    sessionStorage.removeItem(CLAVE_DESTINO);
    return destino;
  }

  /**
   * Renueva el token de acceso. Devuelve el nuevo, o null si ya no se puede.
   *
   * Las llamadas simultáneas comparten una sola petición. Sin eso, una pantalla
   * que lanza seis consultas a la vez produce seis renovaciones en paralelo, y
   * como Cognito rota el token de refresco, cinco de ellas quedan inservibles y
   * expulsan al usuario.
   */
  renovar(): Promise<string | null> {
    if (this.renovacionEnCurso) {
      return this.renovacionEnCurso;
    }

    const refresco = this._sesion()?.refresco;
    if (!refresco) {
      this.limpiar();
      return Promise.resolve(null);
    }

    this.renovacionEnCurso = firstValueFrom(
      this.http.post<RespuestaToken>(
        `${this.configuracion.cognito.dominio}/oauth2/token`,
        new URLSearchParams({
          grant_type: 'refresh_token',
          client_id: this.configuracion.cognito.clienteId,
          refresh_token: refresco,
        }).toString(),
        { headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }) }
      )
    )
      .then((respuesta) => {
        // En una renovación Cognito no reemite el token de refresco, así que se
        // conserva el que ya había.
        this.guardar({ ...respuesta, refresh_token: respuesta.refresh_token ?? refresco });
        return this.tokenDeAcceso();
      })
      .catch(() => {
        this.limpiar();
        return null;
      })
      .finally(() => {
        this.renovacionEnCurso = null;
      });

    return this.renovacionEnCurso;
  }

  /** Cierra sesión aquí y en Cognito. Sin lo segundo, «entrar» volvería a entrar solo. */
  cerrar(): void {
    this.limpiar();

    const parametros = new URLSearchParams({
      client_id: this.configuracion.cognito.clienteId,
      // A la raiz, que es lo registrado en logout_urls. Al volver sin sesion,
      // el guardian manda otra vez a Cognito.
      logout_uri: origenApp(),
    });

    location.assign(`${this.configuracion.cognito.dominio}/logout?${parametros}`);
  }

  limpiar(): void {
    sessionStorage.removeItem(CLAVE_SESION);
    this._sesion.set(null);
  }

  /**
   * La raíz, no `/acceso/retorno` como en el SPA de clientes.
   *
   * <p>Aquí no hay una pantalla que reciba el código: lo canjea el propio
   * guardián de sesión antes de pintar nada, y corre en cualquier ruta. Devolver
   * a `/acceso/retorno` daba dos fallos a la vez —Cognito rechazaba el retorno
   * con {@code redirect_mismatch} porque el cliente tiene registrada la raíz, y
   * aunque hubiera coincidido no existe esa ruta en el enrutador—.
   *
   * <p>Sin barra final: {@code location.origin} no la lleva y Cognito compara la
   * URL entera, carácter a carácter, contra las registradas.
   */
  private urlRetorno(): string {
    return origenApp();
  }

  private guardar(respuesta: RespuestaToken): void {
    const identidad = cuerpoDelToken(respuesta.id_token);

    const sesion: Sesion = {
      acceso: respuesta.access_token,
      refresco: respuesta.refresh_token ?? this._sesion()?.refresco ?? null,
      // Un minuto de margen. Un token que expira mientras viaja produce un 401
      // esporádico e irreproducible.
      expiraEn: Date.now() + (respuesta.expires_in - 60) * 1000,
      correo: identidad['email'] ?? '',
      nombre: identidad['name'] ?? identidad['email'] ?? '',
    };

    sessionStorage.setItem(CLAVE_SESION, JSON.stringify(sesion));
    this._sesion.set(sesion);
  }

  /** True si conviene renovar antes de usarlo. */
  caducado(): boolean {
    const sesion = this._sesion();
    return !sesion || Date.now() >= sesion.expiraEn;
  }
}

function origenApp(): string {
  return location.origin;
}

function leerSesion(): Sesion | null {
  const guardada = sessionStorage.getItem(CLAVE_SESION);
  if (!guardada) {
    return null;
  }
  try {
    return JSON.parse(guardada) as Sesion;
  } catch {
    // Contenido corrupto. Se descarta en silencio: obligar a entrar de nuevo es
    // molesto una vez, y arrastrar una sesión ilegible rompe en cada pantalla.
    sessionStorage.removeItem(CLAVE_SESION);
    return null;
  }
}

/**
 * Lee las reclamaciones del id_token SIN validar la firma.
 *
 * Es seguro aquí y solo aquí: se usa para pintar el nombre y el correo en la
 * barra superior. Quien valida de verdad es la API, contra el JWKS de Cognito.
 * Nada de lo que salga de esta función puede decidir un permiso.
 */
function cuerpoDelToken(token: string): Record<string, string> {
  try {
    const cuerpo = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(decodeURIComponent(escape(atob(cuerpo))));
  } catch {
    return {};
  }
}

function aleatorio(bytes: number): string {
  const datos = new Uint8Array(bytes);
  crypto.getRandomValues(datos);
  return base64Url(datos);
}

async function retoDesde(verificador: string): Promise<string> {
  const resumen = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verificador));
  return base64Url(new Uint8Array(resumen));
}

/** Base64 de URL: sin relleno y sin los caracteres que habría que escapar. */
function base64Url(datos: Uint8Array): string {
  return btoa(String.fromCharCode(...datos))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}
