package com.ondexia.application.registro;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.Ubigeo;
import com.ondexia.domain.comun.error.Conflicto;
import com.ondexia.domain.identidad.Cuenta;
import com.ondexia.domain.identidad.CuentaAdministrador;
import com.ondexia.domain.identidad.CuentaAdministradorRepositorio;
import com.ondexia.domain.identidad.CuentaRepositorio;
import com.ondexia.domain.consultas.DatosDeRuc;
import com.ondexia.domain.consultas.VerificacionDeRuc;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.identidad.EmpresaRepositorio;
import com.ondexia.domain.identidad.EstadoSuscripcion;
import com.ondexia.domain.identidad.PlanSuscripcion;
import com.ondexia.domain.identidad.RolRepositorio;
import com.ondexia.domain.identidad.Sucursal;
import com.ondexia.domain.identidad.SucursalRepositorio;
import com.ondexia.domain.identidad.Usuario;
import com.ondexia.domain.identidad.UsuarioEmpresa;
import com.ondexia.domain.identidad.UsuarioEmpresaRepositorio;
import com.ondexia.domain.identidad.UsuarioRepositorio;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de un cliente nuevo: la puerta de entrada al sistema.
 *
 * <h2>El huevo y la gallina</h2>
 *
 * <p>Todo lo demás en Ondexia parte de un contexto —quién eres, sobre qué
 * empresa trabajas, qué puedes hacer— resuelto a partir de tu fila en
 * {@code usuario}. Este caso de uso es el único que se ejecuta <em>sin</em> esa
 * fila, porque es el que la crea. Por eso queda fuera del
 * {@code ContextoInterceptor}: pedirle contexto sería pedirle lo que viene a
 * fabricar.
 *
 * <p>Lo que sí exige es un token válido. La identidad ya está probada por
 * Cognito; lo que falta es el negocio.
 *
 * <h2>Por qué el backend no crea el usuario en Cognito</h2>
 *
 * <p>No puede: la Lambda vive en una subred sin salida a internet (DTE §4.8) y
 * la API de Cognito está en internet. El alta en Cognito la hace el SPA antes
 * de llamar aquí, y este método recibe el {@code sub} del token ya emitido —
 * que es también la garantía de que ese registro en Cognito es real y
 * verificado, no un dato que alguien escribió en un formulario.
 *
 * <h2>Todo o nada</h2>
 *
 * <p>Cinco filas en una transacción. Media alta —una cuenta sin empresa, una
 * empresa sin administrador— dejaría al usuario dentro de un sistema donde no
 * puede hacer nada y del que no se puede salir sin tocar la base a mano.
 */
@Service
public class RegistrarCuenta {

    /** Código del rol predefinido que recibe quien registra la cuenta (V2). */
    private static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";

    private final CuentaRepositorio cuentas;
    private final UsuarioRepositorio usuarios;
    private final CuentaAdministradorRepositorio administradores;
    private final EmpresaRepositorio empresas;
    private final VerificacionDeRuc verificacion;
    private final SucursalRepositorio sucursales;
    private final UsuarioEmpresaRepositorio asignaciones;
    private final RolRepositorio roles;

    public RegistrarCuenta(CuentaRepositorio cuentas, UsuarioRepositorio usuarios,
            CuentaAdministradorRepositorio administradores, EmpresaRepositorio empresas,
            VerificacionDeRuc verificacion,
            SucursalRepositorio sucursales, UsuarioEmpresaRepositorio asignaciones,
            RolRepositorio roles) {
        this.cuentas = cuentas;
        this.usuarios = usuarios;
        this.administradores = administradores;
        this.empresas = empresas;
        this.verificacion = verificacion;
        this.sucursales = sucursales;
        this.asignaciones = asignaciones;
        this.roles = roles;
    }

