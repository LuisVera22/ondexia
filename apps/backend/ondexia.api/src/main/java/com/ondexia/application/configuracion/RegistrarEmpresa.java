package com.ondexia.application.configuracion;

import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comun.ContextoOperacion;
import com.ondexia.domain.comun.OperarComoEmpresa;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.AccesoDenegado;
import com.ondexia.domain.comun.error.Conflicto;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.consultas.DatosDeRuc;
import com.ondexia.domain.consultas.VerificacionDeRuc;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.identidad.RegimenTributario;
import com.ondexia.domain.identidad.EmpresaRepositorio;
import com.ondexia.domain.identidad.LimitesDeCuenta;
import com.ondexia.domain.identidad.LimitesDeCuentaRepositorio;
import com.ondexia.domain.identidad.Sucursal;
import com.ondexia.domain.identidad.SucursalRepositorio;
import com.ondexia.domain.identidad.UsuarioEmpresa;
import com.ondexia.domain.identidad.UsuarioEmpresaRepositorio;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dar de alta una empresa desde dentro del ERP.
 *
 * <h2>De dónde salen los datos</h2>
 *
 * <p>Los de SUNAT, de la atestación firmada; los nuestros, del formulario. No
 * hay una tercera vía: la razón social, el domicilio, el ubigeo, el estado y la
 * condición <strong>no llegan en la petición</strong>. Se sacan de la firma.
 *
 * <p>Eso convierte el «no editable» de esos campos en algo que no se puede
 * saltar. Con las herramientas del navegador se cambia cualquier campo de un
 * formulario; lo que no se puede es firmar.
 *
 * <h2>El orden de las comprobaciones importa</h2>
 *
 * <p>Primero la firma, después el cupo, después el RUC repetido. Y no es
 * casual: comprobar el cupo antes de la firma diría «has alcanzado tu límite» a
 * quien envía una atestación falsificada, que es información gratis sobre el
 * estado de la cuenta. Y comprobar el RUC repetido antes del cupo permitiría
 * usar este endpoint para averiguar qué RUC están dados de alta en Ondexia sin
 * tener sitio para registrar ninguno.
 */
@Service
public class RegistrarEmpresa {

    private final VerificacionDeRuc verificacion;
    private final LimitesDeCuentaRepositorio limites;
    private final EmpresaRepositorio empresas;
    private final SucursalRepositorio sucursales;
    private final UsuarioEmpresaRepositorio asignaciones;
    private final ProveedorDeContexto contexto;
    private final RegistroDeAuditoria auditoria;
    private final OperarComoEmpresa operar;
    private final DotacionDeEstablecimiento dotacion;

    public RegistrarEmpresa(VerificacionDeRuc verificacion, LimitesDeCuentaRepositorio limites,
            EmpresaRepositorio empresas, SucursalRepositorio sucursales,
            UsuarioEmpresaRepositorio asignaciones, ProveedorDeContexto contexto,
            RegistroDeAuditoria auditoria, OperarComoEmpresa operar,
            DotacionDeEstablecimiento dotacion) {
        this.verificacion = verificacion;
        this.limites = limites;
        this.empresas = empresas;
        this.sucursales = sucursales;
        this.asignaciones = asignaciones;
        this.contexto = contexto;
        this.auditoria = auditoria;
        this.operar = operar;
        this.dotacion = dotacion;
    }

    /**
     * @param atestacion lo que devolvió {@code GET /consultas/ruc/{ruc}}
     * @param nombreComercial nuestro, opcional: SUNAT tiene uno pero las
     *     empresas usan el suyo
     * @param cuentaDetracciones nuestra, opcional
     */
    public record Peticion(String atestacion, String nombreComercial,
            String cuentaDetracciones, boolean nuevoRus) {
    }

