import { ConsultaDeRuc } from '../../../nucleo/consultas.api.service';
import { DatosCliente, TipoDocumentoCliente } from '../../../nucleo/ventas.api.service';

/** Comparte el contrato de alta entre la ficha y el formulario de ventas. */
export function datosAltaCliente(
  valores: DatosCliente & { tipoDocumento: TipoDocumentoCliente; numeroDocumento: string },
  consulta: ConsultaDeRuc | null,
) {
  const numeroDocumento = valores.numeroDocumento.trim();
  return {
    ...valores,
    numeroDocumento,
    atestacion: valores.tipoDocumento === 'RUC' && consulta?.datos.ruc === numeroDocumento
      ? consulta.atestacion : null,
  };
}
