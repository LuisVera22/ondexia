/** Decimal finito: coeficiente entero y escala. Nunca representa importes mediante number. */
export class DecimalExacto {
  private constructor(private readonly coeficiente: bigint, private readonly escala: number) {}

  static desde(texto: string): DecimalExacto {
    if (!/^-?\d{1,24}(\.\d{1,12})?$/.test(texto)) throw new Error('Decimal inválido.');
    const [entero, fraccion = ''] = texto.split('.');
    const signo = entero.startsWith('-') ? -1n : 1n;
    return new DecimalExacto(signo * BigInt(entero.replace('-', '') + fraccion), fraccion.length);
  }

  private ajustar(escala: number): bigint {
    return this.coeficiente * 10n ** BigInt(escala - this.escala);
  }

  sumar(otro: DecimalExacto): DecimalExacto {
    const escala = Math.max(this.escala, otro.escala);
    return new DecimalExacto(this.ajustar(escala) + otro.ajustar(escala), escala);
  }

  negar(): DecimalExacto { return new DecimalExacto(-this.coeficiente, this.escala); }
  restar(otro: DecimalExacto): DecimalExacto { return this.sumar(otro.negar()); }
  multiplicar(otro: DecimalExacto): DecimalExacto {
    return new DecimalExacto(this.coeficiente * otro.coeficiente, this.escala + otro.escala);
  }

  /** HALF_UP, también para valores negativos; escala y exponentes son metadatos enteros. */
  private static cociente(numerador: bigint, denominador: bigint): bigint {
    if (denominador === 0n) throw new Error('No se puede dividir entre cero.');
    const negativo = (numerador < 0n) !== (denominador < 0n);
    const dividendo = numerador < 0n ? -numerador : numerador;
    const divisor = denominador < 0n ? -denominador : denominador;
    const entero = dividendo / divisor;
    const redondeado = entero + (2n * (dividendo % divisor) >= divisor ? 1n : 0n);
    return negativo ? -redondeado : redondeado;
  }

  dividir(otro: DecimalExacto, escala: number): DecimalExacto {
    DecimalExacto.exigirEscala(escala);
    return new DecimalExacto(DecimalExacto.cociente(
      this.coeficiente * 10n ** BigInt(otro.escala + escala),
      otro.coeficiente * 10n ** BigInt(this.escala)), escala);
  }

  redondear(escala: number): DecimalExacto {
    DecimalExacto.exigirEscala(escala);
    return new DecimalExacto(escala >= this.escala ? this.ajustar(escala)
      : DecimalExacto.cociente(this.coeficiente, 10n ** BigInt(this.escala - escala)), escala);
  }

  comparar(otro: DecimalExacto): number {
    const diferencia = this.restar(otro).coeficiente;
    return diferencia < 0n ? -1 : diferencia > 0n ? 1 : 0;
  }

  private static exigirEscala(escala: number): void {
    if (!Number.isInteger(escala) || escala < 0 || escala > 12)
      throw new Error('La escala debe ser un entero entre cero y doce.');
  }

  texto(): string {
    const signo = this.coeficiente < 0n ? '-' : '';
    const digitos = (this.coeficiente < 0n ? -this.coeficiente : this.coeficiente).toString()
      .padStart(this.escala + 1, '0');
    return signo + (this.escala ? digitos.slice(0, -this.escala) + '.' + digitos.slice(-this.escala) : digitos);
  }
}

export const decimal = (texto: string): DecimalExacto => DecimalExacto.desde(texto);

export function normalizarEntradaDecimal(texto: string): string { return texto.replace(',', '.'); }

/** Mismos límites de entrada que @Digits del contrato de ventas; no normaliza errores a cero. */
export function entradaDecimalValida(texto: string, decimales: number): boolean {
  return new RegExp(`^\\d{1,12}(\\.\\d{1,${decimales}})?$`).test(texto);
}

export function importeDecimal(texto: string): string {
  const [entero, fraccion] = decimal(texto).redondear(2).texto().split('.');
  const negativo = entero.startsWith('-');
  const agrupado = entero.replace('-', '').replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `${negativo ? '-' : ''}S/ ${agrupado}.${fraccion}`;
}
