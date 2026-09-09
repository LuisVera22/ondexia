import { Component, EventEmitter, Output, computed, inject, signal } from '@angular/core';
import {
  ConsultaDeRuc,
  ConsultasApiService,
  ErrorDeConsulta,
} from '../../../../nucleo/consultas.api.service';

/**
 * El primer campo de cualquier alta de empresa: el RUC, y lo que SUNAT dice.
 *
 * <h2>Por qué es un componente compartido</h2>
 *
 * <p>Porque hacen falta dos: el alta de cuenta (onboarding) y el registro de una
 * empresa más desde el ERP. Son pantallas distintas con el mismo paso, y
 * escribirlo dos veces significa dos comportamientos en cuanto uno de los dos
 * cambie — y lo que cambiaría es justo el mensaje de por qué un RUC no entra,
 * que es lo único que la persona lee cuando se atasca.
 *
 * <h2>El RUC va primero, y eso no es estética</h2>
 *
 * <p>El borrador lo tenía en quinto lugar. Ahí la consulta se dispararía después
 * de que alguien hubiera rellenado cuatro campos, para sobrescribirlos. Primero
 * el RUC: se consulta, y el resto aparece ya relleno y sin poder tocarse.
 *
 * <h2>Lo que se emite no son los campos, es la firma</h2>
 *
 * <p>La API no puede preguntarle a SUNAT —su Lambda no sale a internet— así que
 * confía en la atestación firmada que devuelve esta consulta. De ahí saca la
 * razón social, el domicilio, el ubigeo, el estado y la condición.
 *
 * <p>Por eso lo que se pinta aquí es <strong>solo para leer</strong>: no viaja en
 * la petición. Quien edite estos valores con las herramientas del navegador no
 * consigue nada, porque no puede firmar. Es la diferencia entre un campo de solo
 * lectura y un dato que no se puede falsificar.
 */
@Component({
  selector: 'app-consulta-ruc',
  templateUrl: './consulta-ruc.component.html',
})
export class ConsultaRucComponent {
  private readonly api = inject(ConsultasApiService);

  /**
   * La consulta comprobada, o {@code null}.
   *
   * <p>Se emite {@code null} también cuando el RUC existe pero no es apto: el
   * formulario de arriba no debe poder enviarse, y distinguir «no consultado» de
   * «consultado y rechazado» es asunto de esta pantalla, no del padre.
   */
  @Output() readonly verificado = new EventEmitter<ConsultaDeRuc | null>();

  readonly ruc = signal('');
  readonly consultando = signal(false);
  readonly resultado = signal<ConsultaDeRuc | null>(null);
  readonly error = signal<string | null>(null);

  /** Si el fallo merece un botón de reintentar. Lo decide el servidor. */
  readonly puedeReintentar = signal(false);

  /** El RUC tiene la forma correcta: once dígitos. */
  get formaValida(): boolean {
    return /^\d{11}$/.test(this.ruc());
  }

  /**
   * Los datos, o nulo. Es `computed` y no un getter porque la plantilla lo usa
   * con `@if (datos(); as ficha)`, que necesita una señal para no volver a
   * evaluarse en cada comprobación de cambios.
   */
  readonly datos = computed(() => this.resultado()?.datos ?? null);

  /** Consultado, existe, y puede registrarse. */
  readonly apta = computed(() => this.datos()?.aptaParaRegistro === true);

  escribirRuc(valor: string): void {
    // Solo digitos: pegar un RUC con guiones o espacios es corriente, y
    // rechazarlo por el formato en vez de limpiarlo es hacer trabajar a alguien
    // para nada.
    const limpio = valor.replace(/\D/g, '').slice(0, 11);
    this.ruc.set(limpio);

    // Cambiar el RUC invalida lo consultado. Sin esto quedaria en pantalla la
    // ficha de un RUC y en el campo otro numero, y al enviar se registraria el
    // primero — con todo el aspecto de haber registrado el segundo.
    if (this.resultado() !== null || this.error() !== null) {
      this.resultado.set(null);
      this.error.set(null);
      this.verificado.emit(null);
    }
  }

  async consultar(): Promise<void> {
    if (!this.formaValida || this.consultando()) {
      return;
    }

    this.consultando.set(true);
    this.error.set(null);
    this.puedeReintentar.set(false);
    this.resultado.set(null);
    this.verificado.emit(null);

    try {
      const consulta = await this.api.consultarRuc(this.ruc());
      this.resultado.set(consulta);

      // Solo se propaga si puede registrarse. Un RUC no habido se muestra con su
      // motivo —hay que poder leerlo— pero no habilita el envio.
      this.verificado.emit(consulta.datos.aptaParaRegistro ? consulta : null);
    } catch (fallo: unknown) {
      if (fallo instanceof ErrorDeConsulta) {
        this.error.set(fallo.detalle.mensaje);
        this.puedeReintentar.set(fallo.detalle.reintentable);
      } else {
        this.error.set('No se pudo consultar el RUC.');
        this.puedeReintentar.set(true);
      }
    } finally {
      this.consultando.set(false);
    }
  }

  /**
   * El texto del estado, sin los guiones bajos del enumerado.
   *
   * <p>«BAJA_PROVISIONAL_OFICIO» es un identificador, no algo que se le enseñe a
   * nadie.
   */
  legible(valor: string | null | undefined): string {
    return (valor ?? '').replace(/_/g, ' ');
  }

  /** Cuándo se comprobó, en corto. La fecha importa: el estado es una foto. */
  get comprobadoEn(): string {
    const crudo = this.datos()?.consultadoEn;
    if (!crudo) {
      return '';
    }
    return new Date(crudo).toLocaleString('es-PE', {
      day: '2-digit',
      month: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      timeZone: 'America/Lima',
    });
  }
}
