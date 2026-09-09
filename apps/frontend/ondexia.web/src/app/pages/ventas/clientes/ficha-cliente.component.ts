import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import {
  DesplegableComponent,
  OpcionDesplegable,
} from '../../../shared/components/comunes/desplegable/desplegable.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { accionConEstado } from '../../../shared/components/comunes/boton/estado-accion';
import { ErrorCampoComponent } from '../../../shared/components/comunes/error-campo/error-campo.component';
import { AccionesGuardadoComponent } from '../../../shared/components/comunes/acciones-guardado/acciones-guardado.component';
import { seguirCambios } from '../../../shared/formularios/cambios';
import { ConCambiosSinGuardar } from '../../../shared/formularios/salida-con-cambios.guard';
import { repartirFallo } from '../../../shared/formularios/fallo-de-formulario';
import { ClienteApi, TipoDocumentoCliente, VentasApiService } from '../../../nucleo/ventas.api.service';
import { ConsultaDeRuc, ConsultasApiService, ErrorDeConsulta } from '../../../nucleo/consultas.api.service';
import { mensajeDeError } from '../../../nucleo/errores';
import { AvisosService } from '../../../shared/services/avisos.service';
import { ContextoService } from '../../../shared/services/contexto.service';

/**
 * Ficha de un cliente: alta y edición.
 *
 * <p>Con RUC, la consulta al padrón devuelve una atestación y de ella salen la
 * razón social y el domicilio: el servidor no acepta esos campos tecleados si
 * viene la firma. Con DNI, la consulta a RENIEC solo rellena el nombre: no hay
 * nada fiscal que firmar, y si el servicio no está el nombre se teclea.
 *
 * <p>El documento no se cambia después del alta: identifica al cliente ante
 * SUNAT y ya está impreso en sus comprobantes. Si se tecleó mal, se crea otro.
 */
