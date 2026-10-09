import { HttpBackend, HttpClient } from '@angular/common/http';
import { InjectionToken, inject, provideAppInitializer } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/**
 * Configuración que depende del entorno, resuelta al arrancar.
 *
 * No va en `environment.ts` con `fileReplacements`, que sería lo habitual en
 * Angular, y la razón es de despliegue: los valores de aquí —la URL de la API,
 * el identificador del cliente de Cognito— los produce Terraform, y en el
 * pipeline el SPA se construye ANTES de aplicar la infraestructura. Con
 * configuración de compilación no existirían todavía.
 *
 * La consecuencia buena es más importante que el problema que resuelve: **el
 * mismo artefacto vale para dev y para prod**. Promocionar a producción es
 * copiar exactamente los bytes que se probaron, en vez de reconstruir con otras
 * banderas y confiar en que el resultado sea equivalente.
 *
 * El precio es una petición extra antes de pintar nada. Es un archivo diminuto
 * servido por CloudFront desde el mismo origen.
 */
export interface ConfiguracionApp {
  /** Base de la API, sin barra final. */
  readonly api: string;

  /**
   * Base de la consulta del padrón de SUNAT, sin barra final.
   *
   * Desplegado vale **lo mismo** que `api` —allí la consulta es una ruta más de
   * la misma pasarela— y en local apunta a otro puerto, porque ahí la sirve
   * `ondexia.consultas` en el 8081: la API de Spring no tiene esa ruta ni
   * debe tenerla, porque desplegada no puede salir a internet.
   *
   * Existe como clave aparte para que el código no distinga los dos casos. Con
   * solo `api` habría que decidir cuándo usar una URL y cuándo otra, que es la
   * clase de rama que se prueba en un entorno y falla en el otro.
   */
  readonly consultas: string;

  /**
   * Si el alta es autoservicio.
   *
   * Quien manda es Cognito: con el autoservicio cerrado —que es como está desde
   * el hallazgo C2 de la auditoría 2026-09-01— la interfaz alojada responde a
   * `/signup` con un error, así que la pantalla de acceso no debe ofrecerlo.
   *
   * Sale del despliegue y no de una constante para que las dos mitades no
   * puedan divergir: es `var.autoservicio_inquilinos` de Terraform, el mismo
   * valor que configura el pool.
   */
  readonly autoservicio: boolean;

  readonly cognito: {
    /** Base de la interfaz alojada: de aquí cuelgan /oauth2/authorize y /oauth2/token. */
    readonly dominio: string;
    readonly clienteId: string;
  };
}

let cargada: ConfiguracionApp | null = null;

export const CONFIGURACION = new InjectionToken<ConfiguracionApp>('configuracion-app', {
  providedIn: 'root',
  factory: () => {
    if (!cargada) {
      // Solo puede ocurrir si algo se inyecta antes de que corra el
      // inicializador. Es un error de programación, no de configuración.
      throw new Error('La configuración se pide antes de haberse cargado.');
    }
    return cargada;
  },
});

/**
 * Carga `config.json` antes de que arranque la aplicación.
 *
 * Usa HttpBackend y no HttpClient a propósito: HttpBackend salta la cadena de
 * interceptores. El de autenticación necesita leer esta configuración para
 * saber a qué URL añadir el token, así que pasar por él aquí sería pedirle que
 * lea lo que todavía se está cargando.
 */
export function cargarConfiguracion() {
  return provideAppInitializer(async () => {
    const http = new HttpClient(inject(HttpBackend));
    const configuracion = await firstValueFrom(
      // Relativa a la base del documento: así el SPA funciona igual servido en
      // la raíz que bajo un subdirectorio.
      http.get<ConfiguracionApp>('config.json')
    );

    if (
      !configuracion?.api ||
      !configuracion.consultas ||
      !configuracion.cognito?.clienteId ||
      typeof configuracion.autoservicio !== 'boolean'
    ) {
      // Fallar aquí y no más adelante. Sin esto, la aplicación arranca, pinta
      // el panel y falla en la primera llamada con un error de red contra
      // `undefined/api/v1/contexto`, que no sugiere en absoluto que el problema
      // sea un archivo de configuración mal generado en el despliegue.
      throw new Error(
        'config.json no trae la configuración esperada: hacen falta `api`, ' +
          '`consultas`, `cognito.clienteId` y `autoservicio`. Lo genera el ' +
          'despliegue a partir de `terraform output -json configuracion_spa`.'
      );
    }

    // Sin barra final: el resto del código compone `${api}/api/v1/...` y una
    // barra de más produce `//api/v1`, que API Gateway trata como otra ruta.
    cargada = {
      ...configuracion,
      api: configuracion.api.replace(/\/+$/, ''),
      consultas: configuracion.consultas.replace(/\/+$/, ''),
    };
  });
}
