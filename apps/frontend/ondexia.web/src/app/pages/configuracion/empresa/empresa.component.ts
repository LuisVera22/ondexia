import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { accionConEstado } from '../../../shared/components/comunes/boton/estado-accion';
import {
  ConfiguracionApiService,
  Empresa,
  RegimenTributario,
  esPersonaNatural,
  mensajeDeError,
} from '../../../nucleo/configuracion.api.service';
import { ConsultasApiService, ErrorDeConsulta } from '../../../nucleo/consultas.api.service';
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
 * <h2>Lo que viene de SUNAT ya no se edita</h2>
 *
 * <p>Razón social, domicilio fiscal, ubigeo, distrito, provincia y departamento
 * se muestran como texto, igual que el RUC. No son campos deshabilitados: el
 * {@code PUT} no los acepta, así que un cuerpo que los traiga los pierde.
 *
 * <p>El motivo es concreto: una razón social que no coincide con el padrón hace
 * que SUNAT rechace <em>todos</em> los comprobantes de la empresa, y el fallo
 * aparece en la primera emisión real — muy lejos de la pantalla donde alguien la
 * escribió.
 *
 * <p>Y «no editable» no significa «congelado». Cambian legítimamente en SUNAT, y
 * las empresas creadas por el onboarding antiguo los tienen tecleados a mano.
 * Para eso está «Actualizar desde SUNAT», que consulta el padrón y trae lo que
 * diga. Sin esa salida, un dato corregible sería imposible de corregir — peor
 * que dejarlo editable.
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
  private readonly consultas = inject(ConsultasApiService);
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

  /**
   * La empresa cargada, entera.
   *
   * <p>Antes eran cuatro señales sueltas —ruc, modoSunat, razón social…— y con
   * doce campos de SUNAT eso serían dieciséis. Con el objeto completo, añadir un
   * campo a la respuesta no obliga a añadir una señal y a acordarse de
   * refrescarla en tres sitios.
   */
  readonly empresa = signal<Empresa | null>(null);

  readonly razonSocialCargada = computed(() => this.empresa()?.razonSocial ?? '');
  readonly ruc = computed(() => this.empresa()?.ruc ?? '');

  /** Nunca se comprobó contra SUNAT. No es lo mismo que estar mal. */
  readonly sinVerificar = computed(() => this.empresa()?.verificadoEn == null);

  /** El estado del padrón deja emitir. Falso también si nunca se comprobó. */
  readonly aptaEnElPadron = computed(
    () => this.empresa()?.estado === 'ACTIVO' && this.empresa()?.condicion === 'HABIDO'
  );

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
    nombreComercial: [''],
    // Sin longitud fija: el formato del Banco de la Nacion no esta publicado, y
    // un largo inventado rechazaria cuentas validas.
    cuentaDetracciones: ['', [Validators.pattern(/^\d*$/)]],
    // Solo editable para una persona natural (RUC 10); ver `preguntaRegimen`.
    regimenTributario: ['OTRO' as RegimenTributario],
    // Doc 12 §3.5: el mostrador vende con existencias insuficientes y avisa,
    // salvo que la empresa lo prohíba aquí.
    permiteVentaSinStock: [true],
  });

  /** El régimen solo se pregunta a una persona natural: es la única que puede estar en el RUS. */
  readonly preguntaRegimen = computed(() => esPersonaNatural(this.empresa()?.ruc));

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

  /** Sin los guiones bajos del enumerado: «BAJA_DEFINITIVA» no se le enseña a nadie. */
  legible(valor: string | null | undefined): string {
    return (valor ?? '').replace(/_/g, ' ');
  }

  /** Cuándo se comprobó. Un estado sin fecha invita a tratarlo como permanente. */
  readonly comprobadoEn = computed(() => {
    const crudo = this.empresa()?.verificadoEn;
    if (!crudo) {
      return '';
    }
    return new Date(crudo).toLocaleString('es-PE', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      timeZone: 'America/Lima',
    });
  });

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.aplicar(await this.api.empresaPorId(this.id()));
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar los datos de la empresa.'));
    } finally {
      this.cargando.set(false);
    }
  }

  /**
   * Repuebla la pantalla con lo que devolvió el servidor, no con lo que se
   * envió: el backend recorta espacios y normaliza, y dejar la vista mostrando
   * la versión sin normalizar haría creer que se guardó otra cosa.
   */
  private aplicar(empresa: Empresa): void {
    this.empresa.set(empresa);
    this.formulario.patchValue({
      nombreComercial: empresa.nombreComercial ?? '',
      cuentaDetracciones: empresa.cuentaDetracciones ?? '',
      regimenTributario: empresa.regimenTributario,
      permiteVentaSinStock: empresa.permiteVentaSinStock,
    });
    this.aplicarModoLectura();
    this.cambios.fijarBase();
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
   * Consulta el padrón y trae lo que SUNAT diga.
   *
   * <h2>Un solo botón, sin teclear el RUC</h2>
   *
   * <p>El RUC ya se conoce, así que pedirlo otra vez sería pedir un dato que la
   * pantalla tiene delante. Se consulta, y la atestación que devuelve se manda
   * al servidor, que valida la firma y actualiza.
   *
   * <h2>Acepta un estado peor, y es a propósito</h2>
   *
   * <p>Si la empresa pasó a NO HABIDO, la actualización se guarda igual. Es lo
   * que hay que ver en pantalla: bloquearla dejaría el dato viejo, que es la
   * única versión que de verdad engaña.
   */
  readonly actualizarDesdeSunat = accionConEstado(async () => {
    if (!this.esActiva()) {
      throw new Error('Solo se puede actualizar la empresa en la que trabajas.');
    }

    try {
      const consulta = await this.consultas.consultarRuc(this.ruc());
      const antes = this.razonSocialCargada();
      this.aplicar(await this.api.verificarEmpresa(consulta.atestacion));

      if (!consulta.datos.aptaParaRegistro) {
        /*
         * Se guarda y se avisa como error, no como informacion.
         *
         * El dato se actualizo bien —la operacion no fallo— pero lo que dice es
         * un problema que alguien tiene que resolver ante SUNAT, y el aviso de
         * error es el que dura mas y el que se lee. Un «info» de cuatro segundos
         * para «tu empresa esta NO HABIDA» se pierde.
         */
        this.avisos.error(
          consulta.datos.motivoDeRechazo ?? 'Conviene revisar su situación en SUNAT.',
          'Atención: cambió la situación de esta empresa en SUNAT'
        );
        return;
      }

      const cambio = antes !== this.razonSocialCargada();
      this.avisos.exito(
        cambio
          ? `La razón social pasó a ser ${this.razonSocialCargada()}.`
          : 'Los datos ya coincidían con SUNAT.',
        'Datos actualizados desde SUNAT'
      );
    } catch (fallo: unknown) {
      const mensaje =
        fallo instanceof ErrorDeConsulta
          ? fallo.detalle.mensaje
          : mensajeDeError(fallo, 'No se pudo actualizar desde SUNAT.');
      this.avisos.error(mensaje, 'No se pudo actualizar');
      throw fallo;
    }
  });

  /**
   * Guarda lo editable de la empresa activa.
   *
   * <p>Solo el nombre comercial y la cuenta de detracciones: son los dos únicos
   * campos que no vienen de SUNAT.
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
      this.aplicar(
        await this.api.guardarEmpresa({
          nombreComercial: valores.nombreComercial || null,
          cuentaDetracciones: valores.cuentaDetracciones || null,
          // Solo si se pregunto: a una persona juridica no se le envia nada y
          // el servidor conserva OTRO.
          regimenTributario: this.preguntaRegimen() ? valores.regimenTributario : null,
          permiteVentaSinStock: valores.permiteVentaSinStock,
        })
      );

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
