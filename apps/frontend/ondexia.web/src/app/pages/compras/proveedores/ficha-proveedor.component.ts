import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { DesplegableComponent, OpcionDesplegable } from '../../../shared/components/comunes/desplegable/desplegable.component';

/**
 * Ficha de proveedor.
 *
 * A diferencia del cliente, el proveedor siempre exige RUC: solo un sujeto
 * con RUC puede emitir una factura de compra. Las adquisiciones a personas
 * sin RUC se documentan con liquidación de compra, que es un flujo aparte.
 */
@Component({
  selector: 'app-ficha-proveedor',
  imports: [EncabezadoPaginaComponent, ReactiveFormsModule, RouterModule, DesplegableComponent],
  templateUrl: './ficha-proveedor.component.html',
})
export class FichaProveedorComponent {
  readonly opcionesCondicionPago: OpcionDesplegable[] = [
    { valor: 'Contado', etiqueta: 'Contado' },
    { valor: 'Crédito 15 días', etiqueta: 'Crédito 15 días' },
    { valor: 'Crédito 30 días', etiqueta: 'Crédito 30 días' },
    { valor: 'Crédito 45 días', etiqueta: 'Crédito 45 días' },
    { valor: 'Crédito 60 días', etiqueta: 'Crédito 60 días' },
  ];

  private readonly ruta = inject(ActivatedRoute);
  private readonly constructorFormulario = inject(FormBuilder);

  esNuevo = false;

  formulario = this.constructorFormulario.nonNullable.group({
    numeroDocumento: ['20100113610', [Validators.required, Validators.pattern(/^(10|15|17|20)\d{9}$/)]],
    razonSocial: ['Cementos Pacasmayo S.A.A.', [Validators.required]],
    direccion: ['Calle La Colonia 150, Urb. El Vivero', [Validators.required]],
    distrito: ['Santiago de Surco'],
    contacto: ['Ana Torres'],
    correo: ['ventas@pacasmayo.com.pe', [Validators.email]],
    telefono: ['(01) 317-6000'],
    condicionPago: ['Crédito 30 días'],
    diasCredito: [30],
    observaciones: [''],
  });

  ngOnInit(): void {
    this.esNuevo = this.ruta.snapshot.paramMap.get('id') === 'nuevo';
    if (this.esNuevo) {
      this.formulario.reset({
        numeroDocumento: '',
        razonSocial: '',
        direccion: '',
        distrito: '',
        contacto: '',
        correo: '',
        telefono: '',
        condicionPago: 'Contado',
        diasCredito: 0,
        observaciones: '',
      });
    }
  }

  get controles() {
    return this.formulario.controls;
  }

  get titulo(): string {
    return this.esNuevo ? 'Nuevo proveedor' : this.controles.razonSocial.value;
  }

  get esCredito(): boolean {
    return this.controles.condicionPago.value !== 'Contado';
  }
}
