import { Component, DestroyRef, computed, effect, inject, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ModalComponent } from '../../../shared/components/comunes/modal/modal.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { ContextoService } from '../../../shared/services/contexto.service';
import { ConsultaDeRuc, ConsultasApiService } from '../../../nucleo/consultas.api.service';
import { ClienteApi, VentasApiService } from '../../../nucleo/ventas.api.service';
import { interpretarError, mensajeDeError } from '../../../nucleo/errores';
import { datosAltaCliente } from './datos-alta-cliente';

@Component({
  selector: 'app-alta-cliente-venta',
  imports: [ReactiveFormsModule, ModalComponent, BotonComponent],
  templateUrl: './alta-cliente-venta.component.html',
})
export class AltaClienteVentaComponent {
  private readonly ventas = inject(VentasApiService);
  private readonly consultas = inject(ConsultasApiService);
  private readonly contexto = inject(ContextoService);
  private readonly destruccion = inject(DestroyRef);
  private readonly constructorFormulario = inject(FormBuilder);
  private vigente = true;
  private consultaNumero = 0;
  private readonly contextoInicial = this.claveContexto();

  readonly cerrar = output<void>();
  readonly seleccionado = output<ClienteApi>();
  readonly consultando = signal(false);
  readonly guardando = signal(false);
  readonly revisando = signal(false);
  readonly fallo = signal<string | null>(null);
  readonly aviso = signal<string | null>(null);
  readonly permiteManual = signal(false);
  readonly consulta = signal<ConsultaDeRuc | null>(null);
  readonly existente = signal<ClienteApi | null>(null);
  readonly puedeRegistrar = computed(() => this.contexto.puede('ventas.cliente:registrar') && this.contexto.puede('ventas.cliente:consultar'));
  readonly formulario = this.constructorFormulario.nonNullable.group({
    ruc: ['', [Validators.required, Validators.pattern(/^(10|15|17|20)\d{9}$/)]],
    nombre: ['', [Validators.required, Validators.maxLength(300)]],
    direccion: ['', [Validators.maxLength(500)]],
  });

  constructor() {
    this.destruccion.onDestroy(() => { this.vigente = false; this.consultaNumero++; });
    this.formulario.controls.ruc.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.consultaNumero++;
      this.consultando.set(false);
      this.consulta.set(null);
      this.existente.set(null);
      this.revisando.set(false);
      this.permiteManual.set(false);
      this.fallo.set(null);
      this.aviso.set(null);
      this.formulario.patchValue({ nombre: '', direccion: '' }, { emitEvent: false });
    });
    effect(() => {
      if (this.claveContexto() !== this.contextoInicial) {
        this.vigente = false;
        this.cerrar.emit();
      }
    });
  }

  private claveContexto(): string {
    return `${this.contexto.empresaActiva()?.id ?? ''}/${this.contexto.establecimientoActivo()?.id ?? ''}`;
  }

  private actual(numero: number, ruc: string): boolean {
    return this.vigente && numero === this.consultaNumero && ruc === this.formulario.controls.ruc.value.trim()
      && this.claveContexto() === this.contextoInicial && this.puedeRegistrar();
  }

  private async buscarExistente(ruc: string): Promise<ClienteApi | null> {
    return (await this.ventas.clientes(ruc)).find((cliente) => cliente.tipoDocumento === 'RUC' && cliente.numeroDocumento === ruc) ?? null;
  }

  async consultar(): Promise<void> {
    const control = this.formulario.controls.ruc;
    if (control.invalid || !this.puedeRegistrar() || this.guardando() || this.consultando()) {
      control.markAsTouched();
      return;
    }
    const ruc = control.value.trim();
    const numero = ++this.consultaNumero;
    this.consultando.set(true);
    this.fallo.set(null);
    this.aviso.set(null);
    this.permiteManual.set(false);
    this.revisando.set(false);
    this.consulta.set(null);
    this.existente.set(null);
    try {
      const existente = await this.buscarExistente(ruc);
      if (!this.actual(numero, ruc)) return;
      if (existente) {
        this.existente.set(existente);
        if (!existente.activo) this.fallo.set('Este cliente está inactivo. Su reactivación se realiza desde Clientes.');
        return;
      }
      try {
        const consulta = await this.consultas.consultarRuc(ruc);
        if (!this.actual(numero, ruc)) return;
        if (consulta.datos.ruc !== ruc) {
          this.fallo.set('La consulta no corresponde al RUC indicado. Vuelve a consultar.');
          return;
        }
        this.consulta.set(consulta);
        this.formulario.patchValue({ nombre: consulta.datos.razonSocial, direccion: consulta.datos.domicilioFiscal ?? '' });
        this.revisando.set(true);
        if (!consulta.datos.aptaParaRegistro) this.aviso.set('El padrón no indica que el cliente esté activo y habido. Revisa sus datos antes de continuar.');
      } catch {
        if (!this.actual(numero, ruc)) return;
        this.fallo.set('No se pudo consultar el RUC. Puedes reintentar o ingresar los datos manualmente; quedarán sin verificar.');
        this.permiteManual.set(true);
      }
    } catch (fallo: unknown) {
      if (this.actual(numero, ruc)) this.fallo.set(mensajeDeError(fallo, 'No se pudo buscar el cliente.'));
    } finally {
      if (numero === this.consultaNumero) this.consultando.set(false);
    }
  }

  ingresarManualmente(): void {
    if (this.permiteManual() && this.puedeRegistrar()) {
      this.revisando.set(true);
      this.fallo.set(null);
      this.aviso.set('Cliente sin verificar. La emisión de factura conserva sus validaciones.');
    }
  }

  usarExistente(): void {
    const cliente = this.existente();
    if (cliente?.activo && this.actual(this.consultaNumero, cliente.numeroDocumento)) this.seleccionado.emit(cliente);
  }

  async registrar(): Promise<void> {
    if (this.formulario.invalid || !this.revisando() || this.guardando() || this.consultando() || !this.puedeRegistrar()) {
      this.formulario.markAllAsTouched();
      return;
    }
    const valores = this.formulario.getRawValue();
    const ruc = valores.ruc.trim();
    const numero = this.consultaNumero;
    if (!this.actual(numero, ruc)) return;
    this.guardando.set(true);
    this.fallo.set(null);
    try {
      const cliente = await this.ventas.crearCliente(datosAltaCliente({ tipoDocumento: 'RUC', numeroDocumento: ruc,
        nombre: valores.nombre.trim(), direccion: valores.direccion.trim() || null, correo: null, telefono: null }, this.consulta()));
      if (this.actual(numero, ruc)) this.seleccionado.emit(cliente);
    } catch (fallo: unknown) {
      if (!this.actual(numero, ruc)) return;
      if (interpretarError(fallo, 'No se pudo registrar el cliente.').estado === 409) {
        try {
          const cliente = await this.buscarExistente(ruc);
          if (!this.actual(numero, ruc)) return;
          if (cliente?.activo) { this.seleccionado.emit(cliente); return; }
        } catch { /* El conflicto se muestra si no se puede recuperar el registro propio. */ }
      }
      this.fallo.set(mensajeDeError(fallo, 'No se pudo registrar el cliente.'));
    } finally {
      this.guardando.set(false);
    }
  }
}
