import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { accionConEstado } from '../../../shared/components/comunes/boton/estado-accion';
import {
  ConfiguracionApiService,
  EmisionElectronica,
  ModoSunat,
} from '../../../nucleo/configuracion.api.service';
import { mensajeDeError } from '../../../nucleo/errores';
import { AvisosService } from '../../../shared/services/avisos.service';
import { ContextoService } from '../../../shared/services/contexto.service';

/** Tipos que la API fija para las URL prefirmadas; van dentro de la firma. */
const TIPO_CERTIFICADO = 'application/x-pkcs12';
const TIPO_CREDENCIALES = 'application/json';
/** Un .pfx pesa unos pocos KB; algo de más de un megabyte no es un certificado. */
const MAXIMO_BYTES_CERTIFICADO = 1024 * 1024;

/**
 * Certificado digital, clave SOL y entorno de SUNAT de la empresa activa
 * (doc 14 §4).
 *
 * <h2>Ni el certificado ni la contraseña pasan por la API</h2>
 *
 * <p>Se piden dos URL prefirmadas, el navegador sube el {@code .pfx} y un JSON
 * con la contraseña y la clave SOL directo al bucket, y después se confirma. La
 * API comprueba que los dos objetos existen sin poder leerlos, y encola una
 * verificación: el Emisor abre el certificado y dice quién es y hasta cuándo
 * vale, o por qué no abre. Esa respuesta llega en segundos y la pantalla la
 * consulta mientras espera.
 *
 * <h2>Producción se pide dos veces</h2>
 *
 * <p>Lo que se emite contra producción tiene valor tributario y no se
 * deshace. El botón muestra una confirmación antes de llamar; la beta vuelve
 * sin preguntar.
 */
@Component({
  selector: 'app-emision',
  imports: [EncabezadoPaginaComponent, BotonComponent, FormsModule],
  templateUrl: './emision.component.html',
})
export class EmisionComponent {
  private readonly api = inject(ConfiguracionApiService);
  private readonly avisos = inject(AvisosService);
  private readonly contexto = inject(ContextoService);

  readonly entradaArchivo = viewChild<ElementRef<HTMLInputElement>>('entradaArchivo');

  readonly emision = signal<EmisionElectronica | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly puedeEditar = computed(() => this.contexto.puede('configuracion.empresa:editar'));

  /** El certificado está cargado y el Emisor todavía no dijo nada. */
  readonly verificando = computed(() => {
    const e = this.emision();
    return e?.certificadoCargadoEn != null && e.certificadoVerificadoEn == null;
  });

  /** Abrió, no caducó: lo que producción exige. */
  readonly certificadoVigente = computed(() => {
    const e = this.emision();
    return (
      e?.certificadoVerificadoEn != null &&
      e.certificadoError == null &&
      e.certificadoVenceEn != null &&
      new Date(e.certificadoVenceEn) >= new Date(new Date().toDateString())
    );
  });

  // El formulario de carga. Sin ReactiveForms: son cuatro campos y un archivo.
  archivo = signal<File | null>(null);
  claveCertificado = '';
  usuarioSol = '';
  claveSol = '';
  readonly mostrarFormulario = signal(false);
  readonly confirmandoProduccion = signal(false);

