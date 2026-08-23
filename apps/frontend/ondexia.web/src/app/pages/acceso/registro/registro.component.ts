import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { MarcoAccesoComponent } from '../../../shared/layout/marco-acceso/marco-acceso.component';
import { CONFIGURACION } from '../../../nucleo/configuracion';
import { SesionService } from '../../../nucleo/sesion.service';
import { VinculacionService } from '../../../nucleo/vinculacion.service';
import { ContextoService } from '../../../shared/services/contexto.service';
import { colocarEnCampos } from '../../../shared/formularios/fallo-de-formulario';
import { ErrorCampoComponent } from '../../../shared/components/comunes/error-campo/error-campo.component';
import { ConsultaRucComponent } from '../../../shared/components/comunes/consulta-ruc/consulta-ruc.component';
import { ConsultaDeRuc } from '../../../nucleo/consultas.api.service';

/**
 * Último paso del alta: los datos de la empresa.
 *
 * <p>Se llega aquí con la identidad ya probada —correo verificado, sesión de
 * Cognito abierta— pero sin fila en nuestra base. Lo que falta es el negocio: el
 * RUC. Con eso, {@code POST /api/v1/registro} crea la cuenta entera de una vez y
 * el siguiente paso ya es el panel.
 *
 * <h2>De cinco campos de empresa a uno</h2>
 *
 * <p>Antes pedía RUC, razón social, domicilio fiscal y ubigeo, y no comprobaba
 * ninguno: bastaba un RUC con el dígito verificador correcto para crear una
 * cuenta con un contribuyente inexistente y la razón social que a uno le
 * pareciera. Una razón social que no coincide con el padrón hace que SUNAT
 * rechace <em>todos</em> los comprobantes de esa empresa, y eso se descubre en la
 * primera emisión real — semanas después del alta.
 *
 * <p>Ahora se pide el RUC, se consulta, y el resto se muestra ya relleno y sin
 * poder tocarse. Lo que viaja en la petición es la atestación firmada, no los
 * campos: no hay forma de darse de alta con datos inventados, y de paso hay
 * cuatro campos menos que rellenar.
 *
 * <p>No pide correo ni contraseña: eso ya lo tiene Cognito, y la identidad del
 * alta sale del token — pedirla otra vez aquí sería un campo que el backend
 * ignoraría por diseño.
 *
 * <h2>Antes de pintar el formulario se pregunta si le invitaron</h2>
 *
 * <p>«Sin fila en nuestra base» tiene dos causas y solo una lleva a este
 * formulario. La otra es que alguien la diera de alta desde Configuración →
 * Usuarios: entonces la fila existe, sin identidad de Cognito, esperando a que
 * la persona se registre. Enseñarle el formulario a esa persona la empuja a
 * crear una segunda cuenta con su propio RUC y a dejar la invitación colgada
 * para siempre — que es exactamente lo que pasaba.
 *
 * <p>La comprobación va aquí y no en el retorno de Cognito porque esta pantalla
 * es la única puerta al formulario: se llega desde el retorno y también desde el
 * interceptor, cuando alguien vuelve al día siguiente con la sesión guardada.
 * Ponerla en los dos sitios sería la misma decisión escrita dos veces.
 */
@Component({
  selector: 'app-registro',
  imports: [
    MarcoAccesoComponent,
    ReactiveFormsModule,
    ErrorCampoComponent,
    ConsultaRucComponent,
  ],
  templateUrl: './registro.component.html',
})
export class RegistroComponent implements OnInit {
  private readonly constructorFormulario = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly configuracion = inject(CONFIGURACION);
  private readonly sesion = inject(SesionService);
  private readonly contexto = inject(ContextoService);
  private readonly vinculacion = inject(VinculacionService);
  private readonly router = inject(Router);

  readonly enviando = signal(false);
  readonly error = signal<string | null>(null);

