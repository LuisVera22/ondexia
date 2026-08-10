import { Component } from '@angular/core';
import { EncabezadoPaginaComponent } from '../../../shared/components/comunes/encabezado-pagina/encabezado-pagina.component';

interface RecursoLogo {
  clave: 'principal' | 'ticket' | 'simbolo';
  nombre: string;
  uso: string;
  requisito: string;
  archivo: string | null;
}

/**
 * Identidad visual de la empresa emisora.
 *
 * Los logos son por empresa y no por cuenta: en multiempresa cada RUC
 * tiene su propia marca (documento 04 §6.2).
 *
 * Se gestionan tres recursos y no uno solo porque la impresora térmica es
 * monocroma y angosta: un logo a color con degradados sale como una mancha
 * gris, y el A4 y el ticket de 80 mm tienen proporciones incompatibles.
 */
@Component({
  selector: 'app-identidad',
  imports: [EncabezadoPaginaComponent],
  templateUrl: './identidad.component.html',
})
export class IdentidadComponent {
  recursos: RecursoLogo[] = [
    {
      clave: 'principal',
      nombre: 'Logo principal',
      uso: 'Encabezado de la aplicación, PDF de comprobantes e informes',
      requisito: 'Horizontal, fondo transparente. PNG o SVG, hasta 1 MB.',
      archivo: '/images/logo/logo.svg',
    },
    {
      clave: 'ticket',
      nombre: 'Logo para ticket',
      uso: 'Impresión térmica de boletas',
      requisito: 'Monocromo y de alto contraste, ancho máximo 384 px.',
      archivo: null,
    },
    {
      clave: 'simbolo',
      nombre: 'Símbolo',
      uso: 'Barra lateral colapsada y pestaña del navegador',
      requisito: 'Cuadrado, mínimo 128 × 128 px.',
      archivo: '/images/logo/logo-icon.svg',
    },
  ];

  vistaPrevia: 'a4' | 'ticket' = 'a4';

  get logoPrincipal(): string | null {
    return this.recursos[0].archivo;
  }

  get logoTicket(): string | null {
    return this.recursos[1].archivo;
  }

  quitar(recurso: RecursoLogo): void {
    recurso.archivo = null;
  }
}
