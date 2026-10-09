import { AbstractControl, ValidationErrors } from '@angular/forms';

/** Decimal exacto NUMERIC(18,6), sin convertir importes a coma flotante. */
export function validarImporteCatalogo(control: AbstractControl): ValidationErrors | null {
  const valor = control.value;
  return valor === null || valor === '' || /^\d{1,12}(\.\d{1,6})?$/.test(valor)
    ? null : { importeInvalido: true };
}

export function presentarImporteCatalogo(valor: string | null): string {
  if (valor === null) return '—';
  const [entero, fraccion = ''] = valor.split('.');
  const agrupado = entero.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `S/ ${agrupado}.${fraccion.replace(/0+$/, '').padEnd(2, '0')}`;
}
