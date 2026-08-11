import { Component, inject } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';

/**
 * Mi perfil.
 *
 * El usuario edita sus datos personales y su contraseña, pero **no** su rol
 * ni sus empresas asignadas: eso lo define el administrador de la cuenta.
 * Se muestran de todos modos, en solo lectura, porque saber con qué permisos
 * se está operando evita la confusión de «no me deja hacer esto» sin
 * explicación.
 */
@Component({
  selector: 'app-perfil',
  imports: [EncabezadoPaginaComponent, ReactiveFormsModule, FormsModule],
  templateUrl: './perfil.component.html',
})
export class PerfilComponent {
  private readonly constructorFormulario = inject(FormBuilder);

  datosGuardados = false;
  contrasenaGuardada = false;
  contrasenaVisible = false;

  /** Asignados por el administrador de la cuenta, no editables aquí. */
  readonly rol = 'Administrador';
  readonly empresasAsignadas = ['Wirbi S.A.C.', 'Comercial Andina S.A.C.'];
  readonly ultimoAcceso = '07/08/2026 08:42 · Lima, Perú';

  formularioDatos = this.constructorFormulario.nonNullable.group({
    nombres: ['Luis David', [Validators.required]],
    apellidos: ['Vera Vilchez', [Validators.required]],
    correo: [{ value: 'luis.vera@wirbi.com', disabled: true }],
    telefono: ['987 654 321'],
  });

  formularioContrasena = this.constructorFormulario.nonNullable.group({
    actual: ['', [Validators.required]],
    nueva: ['', [Validators.required, Validators.minLength(8)]],
    confirmacion: ['', [Validators.required]],
  });

  get controlesContrasena() {
    return this.formularioContrasena.controls;
  }

  get noCoinciden(): boolean {
    const { nueva, confirmacion } = this.formularioContrasena.getRawValue();
    return Boolean(confirmacion) && nueva !== confirmacion;
  }

  get iniciales(): string {
    const { nombres, apellidos } = this.formularioDatos.getRawValue();
    return (nombres.charAt(0) + apellidos.charAt(0)).toUpperCase();
  }

  guardarDatos(): void {
    if (this.formularioDatos.invalid) {
      this.formularioDatos.markAllAsTouched();
      return;
    }
    this.datosGuardados = true;
  }

  cambiarContrasena(): void {
    if (this.formularioContrasena.invalid || this.noCoinciden) {
      this.formularioContrasena.markAllAsTouched();
      return;
    }
    this.contrasenaGuardada = true;
    this.formularioContrasena.reset();
  }
}
