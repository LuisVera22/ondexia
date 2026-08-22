import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import {
  TablaDatosComponent,
  AccionDeFila,
  ColumnaTabla,
} from '../../../shared/components/comunes/tabla-datos/tabla-datos.component';
import { ConfirmacionComponent } from '../../../shared/components/comunes/confirmacion/confirmacion.component';
import { ModalComponent } from '../../../shared/components/comunes/modal/modal.component';
import { BotonComponent } from '../../../shared/components/comunes/boton/boton.component';
import { accionConEstado } from '../../../shared/components/comunes/boton/estado-accion';
import {
  ConfiguracionApiService,
  Establecimiento,
  mensajeDeError,
} from '../../../nucleo/configuracion.api.service';
import { AvisosService } from '../../../shared/services/avisos.service';
import { repartirFallo } from '../../../shared/formularios/fallo-de-formulario';
import { ErrorCampoComponent } from '../../../shared/components/comunes/error-campo/error-campo.component';

/**
 * Establecimientos anexos de la empresa.
 *
 * <p>No se limitan por plan (documento 04 §2): cobrar por local empujaría al
 * cliente a registrar uno solo y declarar mal el código de establecimiento en
 * comprobantes y guías de remisión.
 *
 * <h2>Crear en modal, editar en su propia vista</h2>
 *
 * <p>Antes las dos cosas ocurrían en un formulario que se desplegaba sobre la
 * tabla y empujaba el listado hacia abajo. Ahora crear abre un modal —no hay
 * nada que enlazar todavía, y el listado se conserva de fondo— y abrir un
 * establecimiento existente lleva a su ficha, con URL propia: un registro que ya
 * existe se comparte por enlace, se recarga y se abre en otra pestaña.
 *
 * <h2>Dos cosas que ya cambiaron al conectarla</h2>
 *
 * <p>La columna «Distrito» desapareció: lo que se guarda es el <em>ubigeo</em>,
 * que es el código de seis dígitos que usa SUNAT, y traducirlo a un nombre de
 * distrito necesita un catálogo que todavía no existe.
 *
 * <p>Y «Eliminar» pasó a ser «Desactivar». El backend nunca borra: un
 * establecimiento aparece en los comprobantes ya emitidos, y borrarlo dejaría
 * documentos apuntando a nada.
 */
@Component({
  selector: 'app-establecimientos',
  imports: [
    EncabezadoPaginaComponent,
    TablaDatosComponent,
    ConfirmacionComponent,
    ModalComponent,
    BotonComponent,
    ReactiveFormsModule,
    ErrorCampoComponent,
  ],
  templateUrl: './establecimientos.component.html',
})
export class EstablecimientosComponent {
  private readonly api = inject(ConfiguracionApiService);
  private readonly avisos = inject(AvisosService);
  private readonly router = inject(Router);
  private readonly constructorFormulario = inject(FormBuilder);
  readonly accionesDeFila: AccionDeFila[] = [
    { id: 'abrir', etiqueta: 'Abrir', icono: 'abrir' },
    {
      id: 'desactivar',
      etiqueta: 'Desactivar',
      icono: 'desactivar',
      peligrosa: true,
      // La casa matriz no se desactiva: sin ella la empresa no puede emitir.
      disponible: (registro) => registro['activa'] === true && registro['esMatriz'] !== true,
    },
  ];


  readonly columnas: ColumnaTabla[] = [
    { campo: 'codigo', titulo: 'Código SUNAT', ordenable: true, ancho: 'w-32' },
    { campo: 'nombre', titulo: 'Establecimiento', ordenable: true },
    { campo: 'direccion', titulo: 'Dirección' },
    { campo: 'ubigeo', titulo: 'Ubigeo', ancho: 'w-28' },
    { campo: 'estado', titulo: 'Estado', ancho: 'w-28' },
  ];

  readonly registros = signal<Record<string, unknown>[]>([]);
  readonly cargando = signal(true);

  /**
   * Solo el fallo al cargar el listado, que se pinta en lugar de la tabla.
   *
   * <p>El resultado de crear o desactivar va a un aviso. Esto no: no es
   * respuesta a nada que el usuario haya pulsado, y un aviso que se va en cuatro
   * segundos dejaría una tabla vacía que parece decir «no hay establecimientos».
   */
  readonly error = signal<string | null>(null);

  readonly modalAbierto = signal(false);

  readonly confirmacionAbierta = signal(false);
  private aDesactivar: Establecimiento | null = null;

