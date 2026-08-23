import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ColumnaTabla, TablaDatosComponent } from './tabla-datos.component';
import { EncabezadoPaginaComponent } from '../encabezado-pagina/encabezado-pagina.component';
import { AvisoSuscripcionComponent } from '../aviso-suscripcion/aviso-suscripcion.component';
import { ContextoService } from '../../../services/contexto.service';

/**
 * Que una pantalla de teléfono no se pueda arrastrar en horizontal.
 *
 * <h2>Por qué esto merece una prueba</h2>
 *
 * <p>El desborde horizontal no rompe nada: no hay error en consola, la página
 * funciona, y en un monitor no se nota. Solo aparece en un teléfono, y se
 * manifiesta como que toda la página —encabezado incluido— se puede arrastrar
 * de lado dejando una franja vacía a la derecha. Es de los fallos que llegan al
 * cliente antes que a nosotros.
 *
 * <p>La tabla mide 640 px como mínimo a propósito: seis columnas apretadas en
 * 360 son ilegibles. Lo que NO puede pasar es que esos 640 empujen al documento
 * entero; tienen que quedarse dentro de su contenedor, con barra propia.
 *
 * <h2>Por qué un iframe y no un div de 375 px</h2>
 *
 * <p>Porque las consultas de medios miran la VENTANA, no la caja que contiene
 * al elemento. Metiendo la tabla en un `div` estrecho dentro de la ventana
 * ancha de Karma, todas las variantes `sm:` siguen activas: se estaría midiendo
 * el diseño de escritorio encogido, que es un diseño que no existe en ninguna
 * pantalla. El iframe tiene ventana propia, así que dentro de él «móvil»
 * significa móvil.
 */
@Component({
  selector: 'app-anfitrion-prueba',
  imports: [AvisoSuscripcionComponent, EncabezadoPaginaComponent, TablaDatosComponent],
  template: `
    <!--
      La pagina de Empresas entera, tal cual: encabezado y tabla dentro del
      <main> del marco, con su relleno. Se replica la pagina y no solo la tabla
      porque el desborde puede venir de cualquiera de las dos piezas, y desde
      fuera se ve igual.
    -->
    <main style="padding: 16px">
      <app-aviso-suscripcion />
      <app-encabezado-pagina
        titulo="Empresas"
        descripcion="Los contribuyentes que emiten con esta cuenta. Abre una para ver sus datos fiscales, que son los que se imprimen en cada comprobante."
      />
      <app-tabla-datos
        [columnas]="columnas"
        [registros]="registros"
        nombrePlural="empresas"
        nombreSingular="empresa"
      />
    </main>
  `,
})
class AnfitrionPrueba {
  /** Las columnas reales de Empresas, que es donde se vio el problema. */
  readonly columnas: ColumnaTabla[] = [
    { campo: 'ruc', titulo: 'RUC', ordenable: true, ancho: 'w-36' },
    { campo: 'razonSocial', titulo: 'Razón social', ordenable: true, principal: true },
    { campo: 'nombreComercial', titulo: 'Nombre comercial' },
    { campo: 'domicilioFiscal', titulo: 'Domicilio fiscal' },
    { campo: 'estado', titulo: 'Estado', ancho: 'w-32', formato: 'insignia' },
  ];

  readonly registros = [
    {
      id: 1,
      ruc: '20100000009',
      razonSocial: 'COMERCIAL DEMO S.A.C.',
      nombreComercial: 'Demo',
      domicilioFiscal: 'Av. Javier Prado Este 4200, Santiago de Surco, Lima',
      estado: 'Activa',
    },
    {
      id: 2,
      ruc: '20100000017',
      razonSocial: 'DISTRIBUIDORA DEMO E.I.R.L.',
      nombreComercial: 'Demo Distribucion',
      domicilioFiscal: 'Jr. de la Union 1050, Cercado de Lima, Lima',
      estado: 'Activa',
    },
  ];
}

/** Ancho de un teléfono corriente, el más estrecho que hay que aguantar. */
const ANCHO_TELEFONO = 375;

