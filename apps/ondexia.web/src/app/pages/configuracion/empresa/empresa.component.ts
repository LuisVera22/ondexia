import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';

/**
 * Datos tributarios de la empresa emisora.
 *
 * Todo lo de esta vista se imprime en cada comprobante, así que un error
 * acá se propaga a todos los documentos emitidos.
 */
@Component({
  selector: 'app-empresa',
  imports: [EncabezadoPaginaComponent, ReactiveFormsModule],
  templateUrl: './empresa.component.html',
})
export class EmpresaComponent {
  private readonly constructorFormulario = inject(FormBuilder);

  /**
   * Con comprobantes ya emitidos el RUC queda bloqueado: cambiarlo dejaría
   * huérfana toda la numeración emitida bajo el RUC anterior.
   */
  tieneComprobantesEmitidos = true;

  guardado = false;

  formulario = this.constructorFormulario.nonNullable.group({
    ruc: [{ value: '20512345678', disabled: true }, [Validators.required, Validators.pattern(/^(10|15|17|20)\d{9}$/)]],
    razonSocial: ['Wirbi S.A.C.', [Validators.required]],
    nombreComercial: ['Wirbi'],
    domicilioFiscal: ['Av. Javier Prado Este 1234, Int. 501', [Validators.required]],
    departamento: ['Lima', [Validators.required]],
    provincia: ['Lima', [Validators.required]],
    distrito: ['San Isidro', [Validators.required]],
    ubigeo: ['150131', [Validators.required, Validators.pattern(/^\d{6}$/)]],
    telefono: ['(01) 555-1234'],
    correo: ['facturacion@wirbi.com', [Validators.email]],
  });

  get controles() {
    return this.formulario.controls;
  }

  guardar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.guardado = true;
  }
}
