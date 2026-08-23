package com.ondexia.application.configuracion;

import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.AccesoDenegado;
import com.ondexia.domain.comun.error.Conflicto;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.consultas.DatosDeRuc;
import com.ondexia.domain.consultas.VerificacionDeRuc;
import com.ondexia.domain.identidad.Empresa;
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

    public RegistrarEmpresa(VerificacionDeRuc verificacion, LimitesDeCuentaRepositorio limites,
            EmpresaRepositorio empresas, SucursalRepositorio sucursales,
            UsuarioEmpresaRepositorio asignaciones, ProveedorDeContexto contexto) {
        this.verificacion = verificacion;
        this.limites = limites;
        this.empresas = empresas;
        this.sucursales = sucursales;
        this.asignaciones = asignaciones;
        this.contexto = contexto;
    }

    /**
     * @param atestacion lo que devolvió {@code GET /consultas/ruc/{ruc}}
     * @param nombreComercial nuestro, opcional: SUNAT tiene uno pero las
     *     empresas usan el suyo
     * @param cuentaDetracciones nuestra, opcional
     */
    public record Peticion(String atestacion, String nombreComercial,
            String cuentaDetracciones) {
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

        UUID cuentaId = contexto.obligatorio().cuentaId();

        // 1. La firma. Lanza AtestacionInvalida si no cuadra o si caduco.
        DatosDeRuc datos = verificacion.comprobar(peticion.atestacion());

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

        Empresa empresa = Empresa.registrar(UUID.randomUUID(), cuentaId, datos);
        empresa.renombrarComercialmente(peticion.nombreComercial());
        empresa.anotarCuentaDetracciones(peticion.cuentaDetracciones());
        empresa = empresas.guardar(empresa);

        /*
         * La casa matriz, con codigo 0000.
         *
         * Es el establecimiento que SUNAT asigna al domicilio fiscal y va en cada
         * comprobante. Sin ella, la empresa existe y no puede emitir: el primer
         * intento falla por un establecimiento que nadie recuerda haber tenido que
         * crear. Mismo motivo que en RegistrarCuenta.
         */
        sucursales.guardar(new Sucursal(UUID.randomUUID(), empresa.id(), "0000", "Casa matriz",
                empresa.domicilioFiscal()));

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
}