@Component({
  selector: 'app-ficha-cliente',
  imports: [
    EncabezadoPaginaComponent,
    ReactiveFormsModule,
    RouterModule,
    DesplegableComponent,
    BotonComponent,
    ErrorCampoComponent,
    AccionesGuardadoComponent,
  ],
  templateUrl: './ficha-cliente.component.html',
})
export class FichaClienteComponent implements ConCambiosSinGuardar {
  private readonly api = inject(VentasApiService);
  private readonly consultas = inject(ConsultasApiService);
  private readonly contexto = inject(ContextoService);
  private readonly avisos = inject(AvisosService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly constructorFormulario = inject(FormBuilder);

  readonly opcionesTipo: OpcionDesplegable[] = [
    { valor: 'RUC', etiqueta: 'RUC' },
    { valor: 'DNI', etiqueta: 'DNI' },
    { valor: 'CARNET_EXTRANJERIA', etiqueta: 'Carné de extranjería' },
    { valor: 'PASAPORTE', etiqueta: 'Pasaporte' },
  ];

  readonly esNuevo = signal(false);
  readonly id = signal('');
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly cliente = signal<ClienteApi | null>(null);

  /** La última consulta al padrón, para mandar su atestación al guardar. */
  readonly consultaRuc = signal<ConsultaDeRuc | null>(null);
  readonly consultando = signal(false);
  readonly avisoConsulta = signal<string | null>(null);

  readonly puedeEditar = computed(() => this.contexto.puede('ventas.cliente:editar'));

  formulario = this.constructorFormulario.nonNullable.group({
    tipoDocumento: ['RUC' as TipoDocumentoCliente, [Validators.required]],
    numeroDocumento: ['', [Validators.required]],
    nombre: ['', [Validators.required, Validators.maxLength(300)]],
    direccion: [''],
    correo: ['', [Validators.email]],
    telefono: [''],
  });

  readonly cambios = seguirCambios(this.formulario);

  constructor() {
    const id = this.ruta.snapshot.paramMap.get('id');
    if (!id) {
      void this.router.navigate(['/ventas/clientes']);
      return;
    }
    this.esNuevo.set(id === 'nuevo');
    this.id.set(id);
    this.aplicarReglasDocumento();
    this.formulario.controls.tipoDocumento.valueChanges.subscribe(() => {
      this.consultaRuc.set(null);
      this.avisoConsulta.set(null);
      this.aplicarReglasDocumento();
    });
    // Cambiar el número invalida la consulta anterior: la atestación es de OTRO RUC.
    this.formulario.controls.numeroDocumento.valueChanges.subscribe((numero) => {
      if (this.consultaRuc() && this.consultaRuc()!.datos.ruc !== numero) {
        this.consultaRuc.set(null);
      }
    });
    void this.cargar();
  }

  get controles() {
    return this.formulario.controls;
  }

  get tipo(): TipoDocumentoCliente {
    return this.controles.tipoDocumento.value;
  }

  get esRuc(): boolean {
    return this.tipo === 'RUC';
  }

  get esDni(): boolean {
    return this.tipo === 'DNI';
  }

  get titulo(): string {
    return this.esNuevo() ? 'Nuevo cliente' : this.cliente()?.nombre || 'Cliente';
  }

  get etiquetaNombre(): string {
    return this.esRuc ? 'Razón social' : 'Nombres y apellidos';
  }

  get mensajeDocumento(): string {
    switch (this.tipo) {
      case 'RUC':
        return 'El RUC son once dígitos y empieza por 10, 15, 17 o 20.';
      case 'DNI':
        return 'El DNI son ocho dígitos.';
      default:
        return 'Letras y dígitos, hasta 12 caracteres.';
    }
  }

  /** El validador del número depende del tipo elegido. El servidor vuelve a comprobarlo. */
  private aplicarReglasDocumento(): void {
    const control = this.controles.numeroDocumento;
    const reglas =
      this.tipo === 'RUC'
        ? [Validators.required, Validators.pattern(/^(10|15|17|20)\d{9}$/)]
        : this.tipo === 'DNI'
          ? [Validators.required, Validators.pattern(/^\d{8}$/)]
          : [Validators.required, Validators.pattern(/^[A-Za-z0-9]{1,12}$/)];
    control.setValidators(reglas);
    control.updateValueAndValidity({ emitEvent: false });
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      if (this.esNuevo()) {
        this.cambios.fijarBase();
        return;
      }
      this.aplicar(await this.api.cliente(this.id()));
      this.formulario.controls.tipoDocumento.disable({ emitEvent: false });
      this.formulario.controls.numeroDocumento.disable({ emitEvent: false });
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudo cargar el cliente.'));
    } finally {
      this.cargando.set(false);
    }
  }

  private aplicar(cliente: ClienteApi): void {
    this.cliente.set(cliente);
    this.formulario.patchValue(
      {
        tipoDocumento: cliente.tipoDocumento,
        numeroDocumento: cliente.numeroDocumento,
        nombre: cliente.nombre,
        direccion: cliente.direccion ?? '',
        correo: cliente.correo ?? '',
        telefono: cliente.telefono ?? '',
      },
      { emitEvent: false }
    );
    this.cambios.fijarBase();
  }

