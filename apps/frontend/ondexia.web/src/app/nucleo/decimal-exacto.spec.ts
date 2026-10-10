import { decimal, entradaDecimalValida, importeDecimal } from './decimal-exacto';

describe('DecimalExacto · reglas monetarias vigentes', () => {
  for (const [entrada, esperado] of [
    ['10.075000', '10.08'], ['-10.075000', '-10.08'],
    ['123456789012.344999', '123456789012.34'], ['0.005', '0.01'], ['0', '0.00'],
  ]) {
    it(`redondea ${entrada} con HALF_UP`, () => {
      expect(decimal(entrada).redondear(2).texto()).toBe(esperado);
    });
  }
  it('conserva fracciones, descuento y escala del producto antes de redondear', () => {
    expect(decimal('0.5').multiplicar(decimal('20.150000')).redondear(2).texto()).toBe('10.08');
    expect(decimal('20.075000').restar(decimal('10')).redondear(2).texto()).toBe('10.08');
    expect(decimal('0.000001').multiplicar(decimal('5000')).redondear(2).texto()).toBe('0.01');
  });
  it('obtiene base e IGV desde el total exacto', () => {
    const base = decimal('65.00').dividir(decimal('1.18'), 2);
    expect(base.texto()).toBe('55.08');
    expect(decimal('65.00').restar(base).texto()).toBe('9.92');
    expect(decimal('0.10').sumar(decimal('0.20')).redondear(2).texto()).toBe('0.30');
  });
  it('formatea importes grandes sin conversión binaria', () => {
    expect(importeDecimal('123456789012.344999')).toBe('S/ 123,456,789,012.34');
    expect(importeDecimal('-1.005')).toBe('-S/ 1.01');
  });
  it('rechaza entradas ambiguas y escalas inválidas', () => {
    for (const entrada of ['', 'NaN', 'Infinity', '1e3', '1.2.3', ' 1', '1,20'])
      expect(() => decimal(entrada)).toThrowError('Decimal inválido.');
    expect(() => decimal('1').dividir(decimal('0'), 2)).toThrow();
    for (const escala of [-1, 1.5, 13]) expect(() => decimal('1').redondear(escala)).toThrow();
    expect(entradaDecimalValida('0.000001', 6)).toBeTrue();
    expect(entradaDecimalValida('0.0000001', 6)).toBeFalse();
    expect(entradaDecimalValida('1.001', 2)).toBeFalse();
    expect(entradaDecimalValida('1000000000000', 2)).toBeFalse();
  });
});
