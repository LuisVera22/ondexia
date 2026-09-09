import { TestBed } from '@angular/core/testing';
import { ConfiguracionApiService, ModuloApi, RolApi } from '../../../nucleo/configuracion.api.service';
import { RolesComponent } from './roles.component';

/**
 * La cascada de la matriz de permisos.
 *
 * <p>Es la lógica más intrincada del frontend y la que sostiene una promesa
 * concreta: <strong>lo que se ve marcado es exactamente lo que autoriza</strong>.
 * El servidor poda al guardar lo que se queda sin padre, así que si la pantalla
 * dejara casillas marcadas bajo un módulo apagado, mostraría permisos que no
 * existen.
 *
 * <p>Se instancia el componente sin renderizar la plantilla —{@code
 * runInInjectionContext} en vez de {@code createComponent}— porque lo que se
 * prueba son decisiones, no marcado. Renderizar arrastraría el encabezado, el
 * enrutador y el diálogo de confirmación, y un fallo en cualquiera de ellos
 * teñiría de rojo una prueba que no habla de eso.
 */
describe('RolesComponent · cascada de permisos', () => {

  const CATALOGO: ModuloApi[] = [
    {
      id: 'mod-almacen',
      codigo: 'almacen',
      nombre: 'Almacén',
      descripcion: null,
      submodulos: [
        {
          id: 'sub-producto',
          codigo: 'almacen.producto',
          nombre: 'Productos',
          funciones: [
            { id: 'fn-prod-consultar', accion: 'consultar', nombre: 'Consultar', codigo: 'almacen.producto:consultar' },
            { id: 'fn-prod-registrar', accion: 'registrar', nombre: 'Registrar', codigo: 'almacen.producto:registrar' },
          ],
        },
        {
          id: 'sub-almacen',
          codigo: 'almacen.almacen',
          nombre: 'Almacenes',
          funciones: [
            { id: 'fn-alm-consultar', accion: 'consultar', nombre: 'Consultar', codigo: 'almacen.almacen:consultar' },
          ],
        },
      ],
    },
  ];

  const A_MEDIDA: RolApi = {
    id: 'rol-propio',
    codigo: 'VENDEDOR_PROPIO',
    nombre: 'Vendedor propio',
    descripcion: null,
    delSistema: false,
    cantidadPermisos: 0,
    enUso: false,
  };

  const DEL_SISTEMA: RolApi = { ...A_MEDIDA, id: 'rol-sistema', delSistema: true };

  /** Todo marcado: módulo, sus dos submódulos y sus tres funciones. */
  const TODO = [
    'mod-almacen',
    'sub-producto', 'fn-prod-consultar', 'fn-prod-registrar',
    'sub-almacen', 'fn-alm-consultar',
  ];

  let componente: RolesComponent;

  beforeEach(() => {
    const api = jasmine.createSpyObj<ConfiguracionApiService>('ConfiguracionApiService', [
      'roles', 'catalogoPermisos', 'permisosDelRol',
    ]);
    api.roles.and.resolveTo([]);
    api.catalogoPermisos.and.resolveTo([]);
    api.permisosDelRol.and.resolveTo([]);

    TestBed.configureTestingModule({
      providers: [{ provide: ConfiguracionApiService, useValue: api }],
    });

    componente = TestBed.runInInjectionContext(() => new RolesComponent());
    componente.catalogo.set(CATALOGO);
    componente.seleccionado.set(A_MEDIDA);
  });

  it('apagar el módulo desmarca todo lo que cuelga de él', () => {
    componente.marcados.set(new Set(TODO));

    componente.alternarModulo(CATALOGO[0]);

    expect(componente.marcados().size)
      .withContext('no queda nada marcado bajo un módulo apagado')
      .toBe(0);
  });

  it('encender el módulo no marca nada de dentro', () => {
    // La asimetría es intencionada. Apagar arrastra porque si no, la pantalla
    // mostraría casillas que el servidor va a podar. Encender no debe
    // arrastrar: quien abre un módulo está empezando a componer, y marcarle
    // treinta casillas de golpe le concede cosas que no ha mirado.
    componente.marcados.set(new Set());

    componente.alternarModulo(CATALOGO[0]);

    expect([...componente.marcados()])
      .withContext('solo el propio módulo')
      .toEqual(['mod-almacen']);
  });

  it('con el módulo apagado no se puede marcar un submódulo', () => {
    componente.marcados.set(new Set());

    componente.alternarSubmodulo(CATALOGO[0], CATALOGO[0].submodulos[0]);

    expect(componente.marcados().size).toBe(0);
    expect(componente.bloqueadoPorModulo(CATALOGO[0])).toBeTrue();
  });

  it('desmarcar un submódulo se lleva sus funciones', () => {
    componente.marcados.set(new Set(TODO));

    componente.alternarSubmodulo(CATALOGO[0], CATALOGO[0].submodulos[0]);

    const vigentes = componente.marcados();
    expect(vigentes.has('sub-producto')).withContext('el submódulo').toBeFalse();
    expect(vigentes.has('fn-prod-consultar')).withContext('sus funciones').toBeFalse();
    expect(vigentes.has('fn-prod-registrar')).toBeFalse();
    expect(vigentes.has('sub-almacen')).withContext('el otro submódulo no se toca').toBeTrue();
  });

  it('una función bajo un submódulo apagado no se puede marcar', () => {
    componente.marcados.set(new Set(['mod-almacen']));

    componente.alternarFuncion(CATALOGO[0], CATALOGO[0].submodulos[0], 'fn-prod-consultar');

    expect(componente.marcados().has('fn-prod-consultar')).toBeFalse();
  });

  it('un rol predefinido no se deja tocar', () => {
    componente.seleccionado.set(DEL_SISTEMA);
    componente.marcados.set(new Set(TODO));

    componente.alternarModulo(CATALOGO[0]);
    componente.alternarSubmodulo(CATALOGO[0], CATALOGO[0].submodulos[0]);
    componente.alternarFuncion(CATALOGO[0], CATALOGO[0].submodulos[0], 'fn-prod-consultar');

    expect(componente.soloLectura).toBeTrue();
    expect(componente.marcados().size)
      .withContext('nada cambió')
      .toBe(TODO.length);
  });

  it('el contador cuenta funciones, no módulos ni submódulos', () => {
    // Es lo que el usuario entiende por «permisos». Contar los tres niveles
    // daría un número que no se corresponde con ninguna casilla de acción.
    componente.marcados.set(new Set(TODO));

    expect(componente.totalFunciones()).toBe(3);
  });

  it('«Todas» marca las funciones del submódulo de una vez', () => {
    componente.marcados.set(new Set(['mod-almacen', 'sub-producto']));

    componente.alternarTodasLasFunciones(CATALOGO[0], CATALOGO[0].submodulos[0]);

    expect(componente.marcados().has('fn-prod-consultar')).toBeTrue();
    expect(componente.marcados().has('fn-prod-registrar')).toBeTrue();
  });
});
