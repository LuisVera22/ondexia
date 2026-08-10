import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { DesplegableComponent, OpcionDesplegable } from '../../../shared/components/comunes/desplegable/desplegable.component';

/**
 * Preferencias de emisión e impresión de comprobantes.
 *
 * La tasa de IGV se configura y no se fija en el código: ha cambiado antes
 * y volverá a cambiar, y un valor incrustado obligaría a desplegar una
 * versión nueva el día que ocurra.
 */
@Component({
  selector: 'app-comprobantes',
  imports: [EncabezadoPaginaComponent, ReactiveFormsModule, DesplegableComponent],
  templateUrl: './comprobantes.component.html',
})
export class ComprobantesComponent {
  readonly opcionesMoneda: OpcionDesplegable[] = [
    { valor: 'PEN', etiqueta: 'Soles', detalle: 'PEN' },
    { valor: 'USD', etiqueta: 'Dólares', detalle: 'USD' },
  ];

  private readonly constructorFormulario = inject(FormBuilder);

  guardado = false;

  formulario = this.constructorFormulario.nonNullable.group({
    formatoImpresion: ['a4'],
    monedaPredeterminada: ['PEN'],
    tasaIgv: [18],
    leyendaPie: ['Autorizado mediante Resolución de Intendencia'],
    envioAutomatico: [true],
    incluirXml: [true],
  });

  guardar(): void {
    this.guardado = true;
  }
}
