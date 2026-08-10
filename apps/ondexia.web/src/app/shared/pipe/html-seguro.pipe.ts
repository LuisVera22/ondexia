import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

/**
 * Marca una cadena de HTML como segura para insertarla con `[innerHTML]`.
 *
 * Se usa **solo** para los iconos SVG del menú, que son constantes escritas en
 * el código. Nunca debe recibir texto que venga del servidor, de la URL o de
 * un formulario: saltarse el saneado de Angular con contenido ajeno es
 * exactamente cómo se abre un XSS.
 */
@Pipe({ name: 'htmlSeguro' })
export class HtmlSeguroPipe implements PipeTransform {
  private readonly saneador = inject(DomSanitizer);

  transform(valor: string): SafeHtml {
    return this.saneador.bypassSecurityTrustHtml(valor);
  }
}
