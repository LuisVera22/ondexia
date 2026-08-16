import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EncabezadoPaginaComponent } from '../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';
import { mensajeDeError } from '../../nucleo/configuracion.api.service';
import { DatosDePerfil, PerfilApiService } from '../../nucleo/perfil.api.service';
import { SesionService } from '../../nucleo/sesion.service';
import { ContextoService } from '../../shared/services/contexto.service';

/**
 * Mi perfil.
 *
 * <h2>Tres cosas que esta pantalla NO puede hacer, y no es pereza</h2>
 *
 * <p><strong>Cambiar el correo.</strong> Es la credencial con la que se entra a
 * Cognito. Editarlo aquí dejaría nuestra fila apuntando a un buzón con el que ya
 * no se puede iniciar sesión: el cambio se vería guardado y el acceso roto, en
 * ese orden. Se pinta deshabilitado y con el motivo al lado.
 *
 * <p><strong>Cambiar la contraseña.</strong> No la guardamos, no la vemos y no
 * la queremos. El formulario que había aquí no enviaba nada — era de la maqueta.
 * Se sustituye por el flujo alojado de Cognito, que es quien sabe si el correo
 * existe, manda el código y valida la nueva.
 *
 * <p><strong>Cambiar el rol o las empresas.</strong> Los define el administrador
 * de la cuenta; editarlos aquí sería darse permisos a uno mismo. Se muestran en
 * solo lectura porque saber con qué permisos se opera evita la confusión de «no
 * me deja hacer esto» sin explicación.
 *
 * <h2>De dónde sale cada dato</h2>
 *
 * <p>El nombre, el correo y el teléfono, de {@code GET /api/v1/perfil}. El rol y
 * las empresas, del contexto que ya está cargado — pedirlos otra vez sería una
 * segunda fuente de la misma verdad, y la que se quedara vieja mentiría.
 */
@Component({
  selector: 'app-perfil',
  imports: [EncabezadoPaginaComponent, ReactiveFormsModule],
  templateUrl: './perfil.component.html',
})
export class PerfilComponent implements OnInit {
  private readonly constructorFormulario = inject(FormBuilder);
  private readonly api = inject(PerfilApiService);
  private readonly sesion = inject(SesionService);
  private readonly contexto = inject(ContextoService);

  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly guardado = signal(false);
  readonly error = signal<string | null>(null);

  /** Solo para pintar la cabecera de la tarjeta; el editable es el del formulario. */
  readonly nombreGuardado = signal('');
  readonly correo = signal('');

  /**
   * Si esta cuenta viene de antes de que existiera el apellido y hay que
   * repartir el nombre a mano. Solo se enseña el aviso mientras dure.
   */
  readonly faltaPartirElNombre = signal(false);

  /** Asignados por el administrador de la cuenta. Nunca editables aquí. */
  readonly empresas = computed(() => this.contexto.empresasConRol());
  readonly rolActivo = computed(() => this.contexto.rolEnEmpresaActiva());
  readonly administraLaCuenta = computed(() => this.contexto.cuenta()?.esAdministrador ?? false);

  readonly iniciales = computed(() => iniciales(this.nombreGuardado()));

  formulario = this.constructorFormulario.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(150)]],
    apellido: ['', [Validators.required, Validators.maxLength(150)]],
    telefono: ['', [Validators.maxLength(30)]],
  });

  get controles() {
    return this.formulario.controls;
  }

  async ngOnInit(): Promise<void> {
    try {
      const perfil = await this.api.ver();
      this.aplicar(perfil);
      this.correo.set(perfil.email);

      /*
       * Sin apellido es una cuenta anterior a que el campo existiera: su nombre
       * completo está entero en `nombre`. No se parte por el espacio para
       * repartirlo —«María del Carmen Rojas» no tiene forma de acertar—, se
       * marca el campo como tocado para que salga en rojo y se explica arriba.
       */
      if (!perfil.apellido) {
        this.controles.apellido.markAsTouched();
        this.faltaPartirElNombre.set(true);
      }
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron cargar tus datos.'));
    } finally {
      this.cargando.set(false);
    }
  }

  async guardar(): Promise<void> {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.guardando.set(true);
    this.error.set(null);
    this.guardado.set(false);

    const valores = this.formulario.getRawValue();

    try {
      const perfil = await this.api.guardar({
        nombre: valores.nombre,
        apellido: valores.apellido,
        telefono: valores.telefono.trim() || null,
      });

      // Se repinta con lo que devolvió el servidor, no con lo que se envió: el
      // backend recorta espacios y convierte el teléfono en blanco a nulo, y
      // quedarse con la versión local haría que un simple refresco «cambiara»
      // el dato sin que nadie lo tocara.
      this.aplicar(perfil);
      this.faltaPartirElNombre.set(false);
      this.formulario.markAsPristine();
      this.guardado.set(true);
    } catch (fallo: unknown) {
      this.error.set(mensajeDeError(fallo, 'No se pudieron guardar los cambios.'));
    } finally {
      this.guardando.set(false);
    }
  }

  /** Vuelca la respuesta del servidor en el formulario y en la tarjeta. */
  private aplicar(perfil: DatosDePerfil): void {
    this.nombreGuardado.set(
      perfil.apellido ? `${perfil.nombre} ${perfil.apellido}` : perfil.nombre
    );
    this.formulario.setValue({
      nombre: perfil.nombre,
      apellido: perfil.apellido ?? '',
      telefono: perfil.telefono ?? '',
    });
  }

  /**
   * Lleva al cambio de contraseña de Cognito. No retorna: la pestaña navega
   * fuera, y al volver se entra con la contraseña nueva.
   */
  cambiarContrasena(): void {
    void this.sesion.recuperar();
  }
}

/**
 * Iniciales a partir de un solo nombre.
 *
 * <p>El modelo guarda un `nombre` y no lo parte en nombres y apellidos, así que
 * se toman la primera y la última palabra. Con una sola palabra sale una letra,
 * que es correcto: inventar la segunda daría una inicial que no es de nadie.
 */
function iniciales(nombre: string): string {
  const palabras = nombre.trim().split(/\s+/).filter(Boolean);
  if (palabras.length === 0) {
    return '';
  }
  const primera = palabras[0].charAt(0);
  const ultima = palabras.length > 1 ? palabras[palabras.length - 1].charAt(0) : '';
  return (primera + ultima).toUpperCase();
}
