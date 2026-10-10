import { Component, input } from '@angular/core';

/** Configuración y la impresión usan la misma cabecera para cada formato. */
@Component({
  selector: 'app-cabecera-comprobante',
  template: `
    <header class="cabecera" [class.cabecera--a4]="formato() === 'a4'" [class.cabecera--ticket]="formato() === 'ticket'">
      <div class="emisor">
        @if (logo()) {
          <img [src]="logo()" alt="Logo del emisor" />
        } @else if (nombreComercial()) {
          <p class="nombre-comercial">{{ nombreComercial() }}</p>
        }
        <p class="razon-social">{{ razonSocial() }}</p>
        @if (formato() === 'ticket') { <p>RUC {{ ruc() }}</p> }
        @if (direccion()) { <p>{{ direccion() }}</p> }
      </div>
      <div class="identificacion">
        @if (formato() === 'a4') { <p>RUC {{ ruc() }}</p> }
        <p class="titulo">{{ titulo() }}</p>
        @if (numero()) { <p class="numero">{{ numero() }}</p> }
      </div>
    </header>
  `,
  styles: `
    :host { display: block; min-width: 0; color: #111827; }
    .cabecera { font-size: 12px; line-height: 1.4; overflow-wrap: anywhere; margin-bottom: 12px; }
    p { margin: 0; }
    .emisor, .identificacion { min-width: 0; }
    img { display: block; object-fit: contain; max-width: 100%; height: auto; }
    .nombre-comercial, .titulo, .numero { font-weight: 600; }
    .nombre-comercial { text-transform: uppercase; font-size: 16px; }
    .cabecera--a4 { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 16px; align-items: start; }
    .cabecera--a4 img { max-height: 20mm; margin-bottom: 8px; }
    .cabecera--a4 .identificacion { border: 1px solid #9ca3af; border-radius: 6px; padding: 8px; text-align: center; }
    .cabecera--ticket { text-align: center; }
    .cabecera--ticket img { max-height: 10mm; margin: 0 auto 8px; filter: grayscale(1) contrast(1.25); }
    .cabecera--ticket .identificacion { margin-top: 12px; }
  `,
})
export class CabeceraComprobanteComponent {
  readonly formato = input.required<'a4' | 'ticket'>();
  readonly logo = input<string | null>(null);
  readonly razonSocial = input<string | null>(null);
  readonly nombreComercial = input<string | null>(null);
  readonly ruc = input<string | null>(null);
  readonly direccion = input<string | null>(null);
  readonly titulo = input.required<string>();
  readonly numero = input<string | null>(null);
}
