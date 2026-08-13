import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { MarcoAccesoComponent } from '../../../shared/layout/marco-acceso/marco-acceso.component';
import { CONFIGURACION } from '../../../nucleo/configuracion';
import { mensajeDeError } from '../../../nucleo/configuracion.api.service';
import { SesionService } from '../../../nucleo/sesion.service';
import { ContextoService } from '../../../shared/services/contexto.service';

/**
 * Último paso del alta: los datos de la empresa.
 *
 * <p>Se llega aquí con la identidad ya probada —correo verificado, sesión de
 * Cognito abierta— pero sin fila en nuestra base. Lo que falta es el negocio:
 * RUC, razón social y domicilio. Con eso, {@code POST /api/v1/registro} crea la
 * cuenta entera de una vez y el siguiente paso ya es el panel.
 *
 * <p>No pide correo ni contraseña: eso ya lo tiene Cognito, y la identidad del
 * alta sale del token — pedirla otra vez aquí sería un campo que el backend
 * ignoraría por diseño.
 */
@Component({
  selector: 'app-registro',
  imports: [MarcoAccesoComponent, ReactiveFormsModule],
  templateUrl: './registro.component.html',
})
export class RegistroComponent {
  private readonly constructorFormulario = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly configuracion = inject(CONFIGURACION);
  private readonly sesion = inject(SesionService);
  private readonly contexto = inject(ContextoService);
  private readonly router = inject(Router);

  readonly enviando = signal(false);
  readonly error = signal<string | null>(null);

  readonly correo = this.sesion.usuario()?.correo ?? '';

  formulario = this.constructorFormulario.nonNullable.group({
    nombreTitular: [this.sesion.usuario()?.nombre ?? '', [Validators.required]],
    ruc: ['', [Validators.required, Validators.pattern(/^\d{11}$/)]],
    razonSocial: ['', [Validators.required]],
    domicilioFiscal: ['', [Validators.required]],
    ubigeo: ['', [Validators.pattern(/^$|^\d{6}$/)]],
  });

  get controles() {
    return this.formulario.controls;
  }

  async registrar(): Promise<void> {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.error.set(null);

    const valores = this.formulario.getRawValue();

    try {
      await firstValueFrom(
        this.http.post(`${this.configuracion.api}/api/v1/registro`, {
          ruc: valores.ruc,
          razonSocial: valores.razonSocial,
          domicilioFiscal: valores.domicilioFiscal,
          ubigeo: valores.ubigeo || null,
          nombreTitular: valores.nombreTitular,
        })
      );

      // Con la cuenta creada, el contexto ya resuelve: se carga y directo al
      // panel. Sin esta carga la primera pantalla saldría vacía.
      await this.contexto.cargar();
      await this.router.navigateByUrl('/', { replaceUrl: true });
    } catch (fallo: unknown) {
      /*
       * Los dos conflictos previsibles llegan con el mensaje del servidor:
       * el RUC ya registrado (con la indicación de pedir acceso al
       * administrador) y el dígito verificador que no cuadra. Ambos están
       * mejor escritos allí que cualquier genérico de aquí.
       */
      this.error.set(mensajeDeError(fallo, 'No se pudo completar el registro. Inténtalo de nuevo.'));
      this.enviando.set(false);
    }
  }

  cancelar(): void {
    // Salir a medias no deja nada a medias: el alta es una sola transacción
    // que aún no se ejecutó. Se cierra la sesión de Cognito para no dejar al
    // usuario en el limbo de «autenticado pero sin registrar».
    this.sesion.cerrar();
  }
}
