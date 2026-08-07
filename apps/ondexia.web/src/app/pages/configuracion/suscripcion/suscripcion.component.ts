import { Component } from '@angular/core';
import { PageBreadcrumbComponent } from '../../../shared/components/common/page-breadcrumb/page-breadcrumb.component';

interface Consumo {
  nombre: string;
  usado: number;
  limite: number;
  detalle: string;
}

/**
 * Plan contratado y consumo frente a sus límites.
 *
 * No incluye contratación ni pago en línea: cambiar de plan es una gestión
 * comercial fuera de la aplicación por ahora (documento 04 §5).
 */
@Component({
  selector: 'app-suscripcion',
  imports: [PageBreadcrumbComponent],
  templateUrl: './suscripcion.component.html',
})
export class SuscripcionComponent {
  plan = {
    nombre: 'Intermedio',
    estado: 'Activo',
    vigenteHasta: '31/12/2026',
    ciclo: 'Anual',
  };

  consumos: Consumo[] = [
    { nombre: 'Empresas', usado: 2, limite: 2, detalle: 'RUC emisores registrados' },
    { nombre: 'Usuarios', usado: 2, limite: 5, detalle: 'Se cuentan sobre toda la cuenta, no por empresa' },
    { nombre: 'Establecimientos', usado: 3, limite: 0, detalle: 'Sin límite en ningún plan' },
    { nombre: 'Almacenes', usado: 3, limite: 0, detalle: 'Sin límite en ningún plan' },
  ];

  porcentaje(consumo: Consumo): number {
    if (consumo.limite === 0) {
      return 0;
    }
    return Math.min(100, Math.round((consumo.usado / consumo.limite) * 100));
  }

  /** Se avisa antes de llegar al tope, no cuando ya no se puede crear nada. */
  estaPorAgotarse(consumo: Consumo): boolean {
    return consumo.limite > 0 && this.porcentaje(consumo) >= 80;
  }

  estaAgotado(consumo: Consumo): boolean {
    return consumo.limite > 0 && consumo.usado >= consumo.limite;
  }

  esIlimitado(consumo: Consumo): boolean {
    return consumo.limite === 0;
  }
}
