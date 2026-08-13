import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { ConfiguracionApiService, mensajeDeError } from '../../../nucleo/configuracion.api.service';

/**
 * Datos tributarios de la empresa emisora.
 *
 * <p>Todo lo de esta vista se imprime en cada comprobante, así que un error
 * aquí se propaga a todos los documentos emitidos.
 *
 * <h2>Qué desapareció al conectarla, y por qué</h2>
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
  imports: [EncabezadoPaginaComponent, ReactiveFormsModule],
  templateUrl: './empresa.component.html',
})
export class EmpresaComponent {
  private readonly constructorFormulario = inject(FormBuilder);
  private readonly api = inject(ConfiguracionApiService);

  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly guardado = signal(false);
  readonly error = signal<string | null>(null);

  readonly ruc = signal('');
  readonly modoSunat = signal('');

  formulario = this.constructorFormulario.nonNullable.group({
    razonSocial: ['', [Validators.required]],
    nombreComercial: [''],
    domicilioFiscal: ['', [Validators.required]],
    ubigeo: ['', [Validators.pattern(/^$|^\d{6}$/)]],
  });

  constructor() {
    void this.cargar();
  }

  get controles() {
    return this.formulario.controls;
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      const empresa = await this.api.empresa();
      this.ruc.set(empresa.ruc);
      this.modoSunat.set(empresa.modoSunat);
      this.formulario.patchValue({
        razonSocial: empresa.razonSocial,
        nombreComercial: empresa.nombreComercial ?? '',
        domicilioFiscal: empresa.domicilioFiscal,
        ubigeo: empresa.ubigeo ?? '',
      });
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar los datos de la empresa.'));
    } finally {
      this.cargando.set(false);
    }
  }

  async guardar(): Promise<void> {
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