  /**
   * Consulta el documento y rellena lo que el servicio sabe.
   *
   * <p>Con RUC se guarda la atestación para el alta; con DNI solo se copia el
   * nombre. Un fallo de la consulta no impide seguir: el nombre se teclea y,
   * en el caso del RUC, el cliente queda «sin verificar» hasta que el servicio
   * vuelva.
   */
  readonly consultar = accionConEstado(async () => {
    const numero = this.controles.numeroDocumento.value.trim();
    if (this.controles.numeroDocumento.invalid) {
      this.controles.numeroDocumento.markAsTouched();
      throw new Error('Documento incompleto');
    }
    this.avisoConsulta.set(null);
    this.consultando.set(true);
    try {
      if (this.esRuc) {
        const consulta = await this.consultas.consultarRuc(numero);
        this.consultaRuc.set(consulta);
        this.formulario.patchValue({
          nombre: consulta.datos.razonSocial,
          direccion: consulta.datos.domicilioFiscal ?? this.controles.direccion.value,
        });
        if (!consulta.datos.aptaParaRegistro) {
          // Un cliente puede estar de baja o no habido y aun asi se le factura
          // lo ya vendido; se avisa, no se impide.
          this.avisoConsulta.set(consulta.datos.motivoDeRechazo ?? 'SUNAT no lo tiene como activo y habido.');
        }
      } else if (this.esDni) {
        const datos = await this.consultas.consultarDni(numero);
        this.formulario.patchValue({ nombre: datos.nombreCompleto });
      }
    } catch (fallo: unknown) {
      const mensaje =
        fallo instanceof ErrorDeConsulta ? fallo.detalle.mensaje : 'No se pudo consultar el documento.';
      this.avisoConsulta.set(`${mensaje} Puedes escribir el nombre a mano.`);
      throw fallo;
    } finally {
      this.consultando.set(false);
    }
  });

  readonly guardar = accionConEstado(async () => {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      throw new Error('Formulario incompleto');
    }
    const valores = this.formulario.getRawValue();
    const datos = {
      nombre: valores.nombre,
      direccion: valores.direccion || null,
      correo: valores.correo || null,
      telefono: valores.telefono || null,
    };
    try {
      if (this.esNuevo()) {
        const creado = await this.api.crearCliente({
          ...datos,
          tipoDocumento: valores.tipoDocumento,
          numeroDocumento: valores.numeroDocumento.trim(),
          // Solo si la consulta fue de ESTE número; de otro, el servidor la rechaza.
          atestacion:
            this.esRuc && this.consultaRuc()?.datos.ruc === valores.numeroDocumento.trim()
              ? this.consultaRuc()!.atestacion
              : null,
        });
        this.cambios.fijarBase();
        this.avisos.exito(`${creado.numeroDocumento} · ${creado.nombre}`, 'Cliente registrado');
        await this.router.navigate(['/ventas/clientes', creado.id]);
        return;
      }
      this.aplicar(await this.api.actualizarCliente(this.id(), datos));
      this.avisos.exito('Los datos del cliente se guardaron.');
    } catch (fallo: unknown) {
      repartirFallo(fallo, this.formulario, this.avisos, 'No se pudo guardar el cliente.');
      throw fallo;
    }
  });

  /** Para un RUC ya registrado: consultar y aplicar el padrón en un paso. */
  readonly verificar = accionConEstado(async () => {
    const cliente = this.cliente();
    if (!cliente) {
      return;
    }
    try {
      const consulta = await this.consultas.consultarRuc(cliente.numeroDocumento);
      this.aplicar(await this.api.verificarCliente(cliente.id, consulta.atestacion));
      this.avisos.exito('La razón social y el domicilio son ahora los del padrón.', 'RUC verificado');
    } catch (fallo: unknown) {
      const mensaje =
        fallo instanceof ErrorDeConsulta ? fallo.detalle.mensaje : mensajeDeError(fallo, 'No se pudo verificar el RUC.');
      this.avisos.error(mensaje);
      throw fallo;
    }
  });

  async cambiarEstado(activo: boolean): Promise<void> {
    try {
      this.aplicar(await this.api.cambiarEstadoCliente(this.id(), activo));
      this.avisos.exito(activo ? 'El cliente vuelve a estar activo.' : 'El cliente quedó inactivo.');
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo cambiar el estado.'));
    }
  }

  fecha(instante: string): string {
    return new Date(instante).toLocaleString('es-PE', { dateStyle: 'long', timeStyle: 'short' });
  }

  hayCambiosSinGuardar(): boolean {
    return this.cambios.hayCambios();
  }
}
