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

  /**
   * El token de identidad, EN MEMORIA y nunca en `sessionStorage`.
   *
   * Lo necesita un solo endpoint —la vinculación de una invitación— porque es el
   * único que lleva el correo firmado por Cognito. A diferencia del de acceso,
   * este token contiene datos personales (correo y nombre) y no hace falta para
   * operar, así que no gana nada sobreviviendo a una recarga: si falta, se pide
   * uno nuevo con el token de refresco, que sí está guardado.
   */
  private identidad: string | null = null;

  tokenDeAcceso(): string | null {
    return this._sesion()?.acceso ?? null;
  }

  /** El de identidad, recién renovado si el que había en memoria ya no sirve. */
  async tokenDeIdentidad(): Promise<string | null> {
    if (this.identidad && !this.caducado()) {
      return this.identidad;
    }

    await this.renovar();
    return this.identidad;
  }

  /** Redirige a la pantalla de acceso de Cognito. No retorna: la pestaña navega fuera. */
  iniciar(destino = '/'): Promise<void> {
    return this.navegarACognito('/oauth2/authorize', destino);
  }

  /**
   * Redirige al alta de Cognito: correo, contraseña y verificación del correo.
   *
   * Al confirmar el código, Cognito inicia la sesión solo y vuelve a
   * `/acceso/retorno` con un código de autorización — el mismo camino que un
   * acceso normal. La diferencia aparece después: el contexto responde
   * `usuario_no_registrado` y el SPA lleva a completar los datos de la empresa.
   */
  registrarse(): Promise<void> {
    return this.navegarACognito('/signup', '/');
  }

  /**
   * Redirige a la recuperación de contraseña de Cognito.
   *
   * El formulario propio que había aquí no enviaba nada — era de la maqueta.
   * Quien sabe si el correo existe, manda el código y valida la contraseña
   * nueva es Cognito, así que lo honesto es llevar ahí.
   */
  recuperar(): Promise<void> {
    return this.navegarACognito('/forgotPassword', '/');
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

  /**
   * Cierra sesión: revoca el refresco, lo olvida aquí y sale de Cognito.
   *
   * <p>Los tres pasos importan y faltaba el primero (hallazgo C3). Antes se
   * borraba el `sessionStorage` y se navegaba a `/logout`, que cierra la sesión
   * del NAVEGADOR en Cognito — pero **el refresh token seguía siendo válido**.
   * Quien lo hubiera copiado antes podía canjearlo durante los treinta días de su
   * vigencia; cerrar sesión no le quitaba nada.
   *
   * Es `keepalive` y no `await`: la navegación a Cognito ocurre a continuación y
   * mataría una petición normal a medio vuelo. Con `keepalive` el navegador la
   * termina aunque la página se vaya.
   *
   * Y no se espera la respuesta a propósito. Si la revocación falla —red caída,
   * Cognito de mal día— igualmente hay que borrar la sesión local y salir: la
   * alternativa es dejar a alguien dentro porque no se pudo cerrar del todo. El
   * refresco caduca solo en siete días.
   */
  cerrar(): void {
    this.revocarRefresco();
    this.limpiar();

    const parametros = new URLSearchParams({
      client_id: this.configuracion.cognito.clienteId,
      logout_uri: `${origenApp()}/acceso/ingresar`,
    });

    location.assign(`${this.configuracion.cognito.dominio}/logout?${parametros}`);
  }

  /**
   * `/oauth2/revoke` invalida el refresco Y todos los tokens de acceso emitidos
   * con él. Es el endpoint que habilita `enable_token_revocation` en Terraform.
   *
   * <p>Sin `client_secret`: el cliente del SPA es público, por eso usa PKCE.
   */
  private revocarRefresco(): void {
    const refresco = this._sesion()?.refresco;
    if (!refresco) {
      return;
    }

    void fetch(`${this.configuracion.cognito.dominio}/oauth2/revoke`, {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        token: refresco,
        client_id: this.configuracion.cognito.clienteId,
      }),
      keepalive: true,
    }).catch(() => {
      // Da igual: la sesión local se borra igualmente y el refresco caduca solo.
    });
  }

  limpiar(): void {
    sessionStorage.removeItem(CLAVE_SESION);
    this.identidad = null;
    this._sesion.set(null);
  }

  private urlRetorno(): string {
    return `${origenApp()}/acceso/retorno`;
  }

  private guardar(respuesta: RespuestaToken): void {
    const identidad = cuerpoDelToken(respuesta.id_token);
    this.identidad = respuesta.id_token;

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
