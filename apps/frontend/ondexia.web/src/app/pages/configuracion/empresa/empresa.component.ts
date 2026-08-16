import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { ConfiguracionApiService, mensajeDeError } from '../../../nucleo/configuracion.api.service';
import { ContextoService } from '../../../shared/services/contexto.service';

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
  imports: [EncabezadoPaginaComponent, ReactiveFormsModule, RouterModule],
  templateUrl: './empresa.component.html',
})
export class EmpresaComponent {
  private readonly constructorFormulario = inject(FormBuilder);
  private readonly api = inject(ConfiguracionApiService);
  private readonly contexto = inject(ContextoService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly guardado = signal(false);
  readonly cambiandoEmpresa = signal(false);
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
   * por empresa. Si en esta el usuario no llega a la configuración, la recarga
   * siguiente responderá 403 y se verá el mensaje del servidor: es preferible a
   * adivinarlo aquí con una lista de permisos que habría que mantener a mano.
   */
  async trabajarEnEstaEmpresa(): Promise<void> {
    this.cambiandoEmpresa.set(true);
    this.error.set(null);
    try {
      await this.contexto.cambiarEmpresa({
        id: this.id(),
        nombre: this.razonSocialCargada(),
        detalle: `RUC ${this.ruc()}`,
      });
      await this.cargar();
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudo cambiar de empresa.'));
    } finally {
      this.cambiandoEmpresa.set(false);
    }
  }

  async guardar(): Promise<void> {
    if (!this.esActiva()) {
      return;
    }

    if (this.formulario.invalid) {
      // Marcar como tocado revela los mensajes de los campos que el usuario
      // nunca llegó a visitar.
      this.formulario.markAllAsTouched();
      return;
    }

    this.guardando.set(true);
    this.guardado.set(false);
    this.error.set(null);

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
      this.formulario.markAsPristine();
      this.guardado.set(true);
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron guardar los cambios.'));
    } finally {
      this.guardando.set(false);
    }
  }
}