    /**
     * @param cognitoSub del token, no del cuerpo de la petición. Aceptarlo como
     *                   parámetro de entrada permitiría darse de alta
     *                   suplantando a otro
     */
    @Transactional
    public UUID ejecutar(String cognitoSub, String email, DatosDeRegistro datos) {
        // Idempotencia por si el usuario recarga o pulsa dos veces. Sin esto, el
        // segundo intento crearía una segunda cuenta con la misma persona
        // dentro, y el RUC chocaría dejando la primera a medio construir.
        usuarios.buscarPorCognitoSub(cognitoSub).ifPresent(existente -> {
            throw new Conflicto(
                    "ya_registrado",
                    "Esta cuenta de acceso ya tiene un registro completo.");
        });

        /*
         * El RUC y la razon social salen de la atestacion, no del formulario.
         *
         * Es la puerta del onboarding: para crear una cuenta hay que demostrar
         * que el RUC existe, esta ACTIVO y esta HABIDO. Y no es una comprobacion
         * que este caso de uso haga —no puede, la Lambda no sale a internet—
         * sino una firma de ondexia.consultas que aqui solo se verifica (DT-19).
         *
         * Lo que se gana es que no hay forma de crear una cuenta con una razon
         * social inventada. Antes llegaba en el cuerpo y nada la comparaba con
         * el padron; una que no coincide hace que SUNAT rechace todos los
         * comprobantes de esa empresa, y el fallo aparece en la primera emision
         * real — semanas despues del alta.
         */
        DatosDeRuc padron = verificacion.comprobar(datos.atestacion());
        var ruc = padron.ruc();

        /*
         * El RUC es único en TODA la instalación, no por cuenta.
         *
         * Dos clientes no pueden emitir con el mismo RUC: los correlativos se
         * pisarían y la SUNAT vería numeración duplicada del mismo
         * contribuyente. Se comprueba aquí para dar un mensaje decente; la
         * garantía la da el índice único de `empresa.ruc`.
         */
        /*
         * El mensaje NO confirma que el RUC este registrado (hallazgos C2 y M16).
         *
         * Decia «El RUC X ya está registrado en Ondexia», y con eso cualquiera
         * con una cuenta podia recorrer el padron probando RUC y quedarse con la
         * lista de contribuyentes que son clientes nuestros: quien factura con
         * quien, y cuantos somos. Es informacion comercial de nuestros clientes
         * y nuestra, y la entregaba un endpoint de alta.
         *
         * El codigo tambien cambia: `ruc_ya_registrado` distinguia el caso por si
         * solo, aunque el texto no lo dijera.
         *
         * Lo que se pierde es claridad para quien tiene un motivo legitimo, y por
         * eso el mensaje dice que hacer sin decir por que. Quien sea de esa
         * empresa lo entiende; quien esta enumerando, no aprende nada.
         */
        empresas.buscarPorRuc(ruc).ifPresent(existente -> {
            throw new Conflicto(
                    "registro_no_disponible",
                    "No se pudo completar el registro con estos datos. Si tu empresa ya "
                            + "trabaja con Ondexia, pide a su administrador que te dé acceso; "
                            + "si no, escríbenos.",
                    "ruc");
        });

        var cuenta = cuentas.guardar(new Cuenta(
                UUID.randomUUID(),
                // El nombre de la cuenta es la razon social del padron. Se puede
                // renombrar despues: la cuenta es la unidad comercial y su
                // nombre no aparece en ningun comprobante.
                padron.razonSocial(),
                PlanSuscripcion.PROFESIONAL,
                // Nace en prueba. El cobro es de la Entrega de suscripción; lo
                // que importa hoy es que el estado exista desde el primer día y
                // no haya que migrar cuentas cuando llegue.
                EstadoSuscripcion.EN_PRUEBA));

        var usuario = usuarios.guardar(new Usuario(
                UUID.randomUUID(),
                cuenta.id(),
                cognitoSub,
                email,
                datos.nombreTitular(),
                datos.apellidoTitular(),
                // El teléfono no se pide en el alta: son cinco campos para poder
                // empezar, y uno más que no bloquea nada es uno menos que se
                // rellena. Se añade desde Mi perfil.
                null,
                true));

        // Quien registra es administrador de su cuenta: gobierna la suscripción
        // y el alta de empresas. No es un rol de la matriz de permisos.
        administradores.guardar(new CuentaAdministrador(
                UUID.randomUUID(), cuenta.id(), usuario.id()));

        /*
         * Empresa.registrar y no el constructor: exige que el RUC este apto y
         * copia los campos del padron —estado, condicion, ubigeo, distrito, forma
         * societaria— con su fecha de verificacion. El constructor publico no
         * comprueba nada y dejaria una empresa sin verificar el primer dia.
         */
        var empresa = empresas.guardar(Empresa.registrar(UUID.randomUUID(), cuenta.id(), padron));

        /*
         * Casa matriz, código 0000. Se crea sola y no se pregunta.
         *
         * SUNAT la exige siempre y sin ella no se puede emitir: pedirla en el
         * formulario sería pedir un dato que solo puede tener un valor, y
         * dejarla para después sería dejar al cliente en un sistema que no
         * factura sin decirle por qué.
         */
        var matriz = new Sucursal(UUID.randomUUID(), empresa.id(), "0000", "Casa matriz",
                empresa.domicilioFiscal(), empresa.ubigeo(), true);

        sucursales.guardar(matriz);

        /*
         * La asignación a la empresa. Sin esta fila el alta parece completa y no
         * lo está.
         *
         * Ser administrador de la CUENTA no da ningún permiso dentro de una
         * empresa: son dos cosas distintas a propósito —una gobierna la
         * suscripción, la otra el día a día— y los permisos se resuelven desde
         * `usuario_empresa`. Sin ella, `ResolverContexto` no encuentra ninguna
         * empresa a la que el usuario tenga acceso, no hay empresa activa, y
         * todas las pantallas responden `sin_empresa_activa`.
         *
         * Es exactamente lo que ocurrió en el primer registro real: el usuario
         * entraba al panel y no podía abrir nada.
         *
         * `sucursalId` va a null: alcance sobre TODAS las sucursales de la
         * empresa. Atar al fundador a su casa matriz le impediría operar en los
         * establecimientos que abra después.
         */
        var administrador = roles.buscarPredefinido(ROL_ADMINISTRADOR)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el rol predefinido " + ROL_ADMINISTRADOR
                                + ". Lo crea la migración V2; revisa que se haya aplicado."));

        asignaciones.guardar(new UsuarioEmpresa(
                UUID.randomUUID(), usuario.id(), empresa.id(), administrador.id(), null));

        // No se escribe en la bitácora. La tabla `auditoria` tiene RLS por
        // empresa y aquí todavía no hay contexto que fijar: la fila se
        // rechazaría. El alta queda registrada por los propios `creado_en`.
        return cuenta.id();
    }

    /**
     * @param nombreTitular   de la persona, no de la empresa. Es quien firma el
     *                        alta y queda como administrador
     * @param apellidoTitular se pide aparte y no como «nombre completo» porque
     *                        partirlo después obligaría a adivinar dónde acaba
     *                        el nombre, y en «María del Carmen Rojas» no hay
     *                        forma de acertar
     * @param atestacion      lo que devolvió {@code GET /consultas/ruc/{ruc}}.
     *                        De ahí salen el RUC, la razón social, el domicilio y
     *                        el ubigeo: ninguno llega por el formulario, y por eso
     *                        no se pueden inventar
     */
    public record DatosDeRegistro(
            String atestacion,
            String nombreTitular,
            String apellidoTitular) {
    }
}