  private sondeo: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.aplicar(await this.api.emision());
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudo cargar la configuración de emisión.'));
    } finally {
      this.cargando.set(false);
    }
  }

  private aplicar(e: EmisionElectronica): void {
    this.emision.set(e);
    this.usuarioSol = e.usuarioSol ?? this.usuarioSol;
    this.programarSondeo();
  }

  /**
   * Mientras el Emisor verifica, se vuelve a preguntar cada cinco segundos y
   * hasta doce veces: una verificación tarda lo que un arranque en frío. Si
   * después de un minuto no hay respuesta, la pantalla lo dice y deja de
   * insistir; el resultado aparecerá al volver a entrar.
   */
  private intentosDeSondeo = 0;

  private programarSondeo(): void {
    if (this.sondeo) {
      clearTimeout(this.sondeo);
      this.sondeo = null;
    }
    if (!this.verificando() || this.intentosDeSondeo >= 12) {
      return;
    }
    this.sondeo = setTimeout(async () => {
      this.intentosDeSondeo++;
      try {
        this.aplicar(await this.api.emision());
      } catch {
        // Un fallo de red en el sondeo no merece aviso: la siguiente vuelta lo repite.
        this.programarSondeo();
      }
    }, 5000);
  }

  abrirFormulario(): void {
    this.mostrarFormulario.set(true);
  }

  cancelarFormulario(): void {
    this.mostrarFormulario.set(false);
    this.archivo.set(null);
    this.claveCertificado = '';
    this.claveSol = '';
  }

  alElegirArchivo(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0] ?? null;
    if (archivo && archivo.size > MAXIMO_BYTES_CERTIFICADO) {
      this.avisos.error(
        `Un certificado .pfx pesa unos pocos KB; este tiene ${Math.round(archivo.size / 1024)} KB.`,
        'Ese archivo no parece un certificado'
      );
      entrada.value = '';
      this.archivo.set(null);
      return;
    }
    this.archivo.set(archivo);
  }

  readonly cargarCertificado = accionConEstado(async () => {
    const archivo = this.archivo();
    if (!archivo || !this.claveCertificado || !this.usuarioSol.trim() || !this.claveSol) {
      this.avisos.error('Hacen falta el archivo .pfx, su contraseña, el usuario SOL y la clave SOL.');
      throw new Error('Formulario incompleto');
    }
    try {
      const autorizacion = await this.api.autorizarCargaDeCertificado();
      await this.api.subirContenido(autorizacion.urlCertificado, archivo, TIPO_CERTIFICADO);
      const credenciales = new Blob(
        [JSON.stringify({ claveCertificado: this.claveCertificado, claveSol: this.claveSol })],
        { type: TIPO_CREDENCIALES }
      );
      await this.api.subirContenido(autorizacion.urlCredenciales, credenciales, TIPO_CREDENCIALES);
      this.intentosDeSondeo = 0;
      this.aplicar(await this.api.confirmarCargaDeCertificado(this.usuarioSol.trim()));
      this.cancelarFormulario();
      this.avisos.exito(
        'El Emisor va a abrirlo con la contraseña indicada; en unos segundos se verá aquí si abre.',
        'Certificado cargado'
      );
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo cargar el certificado.'));
      throw fallo;
    }
  });

  pedirProduccion(): void {
    this.confirmandoProduccion.set(true);
  }

  cancelarProduccion(): void {
    this.confirmandoProduccion.set(false);
  }

  // Dos acciones y no una con parámetro: `accionConEstado` no recibe
  // argumentos a propósito —el botón la invoca sin nada— y además cada una
  // tiene su propio estado, que es lo que se quiere: pulsar «volver a la beta»
  // no debe poner en marcha el botón de producción.
  readonly irAProduccion = accionConEstado(() => this.cambiarModo('PRODUCCION'));
  readonly volverABeta = accionConEstado(() => this.cambiarModo('BETA'));

  private async cambiarModo(modo: ModoSunat): Promise<void> {
    try {
      this.aplicar(await this.api.cambiarModoSunat(modo));
      this.confirmandoProduccion.set(false);
      this.avisos.exito(
        modo === 'PRODUCCION'
          ? 'Las boletas y facturas que se emitan desde ahora tienen valor tributario.'
          : 'Lo que se emita desde ahora va al entorno de pruebas de SUNAT.',
        modo === 'PRODUCCION' ? 'Emitiendo en producción' : 'De vuelta en la beta'
      );
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo cambiar el entorno de SUNAT.'));
      throw fallo;
    }
  }

  fecha(instante: string | null): string {
    return instante ? new Date(instante).toLocaleString('es-PE', { dateStyle: 'medium', timeStyle: 'short' }) : '';
  }

  fechaCorta(dia: string | null): string {
    return dia ? new Date(dia + 'T00:00:00').toLocaleDateString('es-PE', { dateStyle: 'long' }) : '';
  }
}
