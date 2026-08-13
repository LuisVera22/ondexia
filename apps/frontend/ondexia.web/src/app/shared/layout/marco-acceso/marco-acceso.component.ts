import { Component, DestroyRef, inject, signal } from '@angular/core';
import { TemaService } from '../../services/tema.service';

/**
 * Marco de las pantallas de acceso: ingresar, registro, retorno, recuperar.
 *
 * <p>Dos columnas en escritorio —el formulario a la izquierda, el panel de
 * marca a la derecha— y una sola en móvil, donde el panel se oculta: en una
 * pantalla de teléfono lo único que importa es el botón de entrar.
 *
 * <p>El panel derecho es un lienzo degradado con la propuesta de valor, no una
 * fotografía: pesa unos bytes, se ve nítido en cualquier densidad y no compite
 * con el formulario por la atención. Los mensajes rotan y los puntos de abajo
 * son controles reales — un indicador que no hace nada enseña a ignorar los
 * indicadores.
 *
 * <p>No lleva menú ni barra superior a propósito: quien no ha entrado no tiene
 * dónde navegar.
 */
@Component({
  selector: 'app-marco-acceso',
  templateUrl: './marco-acceso.component.html',
})
export class MarcoAccesoComponent {
  readonly tema = inject(TemaService);

  /**
   * Lo que se promete aquí tiene que ser verdad hoy. Nada de «integración con
   * SUNAT en un clic» mientras la V1 no la tenga: la pantalla de acceso es lo
   * primero que lee un cliente y lo primero que puede desmentirle el producto.
   */
  readonly mensajes = [
    {
      titulo: 'Tu negocio en orden',
      detalle: 'Ventas, compras y almacén en un solo panel, pensado para empresas peruanas.',
    },
    {
      titulo: 'Numeración que cuadra',
      detalle: 'Series y correlativos bajo control: sin huecos, sin duplicados, sin sorpresas.',
    },
    {
      titulo: 'Cada quien ve lo suyo',
      detalle: 'Roles y permisos por empresa, con los datos de cada cliente aislados de raíz.',
    },
  ];

  readonly mensajeActivo = signal(0);

  private readonly rotacion = setInterval(() => {
    this.mensajeActivo.update((actual) => (actual + 1) % this.mensajes.length);
  }, 6000);

  constructor() {
    inject(DestroyRef).onDestroy(() => clearInterval(this.rotacion));
  }

  elegirMensaje(indice: number): void {
    this.mensajeActivo.set(indice);
  }
}