  formulario = this.constructorFormulario.nonNullable.group({
    codigo: ['', [Validators.required, Validators.pattern(/^\d{4}$/)]],
    nombre: ['', [Validators.required]],
    direccion: ['', [Validators.required]],
    ubigeo: ['', [Validators.pattern(/^$|^\d{6}$/)]],
  });

  private originales: Establecimiento[] = [];

  constructor() {
    void this.cargar();
  }

  get controles() {
    return this.formulario.controls;
  }

  /**
   * Vuelve a traer el listado, a peticion del usuario.
   *
   * <p>Existe porque {@code cargar} es privado y la plantilla no lo alcanza.
   * No es lo mismo que recargar la pagina: no se pierde el orden, ni la
   * pagina en la que se estaba, ni lo escrito en el buscador.
   */
  recargar(): void {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.originales = await this.api.establecimientos();
      this.registros.set(
        this.originales.map((e) => ({
          id: e.id,
          codigo: e.codigo,
          nombre: e.nombre,
          direccion: e.direccion,
          ubigeo: e.ubigeo ?? '—',
          estado: e.activa ? 'Activo' : 'Inactivo',
          // La casa matriz no se desactiva: sin ella la empresa no puede
          // emitir, y SUNAT la exige siempre.
          esMatriz: e.codigo === '0000',
          activa: e.activa,
        }))
      );
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar los establecimientos.'));
    } finally {
      this.cargando.set(false);
    }
  }

  abrirNuevo(): void {
    this.formulario.reset({ codigo: '', nombre: '', direccion: '', ubigeo: '' });
    this.modalAbierto.set(true);
  }

  cerrarModal(): void {
    this.modalAbierto.set(false);
  }

  abrirFicha(fila: Record<string, unknown>): void {
    void this.router.navigate(['/configuracion/establecimientos', fila['id']]);
  }

  /**
   * Da de alta el establecimiento y cierra el modal.
   *
   * <p>El aviso es obligatorio: al cerrarse el modal desaparece el botón que
   * podría contar el resultado, y la fila nueva aparece en una tabla que puede
   * estar ordenada de forma que no quede a la vista.
   */
  readonly crear = accionConEstado(async () => {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      throw new Error('Formulario incompleto');
    }

    const valores = this.formulario.getRawValue();

    try {
      const creado = await this.api.crearEstablecimiento({
        codigo: valores.codigo,
        nombre: valores.nombre,
        direccion: valores.direccion,
        ubigeo: valores.ubigeo || null,
      });

      this.cerrarModal();
      await this.cargar();
      this.avisos.exito(`${creado.codigo} · ${creado.nombre}`, 'Establecimiento registrado');
    } catch (fallo: unknown) {
      // El 409 de código duplicado llega aquí con el mensaje del servidor, que
      // dice qué código choca. Es mejor que cualquier texto genérico de aquí.
      //
      // El modal se queda abierto a propósito: el formulario conserva lo que el
      // usuario escribió, y va a querer corregir el código, no teclearlo todo
      // otra vez.
      repartirFallo(fallo, this.formulario, this.avisos, 'No se pudo registrar el establecimiento.');
      throw fallo;
    }
  });

  pedirDesactivacion(fila: Record<string, unknown>): void {
    const establecimiento = this.originales.find((e) => e.id === fila['id']);
    if (!establecimiento) {
      return;
    }
    this.aDesactivar = establecimiento;
    this.confirmacionAbierta.set(true);
  }

  get nombreADesactivar(): string {
    return this.aDesactivar?.nombre ?? '';
  }

  readonly desactivar = accionConEstado(async () => {
    const establecimiento = this.aDesactivar;
    if (!establecimiento) {
      return;
    }

    try {
      await this.api.desactivarEstablecimiento(establecimiento.id);
      this.confirmacionAbierta.set(false);
      this.aDesactivar = null;
      await this.cargar();
      this.avisos.exito(
        `${establecimiento.nombre} ya no está disponible para emitir`,
        'Establecimiento desactivado'
      );
    } catch (fallo: unknown) {
      this.avisos.error(mensajeDeError(fallo, 'No se pudo desactivar el establecimiento.'));
      throw fallo;
    }
  });

  cancelarDesactivacion(): void {
    this.confirmacionAbierta.set(false);
    this.aDesactivar = null;
  }

  ejecutarAccion(evento: { accion: string; registro: Record<string, unknown> }): void {
    if (evento.accion === 'abrir') {
      this.abrirFicha(evento.registro);
    } else if (evento.accion === 'desactivar') {
      this.pedirDesactivacion(evento.registro);
    }
  }
}