  /**
   * Mientras dura, no se pinta nada. Enseñar el formulario y quitarlo medio
   * segundo después sería peor que esperar: quien fue invitado vería por un
   * instante que le piden un RUC que no tiene por qué tener.
   */
  readonly comprobando = signal(true);

  readonly correo = this.sesion.usuario()?.correo ?? '';

  async ngOnInit(): Promise<void> {
    const resultado = await this.vinculacion.intentar();

    if (resultado.vinculado) {
      // Ya está dentro de la cuenta que le invitó. El contexto se carga antes
      // de navegar, igual que en el retorno, para no pintar el panel vacío.
      await this.contexto.cargar();
      await this.router.navigateByUrl('/', { replaceUrl: true });
      return;
    }

    // Había invitación pero no se pudo aceptar —dos empresas invitaron al mismo
    // correo, o la desactivaron—. Se cuenta y se deja el formulario: seguir en
    // silencio la llevaría a crearse una cuenta duplicada sin saberlo.
    this.error.set(resultado.aviso);
    this.comprobando.set(false);
  }

  formulario = this.constructorFormulario.nonNullable.group({
    nombreTitular: [this.sesion.usuario()?.nombre ?? '', [Validators.required]],
    apellidoTitular: ['', [Validators.required]],
  });

  /**
   * La consulta comprobada, o {@code null} mientras no haya una que sirva.
   *
   * <p>No es un control del formulario porque lo que se envía no es un valor que
   * alguien escriba: es una firma. Metida como control tendría que validarse
   * «que no esté vacía», y ese validador no dice nada de lo que importa — que la
   * firmó `ondexia.consultas` y que el RUC está habido activo.
   */
  readonly consulta = signal<ConsultaDeRuc | null>(null);

  /** El alta solo puede enviarse con un RUC comprobado y apto. */
  get puedeEnviar(): boolean {
    return this.consulta() !== null && this.formulario.valid && !this.enviando();
  }

  alVerificar(consulta: ConsultaDeRuc | null): void {
    this.consulta.set(consulta);
  }

  get controles() {
    return this.formulario.controls;
  }

  async registrar(): Promise<void> {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    const consulta = this.consulta();
    if (!consulta) {
      // No deberia poder llegar aqui: el boton esta deshabilitado. Se comprueba
      // igual porque un `disabled` no es una garantia, y el mensaje es mejor que
      // un 400 del servidor.
      this.error.set('Consulta el RUC de tu empresa antes de continuar.');
      return;
    }

    this.enviando.set(true);
    this.error.set(null);

    const valores = this.formulario.getRawValue();

    try {
      await firstValueFrom(
        this.http.post(`${this.configuracion.api}/api/v1/registro`, {
          // La firma, no los campos. La razon social, el domicilio y el ubigeo
          // salen de aqui dentro; enviarlos aparte permitiria enviar otros.
          atestacion: consulta.atestacion,
          nombreTitular: valores.nombreTitular,
          apellidoTitular: valores.apellidoTitular,
          // El token de ACCESO de Cognito no lleva el correo —eso vive en el de
          // identidad, que el SPA sí tiene— así que se envía. El backend lo usa
          // solo para mostrar: la identidad con la que se opera es el `sub`.
          correo: this.correo,
        })
      );

      // Con la cuenta creada, el contexto ya resuelve: se carga y directo al
      // panel. Sin esta carga la primera pantalla saldría vacía.
      await this.contexto.cargar();
      await this.router.navigateByUrl('/', { replaceUrl: true });
    } catch (fallo: unknown) {
      /*
       * Los conflictos previsibles llegan con el mensaje del servidor: el RUC ya
       * registrado —con la indicación de pedir acceso al administrador— y la
       * atestación caducada, que le pasa a quien tardó en enviar el formulario.
       * Ambos están mejor escritos allí que cualquier genérico de aquí.
       */
      const colocado = colocarEnCampos(
        fallo,
        this.formulario,
        'No se pudo completar el registro. Inténtalo de nuevo.'
      );
      this.error.set(colocado.mensajeGeneral);
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
