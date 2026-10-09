import { Routes } from '@angular/router';

import { sesionGuard } from './nucleo/sesion.guard';

/**
 * Todo detrás del guardián, sin excepciones.
 *
 * <p>En el SPA de clientes hay rutas públicas —registro, recuperación—. Aquí no
 * hay ninguna: no existe el «visitante» de una consola cuyo contenido son las
 * cuentas de todos los clientes.
 */
export const routes: Routes = [
  {
    path: '',
    canActivate: [sesionGuard],
    children: [
      {
        path: 'cuentas',
        loadComponent: () =>
          import('./paginas/cuentas/cuentas.component').then((m) => m.CuentasComponent),
      },
      {
        path: 'cuentas/:id',
        loadComponent: () =>
          import('./paginas/cuenta/cuenta.component').then((m) => m.CuentaComponent),
      },
      { path: '', pathMatch: 'full', redirectTo: 'cuentas' },
    ],
  },
  { path: '**', redirectTo: '' },
];
