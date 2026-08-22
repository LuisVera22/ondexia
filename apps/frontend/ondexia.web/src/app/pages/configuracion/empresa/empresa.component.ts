import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { accionConEstado } from '../../../shared/components/comunes/boton/estado-accion';
import { ConfiguracionApiService, mensajeDeError } from '../../../nucleo/configuracion.api.service';
import { AvisosService } from '../../../shared/services/avisos.service';
import { ContextoService } from '../../../shared/services/contexto.service';
import { repartirFallo } from '../../../shared/formularios/fallo-de-formulario';
import { ErrorCampoComponent } from '../../../shared/components/comunes/error-campo/error-campo.component';
import { AccionesGuardadoComponent } from '../../../shared/components/comunes/acciones-guardado/acciones-guardado.component';
import { seguirCambios } from '../../../shared/formularios/cambios';
import { ConCambiosSinGuardar } from '../../../shared/formularios/salida-con-cambios.guard';

/**
 * Datos tributarios de una empresa emisora.
 *
 * <p>Todo lo de esta vista se imprime en cada comprobante, así que un error
 * aquí se propaga a todos los documentos emitidos.
 *
 * <h2>Se lee cualquiera de las suyas; se edita solo la activa</h2>
 *
 * <p>La ficha se abre desde el listado, así que el id viene de la URL y puede
 * ser el de una empresa en la que no se está trabajando. Leerla así es
 * deliberado: obligar a cambiar de empresa para <em>consultar</em> un domicilio
 * fiscal haría pagar un cambio de permisos, de menú y de datos por una
 * pregunta.
 *
 * <p>Guardar es otra cosa. La bitácora archiva cada cambio bajo la empresa
 * activa —lo hace el adaptador, y la política de aislamiento de la tabla solo
 * admite ese valor—, de modo que editar la empresa B mientras la activa es A
 * dejaría el rastro en el historial de A sin dar ningún error. Por eso no hay
 * {@code PUT /empresas/&#123;id&#125;} y por eso esta pantalla se muestra en
 * solo lectura hasta que se pasa a trabajar en la empresa que se está viendo.
 *
 * <h2>Qué no está en el formulario, y por qué</h2>
 *
 * <p>La maqueta tenía además <em>departamento</em>, <em>provincia</em>,
 * <em>distrito</em>, <em>teléfono</em> y <em>correo</em>. Ninguno existe en el
 * esquema, así que al guardar se habrían perdido en silencio — un campo que
 * acepta lo que escribes y luego lo tira es peor que un campo ausente.
 *
 * <p>Los tres primeros se derivan del ubigeo, que sí se guarda y es lo que
 * SUNAT necesita; mostrarlos requiere un catálogo de ubigeos que todavía no
 * existe. Teléfono y correo necesitarían columnas nuevas: es una migración, y
 * por tanto una decisión, no un olvido.
 */
@Component({
  selector: 'app-empresa',
  imports: [
    EncabezadoPaginaComponent,
    ReactiveFormsModule,
    RouterModule,
    BotonComponent,
    ErrorCampoComponent,
    AccionesGuardadoComponent,
  ],
  templateUrl: './empresa.component.html',
})
export class EmpresaComponent implements ConCambiosSinGuardar {
  private readonly constructorFormulario = inject(FormBuilder);
  private readonly api = inject(ConfiguracionApiService);
  private readonly contexto = inject(ContextoService);
  private readonly avisos = inject(AvisosService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly cargando = signal(true);

  /**
   * Solo el fallo al CARGAR la ficha, que se pinta en lugar del formulario.
   *
   * <p>El resultado de guardar o de cambiar de empresa ya no vive aquí: va a un
   * aviso. La diferencia es que esto no es el resultado de una acción del
   * usuario —no ha pulsado nada— y no hay formulario que mostrar detrás, así
   * que un aviso flotante sobre una pantalla vacía no diría qué hacer.
   */
  readonly error = signal<string | null>(null);

  readonly id = signal('');
  readonly ruc = signal('');
  readonly modoSunat = signal('');
  readonly razonSocialCargada = signal('');

  /**
   * Si la empresa de la ficha es sobre la que se está trabajando.
   *
   * Es {@code computed} y no un booleano fijado al cargar porque cambia sin que
   * esta pantalla vuelva a pedir nada: al pasar a trabajar en esta empresa, o
   * al cambiarla desde el selector de la barra superior.
   */
  readonly esActiva = computed(
    () => this.id() !== '' && String(this.contexto.empresaActiva()?.id ?? '') === this.id()
  );

  formulario = this.constructorFormulario.nonNullable.group({
    razonSocial: ['', [Validators.required]],
    nombreComercial: [''],
    domicilioFiscal: ['', [Validators.required]],
    ubigeo: ['', [Validators.pattern(/^$|^\d{6}$/)]],
  });

  /**
   * Se declara aqui y no dentro de {@code cargar} porque se suscribe a los
   * cambios del formulario, y eso necesita el contexto de inyeccion del campo
   * para darse de baja cuando la pantalla se destruye.
   */
  readonly cambios = seguirCambios(this.formulario);

  constructor() {
    const id = this.ruta.snapshot.paramMap.get('id');
    if (!id) {
      void this.router.navigate(['/configuracion/empresas']);
      return;
    }

    this.id.set(id);
    void this.cargar();
  }

  get controles() {
    return this.formulario.controls;
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const empresa = await this.api.empresaPorId(this.id());
      this.ruc.set(empresa.ruc);
      this.modoSunat.set(empresa.modoSunat);
      this.razonSocialCargada.set(empresa.razonSocial);
      this.formulario.patchValue({
        razonSocial: empresa.razonSocial,
        nombreComercial: empresa.nombreComercial ?? '',
        domicilioFiscal: empresa.domicilioFiscal,
        ubigeo: empresa.ubigeo ?? '',
      });
      this.aplicarModoLectura();
      // Lo que se acaba de cargar es el punto de partida: a partir de aqui,
      // cualquier diferencia es un cambio del usuario.
      this.cambios.fijarBase();
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar los datos de la empresa.'));
    } finally {
      this.cargando.set(false);
    }
  }