    @Transactional
    public Empresa ejecutar(Peticion peticion) {
        /*
         * Administrador de la cuenta, y no un permiso de la matriz de roles.
         *
         * Lo decide la V2, que lo dice explicitamente al conceder permisos al rol
         * ADMINISTRADOR: «esto NO incluye gobernar la suscripcion ni crear
         * empresas. Eso es cuenta_administrador, que no pasa por esta matriz».
         *
         * Y el motivo aguanta: dar de alta una empresa consume cupo del plan, que
         * es un asunto del contrato y no de lo que alguien pueda hacer dentro de
         * una empresa concreta. Un permiso por empresa seria ademas raro de
         * evaluar — el permiso se tendria en una empresa para crear otra.
         *
         * Va aqui y no en una anotacion del controlador a proposito: la regla
         * viaja con el caso de uso, asi que un endpoint nuevo o una tarea
         * programada que lo invoque no puede saltarsela por olvido.
         */
        if (!contexto.obligatorio().esAdministradorCuenta()) {
            throw new AccesoDenegado(
                    "Solo el administrador de la cuenta puede registrar empresas.");
        }

        var actual = contexto.obligatorio();
        UUID cuentaId = actual.cuentaId();

        /*
         * Hallazgo M5: dos comprobaciones que faltaban.
         *
         * Una cuenta en solo lectura —suscripcion caida, doc 09 §5.1— no puede
         * dar de alta empresas. El recorte de permisos lo aplica
         * PermisosEfectivos, pero este caso de uso no pasa por la matriz de
         * permisos (ver arriba), asi que el recorte no le alcanzaba: era la unica
         * escritura que una cuenta suspendida seguia pudiendo hacer.
         *
         * Y hace falta una empresa activa. La asignacion de mas abajo copia el
         * rol de la empresa desde la que se registra; sin empresa activa ese rol
         * es nulo y el INSERT reventaba con un 500 que no decia por que.
         */
        if (actual.soloLectura()) {
            throw new ReglaDeNegocioViolada("cuenta_solo_lectura",
                    "La cuenta está en solo lectura y no puede registrar empresas.");
        }
        actual.empresaActivaObligatoria();

        // 1. La firma. Lanza AtestacionInvalida si no cuadra, si caduco o si la
        // pidio otra persona (M17).
        DatosDeRuc datos = verificacion.comprobar(peticion.atestacion(), actual.sub());

        // 2. El cupo del plan.
        LimitesDeCuenta cupo = limites.de(cuentaId);
        if (!cupo.cabeOtraEmpresa()) {
            throw new ReglaDeNegocioViolada("limite_de_empresas", cupo.motivoDelTope());
        }

        /*
         * 3. El RUC repetido.
         *
         * Es unico GLOBAL, no por cuenta (V1). Si dos clientes dieran de alta el
         * mismo RUC habria dos sistemas emitiendo contra el mismo contribuyente y
         * los correlativos se pisarian — que es un problema fiscal, no un
         * inconveniente.
         *
         * Se comprueba aqui para dar un mensaje util, y ademas lo garantiza el
         * indice unico: entre esta consulta y el insert cabe otra transaccion.
         */
        empresas.buscarPorRuc(datos.ruc()).ifPresent(existente -> {
            throw new Conflicto("ruc_ya_registrado",
                    "El RUC " + datos.ruc() + " ya está registrado en Ondexia. "
                            + "Si es tu empresa, pide a su administrador que te dé acceso.",
                    "ruc");
        });

        Empresa empresa = Empresa.registrar(UUID.randomUUID(), cuentaId, datos,
                peticion.nuevoRus() ? RegimenTributario.NUEVO_RUS : RegimenTributario.OTRO);
        empresa.renombrarComercialmente(peticion.nombreComercial());
        empresa.anotarCuentaDetracciones(peticion.cuentaDetracciones());
        empresa = empresas.guardar(empresa);

        /*
         * A la bitacora (M5). Dar de alta una empresa es de las pocas cosas que
         * cambian lo que factura la cuenta, y no dejaba rastro. Va como
         * instantanea y no como el agregado: Empresa lleva usuarioSol y el ARN
         * del certificado, que no deben acabar en datos_despues (M8).
         */
        auditoria.registrarCreacion("empresa", empresa.id(),
                new Instantanea(empresa.ruc().valor(), empresa.razonSocial(),
                        empresa.nombreComercial()));

        /*
         * La casa matriz, con codigo 0000.
         *
         * Es el establecimiento que SUNAT asigna al domicilio fiscal y va en cada
         * comprobante. Sin ella, la empresa existe y no puede emitir: el primer
         * intento falla por un establecimiento que nadie recuerda haber tenido que
         * crear. Mismo motivo que en RegistrarCuenta.
         */
        var matriz = sucursales.guardar(new Sucursal(UUID.randomUUID(), empresa.id(), "0000",
                "Casa matriz", empresa.domicilioFiscal()));

        /*
         * Y la asignacion a quien la registro. Sin esta fila el alta parece
         * completa y la empresa no aparece en el selector de contexto: se ha
         * creado algo que su propio autor no alcanza.
         *
         * Con el MISMO rol que tiene en la empresa desde la que registro.
         *
         * Los roles son de la cuenta, no de la empresa, asi que el identificador
         * vale igual en la nueva. Y es lo conservador: el alta lo autoriza ser
         * administrador de la cuenta, no el rol, asi que copiar el rol no concede
         * nada que esta persona no tuviera ya en otra empresa.
         *
         * Sin sucursal: alcance a todas. La unica que hay es la casa matriz que
         * se acaba de crear, y limitarla a ella daria el mismo resultado hoy y
         * uno equivocado el dia que aparezca un anexo.
         */
        asignaciones.guardar(new UsuarioEmpresa(UUID.randomUUID(),
                contexto.obligatorio().usuarioId(), empresa.id(),
                contexto.obligatorio().rolId(), null));

        /*
         * Su almacén y su primera caja. Quien registra opera sobre OTRA empresa
         * —la activa en la cabecera— y el RLS de `almacen` y `caja` rechazaría
         * filas de la nueva. Se cambia de empresa solo para esto, con la misma
         * identidad y sin alcance de sucursal, y el puerto deja todo como estaba.
         */
        operar.ejecutar(new ContextoOperacion(actual.usuarioId(), actual.sub(), actual.cuentaId(),
                        actual.permisosVersion(), empresa.id(), null, actual.rolId(),
                        actual.esAdministradorCuenta(), false, actual.ip()),
                () -> dotacion.dotar(matriz));

        return empresa;
    }

    /**
     * Si esta cuenta puede registrar otra empresa, y si no, por qué.
     *
     * <p>Lo consulta la pantalla para decidir si enseña el botón. Sin esto, la
     * única forma de saberlo es intentarlo y leer el error — es decir, rellenar
     * el formulario entero para que al enviarlo diga que no cabía.
     */
    public LimitesDeCuenta cupo() {
        return limites.de(contexto.obligatorio().cuentaId());
    }

    private record Instantanea(String ruc, String razonSocial, String nombreComercial) {
    }
}
