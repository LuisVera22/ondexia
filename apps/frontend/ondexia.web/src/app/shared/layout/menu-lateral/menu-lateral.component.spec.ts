import { TestBed } from '@angular/core/testing';
import { provideRouter, Route } from '@angular/router';

import { GrupoMenu, MenuLateralComponent } from './menu-lateral.component';
import { ContextoService } from '../../services/contexto.service';
import { routes } from '../../../app.routes';

/**
 * Que el menú esconda lo que la cuenta no tiene, que los códigos existan, y
 * que ninguna entrada lleve a una pantalla que no está.
 *
 * <p>La segunda parte es la que importa: un código mal escrito no revienta
 * nada. {@code puede()} devuelve false para siempre y la entrada desaparece
 * para todo el mundo, incluido quien sí la tiene contratada. Nadie ve un error;
 * se ve un cliente escribiendo que «se le perdió» una pantalla.
 *
 * <p>La tercera es de la iteración 7. Al recortar el menú al primer producto se
 * retiraron veintitantas entradas cuyas rutas se fueron a
 * {@code pages/_maquetas/}: sin esta prueba, dejar una entrada apuntando a una
 * ruta retirada se descubre pulsándola.
 */
describe('MenuLateralComponent · lo que se ve segun los permisos', () => {
  /**
   * Los submódulos reales, copiados de V6__permisos_jerarquicos.sql y de las
   * migraciones que añadieron alguno después (V7 identidad, V17 caja, V19 nota
   * de venta, V21 nota de crédito, V22 comunicación de baja).
   *
   * <p>Sí, es una segunda copia de la misma verdad. Está aquí a propósito: una
   * prueba que repite el dato de forma independiente es lo que convierte un
   * error de transcripción en un build rojo. Si algún día se añade un submódulo
   * a la migración, esta lista hay que actualizarla — y eso es un recordatorio,
   * no una desincronización silenciosa.
   *
   * <p>Se conserva entera, con los submódulos de Compras y de los catálogos de
   * almacén que el menú ya no ofrece: la tabla `permiso` sigue teniéndolos y sus
   * pantallas vuelven con su iteración.
   */
  const SUBMODULOS = new Set([
    'almacen.producto', 'almacen.marca', 'almacen.modelo', 'almacen.presentacion',
    'almacen.tipo_precio', 'almacen.precio', 'almacen.almacen', 'almacen.stock',
    'almacen.kardex', 'almacen.guia_ingreso', 'almacen.guia_remision',
    'compras.proveedor', 'compras.nota_pedido', 'compras.orden_compra',
    'compras.orden_servicio', 'compras.nota_compra', 'compras.factura_compra',
    'compras.liquidacion',
    'ventas.cliente', 'ventas.cotizacion', 'ventas.nota_preventa', 'ventas.comprobante',
    'ventas.nota_credito', 'ventas.nota_debito', 'ventas.resumen_diario', 'ventas.caja',
    'ventas.comunicacion_baja',
    'ventas.nota_venta',
    'configuracion.empresa', 'configuracion.sucursal', 'configuracion.usuario',
    'configuracion.rol', 'configuracion.serie', 'configuracion.comprobante',
    'configuracion.auditoria', 'configuracion.identidad',
  ]);

  let permisos: string[];
  let componente: MenuLateralComponent;

  function nombres(grupos: GrupoMenu[]): string[] {
    return grupos.flatMap((grupo) =>
      grupo.entradas.flatMap((entrada) => [
        entrada.nombre,
        ...(entrada.submenu ?? []).map((sub) => sub.nombre),
      ])
    );
  }

  /** Toda ruta declarada, con el prefijo del padre, y sin los parámetros. */
  function rutasDeclaradas(declaradas: Route[], prefijo = ''): string[] {
    return declaradas.flatMap((ruta) => {
      const camino = [prefijo, ruta.path ?? ''].filter(Boolean).join('/');
      return [`/${camino}`, ...rutasDeclaradas(ruta.children ?? [], camino)];
    });
  }

  beforeEach(() => {
    permisos = [];

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: ContextoService,
          useValue: { puede: (permiso: string) => permisos.includes(permiso) },
        },
      ],
    });

    componente = TestBed.runInInjectionContext(() => new MenuLateralComponent());
  });

  it('todos los codigos declarados existen de verdad', () => {
    const declarados = componente.todosParaPruebas.flatMap((grupo) =>
      grupo.entradas.flatMap((entrada) =>
        (entrada.submenu ?? []).map((sub) => sub.permiso).filter((codigo) => !!codigo)
      )
    );

    expect(declarados.length).toBeGreaterThan(10);
    declarados.forEach((codigo) =>
      expect(SUBMODULOS.has(codigo!)).withContext(`«${codigo}» no es un submodulo real`).toBeTrue()
    );
  });

  it('ninguna entrada lleva a una ruta que no existe', () => {
    const existentes = new Set(rutasDeclaradas(routes));
    const enlazadas = componente.todosParaPruebas.flatMap((grupo) =>
      grupo.entradas.flatMap((entrada) => [
        ...(entrada.ruta ? [entrada.ruta] : []),
        ...(entrada.submenu ?? []).map((sub) => sub.ruta),
      ])
    );

    expect(enlazadas.length).toBeGreaterThan(10);
    enlazadas.forEach((ruta) =>
      expect(existentes.has(ruta))
        .withContext(`«${ruta}» no corresponde a ninguna ruta declarada`)
        .toBeTrue()
    );
  });

  it('cada entrada del primer producto declara su submodulo', () => {
    // Tras el recorte de la iteración 7 no queda ninguna entrada sin anotar.
    // El valor ausente sigue significando «siempre visible» —esconder una
    // pantalla porque a alguien se le olvidó anotarla es el error que nadie
    // detecta— pero hoy ninguna se apoya en ese comportamiento.
    const sinAnotar = componente.todosParaPruebas.flatMap((grupo) =>
      grupo.entradas.flatMap((entrada) =>
        (entrada.submenu ?? []).filter((sub) => !sub.permiso).map((sub) => sub.nombre)
      )
    );

    expect(sinAnotar).toEqual([]);
  });

  it('sin ningun permiso solo queda lo que no exige modulo', () => {
    const visibles = nombres(componente.grupos());

    expect(visibles).toContain('Panel');
    expect(visibles).not.toContain('Almacén');
    expect(visibles).not.toContain('Ventas');
  });

  it('un modulo contratado trae sus submodulos contratados, y solo esos', () => {
    permisos = ['ventas:acceder', 'ventas.caja:acceder', 'ventas.cliente:acceder'];

    const visibles = nombres(componente.grupos());

    expect(visibles).toContain('Ventas');
    expect(visibles).toContain('Cajas');
    expect(visibles).toContain('Clientes');
    expect(visibles).not.toContain('Facturas');
    expect(visibles).not.toContain('Comunicaciones de baja');
  });

  it('un modulo cuyos submodulos estan todos apagados desaparece', () => {
    // Un desplegable que no despliega nada se lee como que algo se rompio.
    permisos = ['almacen:acceder'];

    expect(nombres(componente.grupos())).not.toContain('Almacén');
  });
});
