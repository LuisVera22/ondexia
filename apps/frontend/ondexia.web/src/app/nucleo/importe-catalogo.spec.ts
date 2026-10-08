import { FormControl } from '@angular/forms';
import { presentarImporteCatalogo, validarImporteCatalogo } from './importe-catalogo';

describe('Importes exactos del catálogo', () => {
  it('acepta el límite NUMERIC(18,6) y muestra todos los decimales sin redondeo binario', () => {
    expect(validarImporteCatalogo(new FormControl('999999999999.123456'))).toBeNull();
    expect(presentarImporteCatalogo('999999999999.123456')).toBe('S/ 999,999,999,999.123456');
    expect(presentarImporteCatalogo('32.500000')).toBe('S/ 32.50');
    expect(presentarImporteCatalogo(null)).toBe('—');
  });
  it('rechaza valores negativos, siete decimales y trece enteros', () => {
    for (const valor of ['-1', '1.1234567', '1000000000000', 'NaN', '1e3']) {
      expect(validarImporteCatalogo(new FormControl(valor))).withContext(valor).not.toBeNull();
    }
  });
});
