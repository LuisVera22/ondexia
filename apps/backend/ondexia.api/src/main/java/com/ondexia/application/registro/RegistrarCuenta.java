package com.ondexia.application.registro;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.Ubigeo;
import com.ondexia.domain.comun.error.Conflicto;
import com.ondexia.domain.identidad.Cuenta;
import com.ondexia.domain.identidad.CuentaAdministrador;
import com.ondexia.domain.identidad.CuentaAdministradorRepositorio;
import com.ondexia.domain.identidad.CuentaRepositorio;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.identidad.EmpresaRepositorio;
import com.ondexia.domain.identidad.EstadoSuscripcion;
import com.ondexia.domain.identidad.PlanSuscripcion;
import com.ondexia.domain.identidad.Sucursal;
import com.ondexia.domain.identidad.SucursalRepositorio;
import com.ondexia.domain.identidad.Usuario;
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

    private final CuentaRepositorio cuentas;
    private final UsuarioRepositorio usuarios;
    private final CuentaAdministradorRepositorio administradores;
    private final EmpresaRepositorio empresas;
    private final SucursalRepositorio sucursales;

    public RegistrarCuenta(CuentaRepositorio cuentas, UsuarioRepositorio usuarios,
            CuentaAdministradorRepositorio administradores, EmpresaRepositorio empresas,
            SucursalRepositorio sucursales) {
        this.cuentas = cuentas;
        this.usuarios = usuarios;
        this.administradores = administradores;
        this.empresas = empresas;
        this.sucursales = sucursales;
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

        var ruc = new Ruc(datos.ruc());

        /*
         * El RUC es único en TODA la instalación, no por cuenta.
         *
         * Dos clientes no pueden emitir con el mismo RUC: los correlativos se
         * pisarían y la SUNAT vería numeración duplicada del mismo
         * contribuyente. Se comprueba aquí para dar un mensaje decente; la
         * garantía la da el índice único de `empresa.ruc`.
         */
        empresas.buscarPorRuc(ruc).ifPresent(existente -> {
            throw new Conflicto(
                    "ruc_ya_registrado",
                    "El RUC " + ruc.valor() + " ya está registrado en Ondexia. "
                            + "Si es tu empresa, pide a su administrador que te dé acceso.");
        });

        var cuenta = cuentas.guardar(new Cuenta(
                UUID.randomUUID(),
                datos.razonSocial(),
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
                true));

        // Quien registra es administrador de su cuenta: gobierna la suscripción
        // y el alta de empresas. No es un rol de la matriz de permisos.
        administradores.guardar(new CuentaAdministrador(
                UUID.randomUUID(), cuenta.id(), usuario.id()));

        var empresa = empresas.guardar(new Empresa(
                UUID.randomUUID(), cuenta.id(), ruc, datos.razonSocial(), datos.domicilioFiscal()));

        /*
         * Casa matriz, código 0000. Se crea sola y no se pregunta.
         *
         * SUNAT la exige siempre y sin ella no se puede emitir: pedirla en el
         * formulario sería pedir un dato que solo puede tener un valor, y
         * dejarla para después sería dejar al cliente en un sistema que no
         * factura sin decirle por qué.
         */
        var matriz = new Sucursal(
                UUID.randomUUID(), empresa.id(), "0000", "Casa matriz", datos.domicilioFiscal());

        if (datos.ubigeo() != null && !datos.ubigeo().isBlank()) {
            var ubigeo = new Ubigeo(datos.ubigeo());
            empresa.actualizarDatosFiscales(
                    datos.razonSocial(), null, datos.domicilioFiscal(), ubigeo);
            empresas.guardar(empresa);
            matriz.actualizar(matriz.nombre(), matriz.direccion(), ubigeo);
        }

        sucursales.guardar(matriz);

        // No se escribe en la bitácora. La tabla `auditoria` tiene RLS por
        // empresa y aquí todavía no hay contexto que fijar: la fila se
        // rechazaría. El alta queda registrada por los propios `creado_en`.
        return cuenta.id();
    }

    /**
     * @param nombreTitular de la persona, no de la empresa. Es quien firma el
     *                      alta y queda como administrador
     * @param ubigeo        opcional mientras no haya integración con SUNAT
     */
    public record DatosDeRegistro(
            String ruc,
            String razonSocial,
            String domicilioFiscal,
            String ubigeo,
            String nombreTitular) {
    }
}
