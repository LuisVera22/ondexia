import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { accionConEstado } from '../../../shared/components/comunes/boton/estado-accion';
import { ConsultaRucComponent } from '../../../shared/components/comunes/consulta-ruc/consulta-ruc.component';
import { ConsultaDeRuc } from '../../../nucleo/consultas.api.service';
import {
  ConfiguracionApiService,
  CupoDeEmpresas,
  mensajeDeError,
  esPersonaNatural,
} from '../../../nucleo/configuracion.api.service';
import { AvisosService } from '../../../shared/services/avisos.service';
import { ContextoService } from '../../../shared/services/contexto.service';
import { repartirFallo } from '../../../shared/formularios/fallo-de-formulario';
import { ErrorCampoComponent } from '../../../shared/components/comunes/error-campo/error-campo.component';

/**
 * Dar de alta una empresa más.
 *
 * <h2>Esta pantalla no existía, y su ausencia era un agujero</h2>
 *
 * <p>El listado decía que las empresas se dan de alta «desde la administración
 * de la cuenta», y allí tampoco estaba: no había {@code POST} en ninguna parte,
 * así que una cuenta del plan de dos empresas no tenía forma de crear la
 * segunda por ningún camino.
 *
 * <h2>El mismo paso que el onboarding, con el mismo componente</h2>
 *
 * <p>Se pide el RUC, se consulta el padrón, y el resto se muestra en solo
 * lectura. Lo que viaja al servidor es la atestación firmada: no hay forma de
 * registrar una empresa con una razón social inventada, porque el cuerpo de la
 * petición no lleva razón social.
 *
 * <h2>El cupo se comprueba antes de pintar el formulario</h2>
 *
 * <p>Y no al enviarlo. Rellenar dos campos y una consulta para que el servidor
 * conteste «tu plan permite 2 empresas y ya tienes 2» es trabajo tirado, y el
 * mensaje llega cuando ya no sirve de nada.
 */
@Component({
  selector: 'app-nueva-empresa',
  imports: [
    EncabezadoPaginaComponent,
    ReactiveFormsModule,
    RouterModule,
    BotonComponent,
    ConsultaRucComponent,
    ErrorCampoComponent,
  ],
  templateUrl: './nueva-empresa.component.html',
})
export class NuevaEmpresaComponent {
  private readonly constructorFormulario = inject(FormBuilder);
  private readonly api = inject(ConfiguracionApiService);
  private readonly contexto = inject(ContextoService);
  private readonly avisos = inject(AvisosService);
  private readonly router = inject(Router);

  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly cupo = signal<CupoDeEmpresas | null>(null);

  /** La consulta comprobada y apta, o nulo. No es un control del formulario. */
  readonly consulta = signal<ConsultaDeRuc | null>(null);

  formulario = this.constructorFormulario.nonNullable.group({
    nombreComercial: [''],
    cuentaDetracciones: ['', [Validators.pattern(/^\d*$/)]],
    nuevoRus: [false],
  });

  /** La casilla del Nuevo RUS solo aparece con un RUC 10 comprobado. */
  readonly preguntaNuevoRus = computed(() => esPersonaNatural(this.consulta()?.datos.ruc));

  constructor() {
    void this.cargarCupo();
  }

  get controles() {
    return this.formulario.controls;
  }

  get puedeEnviar(): boolean {
    return this.consulta() !== null && this.formulario.valid && this.cupo()?.cabeOtra === true;
  }

  alVerificar(consulta: ConsultaDeRuc | null): void {
    this.consulta.set(consulta);
  }

  private async cargarCupo(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.cupo.set(await this.api.cupoDeEmpresas());
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudo comprobar cuántas empresas admite tu plan.'));
    } finally {
      this.cargando.set(false);
    }
  }

  /**
   * Registra la empresa y deja al usuario dentro de ella.
   *
   * <h2>Por qué se cambia de empresa activa al terminar</h2>
   *
   * <p>Porque lo siguiente que hace falta en una empresa nueva es configurarla
   * —series, establecimientos, certificado— y todo eso opera sobre la activa.
   * Dejar al usuario en la anterior le haría descubrir por su cuenta que tiene
   * que cambiar en el selector, y hasta entonces cada pantalla que abriera
   * mostraría los datos de la empresa equivocada.
   */
  readonly registrar = accionConEstado(async () => {
    const consulta = this.consulta();
    if (!consulta) {
      throw new Error('Consulta el RUC antes de registrar.');
    }

    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      throw new Error('Formulario incompleto');
    }

    const valores = this.formulario.getRawValue();

    try {
      const empresa = await this.api.registrarEmpresa({
        // La firma, no los campos. La razon social, el domicilio y el ubigeo
        // salen de aqui dentro.
        atestacion: consulta.atestacion,
        nombreComercial: valores.nombreComercial || null,
        cuentaDetracciones: valores.cuentaDetracciones || null,
        nuevoRus: this.preguntaNuevoRus() && valores.nuevoRus,
      });

      await this.contexto.cambiarEmpresa({
        id: empresa.id,
        nombre: empresa.razonSocial,
        detalle: `RUC ${empresa.ruc}`,
      });

      this.avisos.exito(
        `Ya trabajas en ${empresa.razonSocial}. Su casa matriz (0000) se creó sola.`,
        'Empresa registrada'
      );

      await this.router.navigate(['/configuracion/empresas', empresa.id]);
    } catch (fallo: unknown) {
      /*
       * Los fallos previsibles traen el mensaje del servidor: el RUC ya
       * registrado —con la indicacion de pedir acceso al administrador—, la
       * atestacion caducada, el cupo del plan, y el 403 de quien no administra
       * la cuenta. Todos estan mejor escritos alli.
       */
      repartirFallo(fallo, this.formulario, this.avisos, 'No se pudo registrar la empresa.');
      throw fallo;
    }
  });
}
