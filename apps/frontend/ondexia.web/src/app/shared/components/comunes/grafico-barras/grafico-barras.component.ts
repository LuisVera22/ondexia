import { Component, Input } from '@angular/core';

export interface PuntoGrafico {
  /** Lo que va debajo de la barra: «Lun», «Ago», «12». */
  etiqueta: string;
  valor: number;
  /** El periodo en curso: hoy, este mes. Se pinta con el color pleno. */
  destacado?: boolean;
}

/**
 * Gráfico de barras verticales.
 *
 * <h2>Por qué a mano y no con una librería</h2>
 *
 * <p>El proyecto no tiene ninguna dependencia de gráficos —solo Angular,
 * Tailwind y rxjs— y unas barras comparativas son un div con una altura en
 * porcentaje. Traer ApexCharts o Chart.js por esto añade cientos de kilobytes
 * al paquete inicial, un tema propio que mantener en claro y oscuro, y una
 * pieza que dibuja en canvas y por tanto no existe para un lector de pantalla.
 *
 * <p>El día que haga falta un gráfico de verdad —series solapadas, escalas
 * logarítmicas, zoom— esa decisión se vuelve a mirar. Para comparar siete días
 * entre sí, no hace falta.
 *
 * <h2>Los números se pueden leer, no solo la forma</h2>
 *
 * <p>Un gráfico que solo da la silueta obliga a irse a otra pantalla para saber
 * cuánto se vendió el jueves. Aquí el valor sale sobre la barra destacada, en
 * el título nativo de cada barra al posar el puntero, y completo en una tabla
 * que solo ven los lectores de pantalla. Sin esa tabla, quien no ve el gráfico
 * no tiene ninguna vía a los datos: el dibujo es puro CSS, no hay texto que
 * anunciar.
 */
@Component({
  selector: 'app-grafico-barras',
  imports: [],
  templateUrl: './grafico-barras.component.html',
})
export class GraficoBarrasComponent {
  @Input() puntos: PuntoGrafico[] = [];

  /** Qué mide el eje, para la tabla accesible. Ej.: «Ventas». */
  @Input() magnitud = 'Valor';

  /** Prefijo de la cifra. Vacío para cantidades sin unidad monetaria. */
  @Input() prefijo = 'S/ ';

  /**
   * Alto del área de barras.
   *
   * <p>Fijo y no proporcional al contenido: si el alto dependiera del valor
   * máximo, dos gráficos hermanos —ventas de la semana y del mes— tendrían
   * distinta altura y parecerían comparables entre sí cuando no lo son.
   */
  @Input() alto = 180;

  /**
   * El máximo de la serie, que es el 100 % del alto.
   *
   * <p>La escala arranca en cero a propósito. Recortarla por abajo —empezar en
   * el mínimo de la serie— multiplica visualmente diferencias pequeñas: una
   * semana de ventas casi planas parecería una montaña.
   */
  get maximo(): number {
    return this.puntos.reduce((mayor, p) => Math.max(mayor, p.valor), 0);
  }

  /**
   * Alto de una barra en porcentaje.
   *
   * <p>Nunca baja de 2: una barra de valor cero con alto cero desaparece, y
   * entonces no se distingue «no hubo ventas» de «no hay dato». Con dos por
   * ciento queda una línea visible al pie.
   */
  porcentaje(punto: PuntoGrafico): number {
    if (this.maximo <= 0) {
      return 2;
    }
    return Math.max(2, Math.round((punto.valor / this.maximo) * 100));
  }

  cifra(valor: number): string {
    return this.prefijo + valor.toLocaleString('es-PE', { maximumFractionDigits: 0 });
  }

  /** Lo que anuncia el título nativo al posar el puntero sobre la barra. */
  detalle(punto: PuntoGrafico): string {
    return `${punto.etiqueta}: ${this.cifra(punto.valor)}`;
  }
}
