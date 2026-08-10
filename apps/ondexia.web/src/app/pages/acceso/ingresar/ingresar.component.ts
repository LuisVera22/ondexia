import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MarcoAccesoComponent } from '../../../shared/layout/marco-acceso/marco-acceso.component';

/**
 * Inicio de sesión.
 *
 * Sin backend todavía: el envío navega al panel sin validar credenciales.
 * La validación de formato sí es real, para que el recorrido muestre cómo
 * se comporta el formulario ante datos incompletos.
 */
@Component({
  selector: 'app-ingresar',
  imports: [MarcoAccesoComponent, ReactiveFormsModule, RouterModule],
  templateUrl: './ingresar.component.html',
})
export class IngresarComponent {
  private readonly constructorFormulario = inject(FormBuilder);
  private readonly router = inject(Router);

  contrasenaVisible = false;
  enviando = false;

  formulario = this.constructorFormulario.nonNullable.group({
    correo: ['', [Validators.required, Validators.email]],
    contrasena: ['', [Validators.required, Validators.minLength(8)]],
    recordarme: [false],
  });

  get correo() {
    return this.formulario.controls.correo;
  }

  get contrasena() {
    return this.formulario.controls.contrasena;
  }

  alternarContrasena(): void {
    this.contrasenaVisible = !this.contrasenaVisible;
  }

  ingresar(): void {
    if (this.formulario.invalid) {
      // Marcar como tocado revela los mensajes de error de los campos que
      // el usuario nunca llegó a visitar.
      this.formulario.markAllAsTouched();
      return;
    }
    this.enviando = true;
    this.router.navigate(['/']);
  }
}
