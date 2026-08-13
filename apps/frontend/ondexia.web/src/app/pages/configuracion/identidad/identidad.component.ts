import { Component, inject, signal } from '@angular/core';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import {
  ConfiguracionApiService,
  LogoApi,
  mensajeDeError,
} from '../../../nucleo/configuracion.api.service';

/** Lo que el navegador anuncia para PNG y JPG. Sin SVG, a propósito. */
const TIPOS_ADMITIDOS = ['image/png', 'image/jpeg'];

/**
 * Identidad visual: los logos de la empresa.
 *
 * <h2>El archivo no pasa por nuestra API</h2>
 *
 * <p>Tres pasos: se pide permiso, el navegador sube directo al almacén, y
 * después se confirma. Eso esquiva el límite de 10 MB de la pasarela, no gasta
 * tiempo de función, y —lo que hizo viable esta pantalla— funciona aunque la
 * Lambda no tenga salida a internet, porque firmar es un cálculo local.
 *
 * <p>La petición de subida es la única de toda la aplicación que sale sin
 * cabecera de sesión. No es un descuido: el interceptor solo firma las URL de
 * nuestra API, y meter un {@code Authorization} en una URL ya firmada por S3
 * serían dos mecanismos de autenticación en la misma petición, que S3 rechaza.
 *
 * <h2>PNG y JPG. No SVG</h2>
 *
 * <p>Un SVG es XML que puede llevar JavaScript, y este es el único sitio donde
 * alguien sube un archivo que después se muestra a otros —incluido dentro de un
 * PDF y en la barra superior—. Se comprueba aquí para avisar antes de gastar la
 * subida, y el servidor lo comprueba otra vez porque esta validación se salta
 * llamando a la API directamente.
 *
 * <h2>Lo que la maqueta prometía y no está</h2>
 *
 * <p>La previsualización dentro del comprobante. Necesita el comprobante, que es
 * de C1. Dejarla pintada haría creer que el logo ya sale en los documentos.
 */
@Component({
  selector: 'app-identidad',
  imports: [EncabezadoPaginaComponent],
  templateUrl: './identidad.component.html',
})
export class IdentidadComponent {
  private readonly api = inject(ConfiguracionApiService);

  readonly logos = signal<LogoApi[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  /** Cuál está subiendo, para deshabilitar solo ese y no la pantalla entera. */
  readonly enCurso = signal<string | null>(null);

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.logos.set(await this.api.logos());
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar los logos.'));
    } finally {
      this.cargando.set(false);
    }
  }

  limiteEnKb(logo: LogoApi): number {
    return Math.round(logo.maximoBytes / 1024);
  }

  async alElegirArchivo(logo: LogoApi, evento: Event): Promise<void> {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0];

    // Se limpia siempre: sin esto, elegir el mismo archivo dos veces seguidas no
    // dispara el evento y parece que el botón dejó de funcionar.
    entrada.value = '';

    if (!archivo) {
      return;
    }

    if (!TIPOS_ADMITIDOS.includes(archivo.type)) {
      this.error.set(
        `El ${logo.nombre.toLowerCase()} admite PNG o JPG. El SVG no se acepta: puede llevar código dentro.`
      );
      return;
    }

    if (archivo.size > logo.maximoBytes) {
      this.error.set(
        `El ${logo.nombre.toLowerCase()} no puede pasar de ${this.limiteEnKb(logo)} KB. ` +
          `El tuyo son ${Math.round(archivo.size / 1024)} KB.`
      );
      return;
    }

    await this.subir(logo, archivo);
  }

  private async subir(logo: LogoApi, archivo: File): Promise<void> {
    this.enCurso.set(logo.logo);
    this.error.set(null);

    try {
      const autorizacion = await this.api.autorizarSubidaDeLogo(
        logo.logo,
        archivo.type,
        archivo.size
      );

      await this.api.subirArchivo(autorizacion.url, archivo);

      // Hasta aquí no ha cambiado nada en la base: el servidor comprueba con el
      // almacén qué llegó de verdad antes de guardar la referencia.
      this.logos.set(await this.api.confirmarLogo(logo.logo, autorizacion.clave));
    } catch (fallo: unknown) {
      this.error.set(
        mensajeDeError(fallo, `No se pudo subir el ${logo.nombre.toLowerCase()}.`)
      );
    } finally {
      this.enCurso.set(null);
    }
  }

  async quitar(logo: LogoApi): Promise<void> {
    this.enCurso.set(logo.logo);
    this.error.set(null);
    try {
      this.logos.set(await this.api.quitarLogo(logo.logo));
    } catch (fallo: unknown) {
      this.error.set(
        mensajeDeError(fallo, `No se pudo quitar el ${logo.nombre.toLowerCase()}.`)
      );
    } finally {
      this.enCurso.set(null);
    }
  }

  /** Texto de ayuda por hueco. Vive aquí y no en la API: es cosa de la pantalla. */
  descripcion(logo: LogoApi): string {
    switch (logo.logo) {
      case 'logo_principal':
        return 'Encabezado de la aplicación, PDF de comprobantes e informes.';
      case 'logo_ticket':
        return 'Impresión térmica de boletas. Monocromo y de alto contraste.';
      default:
        return 'Barra lateral colapsada y pestaña del navegador. Cuadrado.';
    }
  }
}
