import { Component, computed, inject, signal } from '@angular/core';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import {
  ConfiguracionApiService,
  Empresa,
  LogoApi,
  mensajeDeError,
} from '../../../nucleo/configuracion.api.service';

/** Lo que el navegador anuncia para PNG y JPG. Sin SVG, a propósito. */
const TIPOS_ADMITIDOS = ['image/png', 'image/jpeg'];

type Formato = 'a4' | 'ticket';

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
 * <h2>La previsualización no es un adorno</h2>
 *
 * <p>Es lo único que detecta que un logo quedó desproporcionado <em>antes</em> de
 * emitir. Suelto sobre fondo blanco casi cualquier archivo se ve bien; dentro de
 * la cabecera de un documento se delatan el logo demasiado ancho y el que trae
 * márgenes internos enormes.
 *
 * <p>En el ticket importa aún más: la impresión térmica es monocroma, así que el
 * logo se pinta en escala de grises al ancho real de 80 mm. Un archivo con
 * degradado se ve perfecto en la tarjeta y sale una mancha en la impresora.
 *
 * <p>No necesita ningún comprobante: es un marco con las proporciones correctas.
 * Los datos fiscales que lleva —RUC, razón social, la serie— son los reales de
 * la empresa, no los inventados de una maqueta.
 *
 * <h2>PNG y JPG. No SVG</h2>
 *
 * <p>Un SVG es XML que puede llevar JavaScript, y este es el único sitio donde
 * alguien sube un archivo que después se muestra a otros. Se comprueba aquí para
 * avisar antes de gastar la subida, y el servidor lo comprueba otra vez porque
 * esta validación se salta llamando a la API directamente.
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

  readonly formato = signal<Formato>('a4');

  /** Datos fiscales de la cabecera. Null si no se pudieron leer — ver cargar(). */
  readonly empresa = signal<Empresa | null>(null);
  readonly numeroFactura = signal<string | null>(null);
  readonly numeroBoleta = signal<string | null>(null);

  /**
   * El logo que corresponde al formato: el principal encabeza el A4, y el de
   * ticket la impresión térmica.
   *
   * <p>Sin respaldo de uno por otro a propósito. Si el de ticket falta, lo que
   * hay que ver es que falta —es justo la comprobación que se viene a hacer— y
   * no el principal disfrazado, que daría por bueno algo que no se va a imprimir.
   */
  readonly logoDelFormato = computed(() => {
    const clave = this.formato() === 'a4' ? 'logo_principal' : 'logo_ticket';
    return this.logos().find((l) => l.logo === clave)?.url ?? null;
  });

  readonly numeroDelFormato = computed(() =>
    this.formato() === 'a4' ? this.numeroFactura() : this.numeroBoleta()
  );

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

    await this.cargarDatosDeLaCabecera();
  }

  /**
   * Empresa y series, solo para la cabecera de la previsualización.
   *
   * <p>Van aparte y con el fallo absorbido porque son de <strong>otros
   * permisos</strong>: quien tenga {@code configuracion.identidad} y no
   * {@code configuracion.empresa} recibiría un 403 aquí. Dejarlo caer en el
   * mismo bloque que los logos rompería la pantalla entera por no poder pintar
   * un RUC.
   *
   * <p>Sin estos datos la previsualización sigue sirviendo para lo que importa:
   * ver el logo a escala dentro del marco.
   */
  private async cargarDatosDeLaCabecera(): Promise<void> {
    try {
      this.empresa.set(await this.api.empresa());
    } catch {
      this.empresa.set(null);
    }

    try {
      const series = await this.api.series();
      const activas = series.filter((s) => s.activa);
      this.numeroFactura.set(
        activas.find((s) => s.tipoDocumento === '01')?.siguienteNumero ?? null
      );
      this.numeroBoleta.set(
        activas.find((s) => s.tipoDocumento === '03')?.siguienteNumero ?? null
      );
    } catch {
      this.numeroFactura.set(null);
      this.numeroBoleta.set(null);
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

      // Se salta al formato donde se acaba de cambiar algo: mirar la
      // previsualización es el motivo de haber subido.
      this.formato.set(logo.logo === 'logo_ticket' ? 'ticket' : 'a4');
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
