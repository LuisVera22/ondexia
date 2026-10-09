import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

/**
 * Los iconos del menú, como constantes del código.
 *
 * Viven aquí y no en el menú para que el pipe pueda tiparse con sus claves: es
 * lo que hace imposible pasarle otra cosa.
 */
export const ICONO = {
  panel: `<svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg"><rect x="3" y="3" width="7.5" height="7.5" rx="2"/><rect x="13.5" y="3" width="7.5" height="7.5" rx="2"/><rect x="3" y="13.5" width="7.5" height="7.5" rx="2"/><rect x="13.5" y="13.5" width="7.5" height="7.5" rx="2"/></svg>`,
  almacen: `<svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg"><path d="M21 8.5v7a2 2 0 0 1-1.1 1.79l-7 3.5a2 2 0 0 1-1.8 0l-7-3.5A2 2 0 0 1 3 15.5v-7a2 2 0 0 1 1.1-1.79l7-3.5a2 2 0 0 1 1.8 0l7 3.5A2 2 0 0 1 21 8.5Z"/><path d="m3.3 7.5 8.7 4.35 8.7-4.35M12 21v-9.15"/></svg>`,
  compras: `<svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg"><path d="M2.5 3.5h1.6a1 1 0 0 1 .98.8L5.4 7m0 0 1.85 7.4a2 2 0 0 0 1.94 1.52h7.24a2 2 0 0 0 1.94-1.5L20.1 8.25A1 1 0 0 0 19.13 7H5.4Z"/><circle cx="9.5" cy="19.5" r="1.5"/><circle cx="17" cy="19.5" r="1.5"/></svg>`,
  ventas: `<svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg"><path d="M6 2.5h12a1 1 0 0 1 1 1v18l-2.6-1.7-2.6 1.7-2.6-1.7-2.6 1.7L5 21.5v-18a1 1 0 0 1 1-1Z"/><path d="M8.5 8h7M8.5 12h7M8.5 16h4"/></svg>`,
  configuracion: `<svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="3"/><path d="M19.14 12.94a1.5 1.5 0 0 1 0-1.88l1.2-1.5-1.74-3-1.83.62a1.5 1.5 0 0 1-1.63-.94L14.5 4.4h-5l-.64 1.84a1.5 1.5 0 0 1-1.63.94l-1.83-.62-1.74 3 1.2 1.5a1.5 1.5 0 0 1 0 1.88l-1.2 1.5 1.74 3 1.83-.62a1.5 1.5 0 0 1 1.63.94l.64 1.84h5l.64-1.84a1.5 1.5 0 0 1 1.63-.94l1.83.62 1.74-3Z"/></svg>`,
} as const;

export type Icono = keyof typeof ICONO;

/**
 * Convierte el NOMBRE de un icono en HTML seguro para `[innerHTML]`.
 *
 * Recibe la clave, no el HTML (tabla de bajas de la auditoría 2026-09-01). La
 * versión anterior aceptaba cualquier cadena y la marcaba como segura: hoy solo
 * le llegaban constantes, pero un pipe global que hace
 * `bypassSecurityTrustHtml` sobre lo que le pasen es un XSS a la espera de que
 * alguien lo use con un texto del servidor. Con la clave tipada, el compilador
 * rechaza cualquier otra cosa.
 */
@Pipe({ name: 'htmlSeguro' })
export class HtmlSeguroPipe implements PipeTransform {
  private readonly saneador = inject(DomSanitizer);

  transform(icono: Icono): SafeHtml {
    return this.saneador.bypassSecurityTrustHtml(ICONO[icono]);
  }
}
