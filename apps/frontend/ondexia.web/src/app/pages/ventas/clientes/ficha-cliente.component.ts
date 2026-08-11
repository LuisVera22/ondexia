import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { DesplegableComponent, OpcionDesplegable } from '../../../shared/components/comunes/desplegable/desplegable.component';

/**
 * Ficha de cliente.
 *
 * La validación del documento cambia con su tipo: el RUC tiene 11 dígitos
 * y empieza por 10, 15, 17 o 20; el DNI tiene 8. Validar ambos con la misma
 * regla dejaría pasar documentos que SUNAT rechaza al emitir.
 */
@Component({
  selector: 'app-ficha-cliente',
  imports: [EncabezadoPaginaComponent, ReactiveFormsModule, RouterModule, DesplegableComponent],
  templateUrl: './ficha-cliente.component.html',
})
export class FichaClienteComponent {
  readonly opcionesTipoDocumento: OpcionDesplegable[] = [
    { valor: 'RUC', etiqueta: 'RUC' },
    { valor: 'DNI', etiqueta: 'DNI' },
    { valor: 'CE', etiqueta: 'Carné de extranjería' },
    { valor: 'PAS', etiqueta: 'Pasaporte' },
  ];

  readonly opcionesTipoPrecio: OpcionDesplegable[] = [
    { valor: 'Público', etiqueta: 'Público' },
    { valor: 'Mayorista', etiqueta: 'Mayorista' },
    { valor: 'Distribuidor', etiqueta: 'Distribuidor' },
  ];

  private readonly ruta = inject(ActivatedRoute);
  private readonly constructorFormulario = inject(FormBuilder);

  esNuevo = false;
  consultandoPadron = false;

  formulario = this.constructorFormulario.nonNullable.group({
    tipoDocumento: ['RUC', [Validators.required]],
    numeroDocumento: ['20512345678', [Validators.required]],
    razonSocial: ['Distribuidora Andina S.A.C.', [Validators.required]],
    nombreComercial: ['Andina'],
    direccion: ['Av. Colonial 2450', [Validators.required]],
    distrito: ['Cercado de Lima'],
    correo: ['compras@andina.com', [Validators.email]],
    telefono: ['(01) 423-8800'],
    tipoPrecio: ['Mayorista'],
    observaciones: [''],
  });

  ngOnInit(): void {
    this.esNuevo = this.ruta.snapshot.paramMap.get('id') === 'nuevo';
    if (this.esNuevo) {
      this.formulario.reset({
        tipoDocumento: 'RUC',
        numeroDocumento: '',
        razonSocial: '',
        nombreComercial: '',
        direccion: '',
        distrito: '',
        correo: '',
        telefono: '',
        tipoPrecio: 'Público',
        observaciones: '',
      });
    }
    this.aplicarReglasDocumento();
    this.formulario.controls.tipoDocumento.valueChanges.subscribe(() => this.aplicarReglasDocumento());
  }

  get controles() {
    return this.formulario.controls;
  }

  get esRuc(): boolean {
    return this.controles.tipoDocumento.value === 'RUC';
  }

  get titulo(): string {
    return this.esNuevo ? 'Nuevo cliente' : this.controles.razonSocial.value;
  }

  get etiquetaNombre(): string {
    return this.esRuc ? 'Razón social' : 'Nombres y apellidos';
  }

  /** El validador del número depende del tipo de documento elegido. */
  private aplicarReglasDocumento(): void {
    const control = this.controles.numeroDocumento;
    const reglas = this.esRuc
      ? [Validators.required, Validators.pattern(/^(10|15|17|20)\d{9}$/)]
      : [Validators.required, Validators.pattern(/^\d{8}$/)];
    control.setValidators(reglas);
    control.updateValueAndValidity({ emitEvent: false });
  }

  consultarPadron(): void {
    this.consultandoPadron = true;
    // Sin backend todavía: la consulta al padrón de SUNAT llegará después.
    this.consultandoPadron = false;
  }
}