describe('TablaDatosComponent · no arrastra la página en un teléfono', () => {
  let marco: HTMLIFrameElement;
  let ventana: Document;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnfitrionPrueba],
      providers: [
        provideRouter([]),
        // El aviso de la cuenta en prueba, que es el que sale en la captura y
        // el texto mas largo de los que se pintan.
        {
          provide: ContextoService,
          useValue: {
            cuenta: signal({
              id: 'cuenta-1',
              esAdministrador: true,
              estadoSuscripcion: 'PRUEBA',
              soloLectura: false,
            }),
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(AnfitrionPrueba);
    fixture.detectChanges();

    marco = document.createElement('iframe');
    marco.style.cssText = `width:${ANCHO_TELEFONO}px;height:760px;border:0`;
    document.body.appendChild(marco);
    ventana = marco.contentDocument!;

    // Las hojas se copian por texto y no clonando el <link>: un <link> carga de
    // forma asincrona y la prueba mediria el DOM sin estilos — que es el
    // resultado que pasaria en verde sin significar nada.
    let reglas = 0;
    const css = [...document.styleSheets]
      .map((hoja) => {
        try {
          return [...hoja.cssRules]
            .map((r) => {
              reglas += 1;
              return r.cssText;
            })
            .join('\n');
        } catch {
          return '';
        }
      })
      .join('\n');

    const estilo = ventana.createElement('style');
    estilo.textContent = css;
    ventana.head.appendChild(estilo);
    ventana.body.style.margin = '0';
    ventana.body.appendChild(fixture.nativeElement as HTMLElement);

    // Si esto falla, lo que sigue no mide nada: mejor enterarse aqui.
    expect(reglas).withContext('no se copiaron los estilos al iframe').toBeGreaterThan(100);
  });

  afterEach(() => marco.remove());

  it('la ventana es de teléfono de verdad: las variantes «sm» están apagadas', () => {
    expect(marco.contentWindow!.innerWidth).toBe(ANCHO_TELEFONO);

    // La barra de herramientas apila en movil (`flex-col`) y se pone en fila
    // desde `sm`. Si esto dice `row`, el iframe no esta aplicando su ancho y
    // las medidas de las demas pruebas serian las del diseño de escritorio.
    const barra = ventana.querySelector('.flex.flex-col')!;
    expect(marco.contentWindow!.getComputedStyle(barra).flexDirection)
      .withContext('dentro del iframe deben mandar las clases de móvil')
      .toBe('column');
  });

  it('la tabla ancha se queda dentro de su contenedor, con barra propia', () => {
    const scroller = ventana.querySelector<HTMLElement>('.overflow-x-auto')!;
    const tabla = scroller.querySelector('table')!;

    expect(tabla.getBoundingClientRect().width)
      .withContext('la tabla debe conservar su ancho mínimo legible')
      .toBeGreaterThan(scroller.clientWidth);

    expect(scroller.scrollWidth)
      .withContext('el contenedor es quien desplaza, no la página')
      .toBeGreaterThan(scroller.clientWidth);
  });

  it('nada sobresale por la derecha de la tarjeta', () => {
    const tarjeta = ventana.querySelector<HTMLElement>('app-tabla-datos > div')!;
    const scroller = tarjeta.querySelector<HTMLElement>('.overflow-x-auto')!;
    const borde = tarjeta.getBoundingClientRect().right;

    // Se compara el borde derecho y no el ancho: un elemento puede caber de
    // sobra y aun asi asomar, si empieza demasiado a la derecha.
    const fuera = [...tarjeta.querySelectorAll<HTMLElement>('*')]
      .filter((e) => !scroller.contains(e))
      .filter((e) => {
        const caja = e.getBoundingClientRect();
        return caja.width > 0 && caja.right > borde + 1;
      })
      .map((e) => `${e.tagName.toLowerCase()}.${String(e.className).slice(0, 40)}`);

    expect(fuera).withContext(fuera.join(' | ')).toEqual([]);
  });

  it('el documento del teléfono no se puede arrastrar de lado', () => {
    const raiz = ventana.documentElement;

    // Y si se puede, que diga quien tiene la culpa en vez de solo el numero.
    //
    // Se descarta lo que vive dentro de un contenedor que recorta o desplaza:
    // la tabla de 640 px asoma por la derecha de la ventana a proposito, y su
    // contenedor la absorbe. Señalarla seria acusar al que funciona bien.
    const vista = marco.contentWindow!;
    const recortado = (e: HTMLElement): boolean => {
      let padre = e.parentElement;
      while (padre && padre !== ventana.documentElement) {
        const desbordeX = vista.getComputedStyle(padre).overflowX;
        if (desbordeX !== 'visible') {
          return true;
        }
        padre = padre.parentElement;
      }
      return false;
    };

    const culpables = [...ventana.querySelectorAll<HTMLElement>('body *')]
      .filter((e) => {
        const caja = e.getBoundingClientRect();
        return caja.width > 0 && caja.right > ANCHO_TELEFONO + 1 && !recortado(e);
      })
      .map((e) => {
        const caja = e.getBoundingClientRect();
        return `${e.tagName.toLowerCase()}.${String(e.className).slice(0, 40)} → ${Math.round(caja.right)}`;
      });

    expect(culpables).withContext(culpables.join('  |  ')).toEqual([]);
    expect(raiz.scrollWidth)
      .withContext(`la página mide ${raiz.scrollWidth} en una ventana de ${ANCHO_TELEFONO}`)
      .toBeLessThanOrEqual(ANCHO_TELEFONO);
  });
});