  /**
   * Deshabilita el formulario cuando la empresa no es la activa.
   *
   * <p>Se deshabilitan los controles en lugar de esconder el botón de guardar:
   * un formulario que se deja escribir y luego no guarda pierde lo tecleado, y
   * el usuario no tiene forma de saber por qué.
   */
  private aplicarModoLectura(): void {
    if (this.esActiva()) {
      this.formulario.enable();
    } else {
      this.formulario.disable();
    }
  }

  /**
   * Pasa a trabajar en esta empresa, que es lo que habilita la edición.
   *
   * <p>Recarga el contexto entero —permisos incluidos— porque los permisos son
   * por empresa.
   *
   * <h2>El aviso va antes de recargar la ficha, y es deliberado</h2>
   *
   * <p>El cambio de empresa ya ocurrió en ese punto. Si en la nueva el usuario
   * no alcanza la configuración —el caso del rol Vendedor—, la recarga responde
   * 403 y el interceptor global lo lleva a {@code /sin-permisos}. Como los
   * avisos viven en el marco y no en la pantalla, este sobrevive a esa
   * navegación y es lo único que explica por qué acabó ahí.
   *
   * <p>Anunciarlo después, al terminar bien la recarga, dejaba al usuario en una
   * pantalla de «sin permisos» sin ninguna pista de que había cambiado de
   * empresa él mismo un segundo antes.
   */
  readonly cambiarDeEmpresa = accionConEstado(async () => {
    await this.contexto.cambiarEmpresa({
      id: this.id(),
      nombre: this.razonSocialCargada(),
      detalle: `RUC ${this.ruc()}`,
    });

    // Sin punto final: muchas razones sociales acaban en uno —«E.I.R.L.»,
    // «S.A.C.»— y quedaban dos seguidos.
    this.avisos.exito(
      `Ahora trabajas en ${this.razonSocialCargada()}`,
      'Empresa activa cambiada'
    );

    await this.cargar();
  });

  /**
   * Guarda los datos fiscales de la empresa activa.
   *
   * <p>El aviso es obligatorio aquí y no basta el botón: lo que cambia son los
   * datos que se imprimen en los comprobantes, y el formulario se queda igual
   * de aspecto tras guardar. Sin aviso, la única señal sería un check de un
   * segundo y medio en el botón.
   */
  readonly guardar = accionConEstado(async () => {
    if (!this.esActiva()) {
      throw new Error('Solo se puede editar la empresa en la que trabajas.');
    }

    if (this.formulario.invalid) {
      // Marcar como tocado revela los mensajes de los campos que el usuario
      // nunca llegó a visitar. Los errores de validación se quedan junto al
      // campo: un aviso en la esquina alejaría el mensaje de lo que corregir.
      this.formulario.markAllAsTouched();
      throw new Error('Formulario incompleto');
    }

    const valores = this.formulario.getRawValue();

    try {
      const empresa = await this.api.guardarEmpresa({
        razonSocial: valores.razonSocial,
        nombreComercial: valores.nombreComercial || null,
        domicilioFiscal: valores.domicilioFiscal,
        ubigeo: valores.ubigeo || null,
      });

      // Se repuebla con lo que devolvió el servidor, no con lo que se envió: el
      // backend recorta espacios y normaliza, y dejar la pantalla mostrando la
      // versión sin normalizar haría creer que se guardó otra cosa.
      this.razonSocialCargada.set(empresa.razonSocial);
      this.formulario.patchValue({
        razonSocial: empresa.razonSocial,
        nombreComercial: empresa.nombreComercial ?? '',
        domicilioFiscal: empresa.domicilioFiscal,
        ubigeo: empresa.ubigeo ?? '',
      });
      this.cambios.fijarBase();

      this.avisos.exito(
        'Se aplicarán a los comprobantes que se emitan desde ahora.',
        'Datos de la empresa guardados'
      );
    } catch (fallo: unknown) {
      repartirFallo(fallo, this.formulario, this.avisos, 'No se pudieron guardar los cambios.');
      throw fallo;
    }
  });

  hayCambiosSinGuardar(): boolean {
    return this.cambios.hayCambios();
  }
}
