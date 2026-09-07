import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { GrupoMenu, MenuLateralComponent } from './menu-lateral.component';
import { ContextoService } from '../../services/contexto.service';

/**
 * Que el menú esconda lo que la cuenta no tiene, y que los códigos existan.
 *
 * <p>La segunda mitad es la que importa: un código mal escrito no revienta
 * nada. {@code puede()} devuelve false para siempre y la entrada desaparece
 * para todo el mundo, incluido quien sí la tiene contratada. Nadie ve un error;
 * se ve un cliente escribiendo que «se le perdió» una pantalla.
 */
describe('MenuLateralComponent · lo que se ve segun los permisos', () => {
  /**
   * Los submódulos reales, copiados de V6__permisos_jerarquicos.sql y de las
   * migraciones que añadieron alguno después (V7 identidad, V17 caja).
   *
   * <p>Sí, es una segunda copia de la misma verdad. Está aquí a propósito: una
   * prueba que repite el dato de forma independiente es lo que convierte un
   * error de transcripción en un build rojo. Si algún día se añade un submódulo
   * a la migración, esta lista hay que actualizarla — y eso es un recordatorio,
   * no una desincronización silenciosa.
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

    expect(declarados.length).toBeGreaterThan(20);
    declarados.forEach((codigo) =>
      expect(SUBMODULOS.has(codigo!)).withContext(`«${codigo}» no es un submodulo real`).toBeTrue()
    );
  });

  it('sin ningun permiso solo queda lo que no exige modulo', () => {
    const visibles = nombres(componente.grupos());

    expect(visibles).toContain('Panel');
    expect(visibles).not.toContain('Almacén');
    expect(visibles).not.toContain('Ventas');
  });

  it('un modulo contratado trae sus submodulos contratados, y solo esos', () => {
    permisos = ['almacen:acceder', 'almacen.producto:acceder', 'almacen.marca:acceder'];

    const visibles = nombres(componente.grupos());

    expect(visibles).toContain('Almacén');
    expect(visibles).toContain('Productos');
    expect(visibles).toContain('Marcas');
    expect(visibles).not.toContain('Kardex');
    expect(visibles).not.toContain('Guías de remisión');
  });

  it('las entradas sin submodulo propio se muestran con el modulo', () => {
    // «Unidades» no tiene fila en `permiso`. Esconderla por no estar anotada
    // borraria una pantalla sin que nadie se entere.
    permisos = ['almacen:acceder'];

    expect(nombres(componente.grupos())).toContain('Unidades');
  });

  it('un modulo cuyos submodulos estan todos apagados desaparece', () => {
    // Un desplegable que no despliega nada se lee como que algo se rompio.
    permisos = ['compras:acceder'];

    expect(nombres(componente.grupos())).not.toContain('Compras');
  });
});
