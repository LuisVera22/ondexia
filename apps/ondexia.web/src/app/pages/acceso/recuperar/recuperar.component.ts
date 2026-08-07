import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuthPageLayoutComponent } from '../../../shared/layout/auth-page-layout/auth-page-layout.component';

/**
 * Recuperación de contraseña.
 *
 * La confirmación no revela si el correo existe: responder «ese correo no
 * está registrado» convierte el formulario en un verificador de cuentas
 * para cualquiera que quiera enumerarlas.
 */
@Component({
  selector: 'app-recuperar',
  imports: [AuthPageLayoutComponent, ReactiveFormsModule, RouterModule],
  templateUrl: './recuperar.component.html',
})
export class RecuperarComponent {
  private readonly constructorFormulario = inject(FormBuilder);

  enviado = false;

  formulario = this.constructorFormulario.nonNullable.group({
    correo: ['', [Validators.required, Validators.email]],
  });

  get correo() {
    return this.formulario.controls.correo;
  }

  enviar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.enviado = true;
  }
}
