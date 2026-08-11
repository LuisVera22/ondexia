package com.ondexia.api.comun.seguridad;

import java.util.UUID;

/**
 * Quien hace esta peticion, sobre que empresa y con que alcance.
 *
 * <p>Se resuelve <strong>en la base de datos, en cada peticion</strong>. El
 * token de Cognito porta identidad y nada mas (DTE §8.1). Dos razones:
 *
 * <ul>
 *   <li>Un JWT es valido hasta que caduca. Si los permisos viajaran dentro,
 *       revocar «anular comprobante» no surtiria efecto hasta la renovacion del
 *       token — y quien esta a punto de hacer dano no va a renovar.</li>
 *   <li>Unos 200 permisos no caben en una cabecera que viaja en cada
 *       llamada.</li>
 * </ul>
 *
 * <p><strong>El contexto que envia el cliente no se cree nunca.</strong> El
 * frontend manda una cabecera con la empresa activa; el servidor comprueba
 * contra {@code usuario_empresa} que ese usuario tiene esa asignacion. No es
 * celo de manual: la empresa determina <em>con que certificado digital se
 * firma</em>. Un {@code empresa_id} falsificado no seria ver datos ajenos,
 * seria emitir un comprobante firmado con el certificado de otro RUC.
 *
 * @param empresaId              empresa activa; {@code null} si el usuario aun
 *                               no ha elegido una y tiene varias
 * @param sucursalId             alcance dentro de la empresa; {@code null}
 *                               significa todas
 * @param rolId                  rol del usuario <em>en esa empresa</em>. Cambia
 *                               al cambiar de empresa
 * @param permisosVersion        version del catalogo de permisos de la cuenta,
 *                               para invalidar el cache. Ver {@code Cuenta}
 * @param esAdministradorCuenta  gobierna la suscripcion, no un modulo. Ver
 *                               {@code CuentaAdministrador}
 */
public record ContextoPeticion(
        UUID usuarioId,
        UUID cuentaId,
        long permisosVersion,
        UUID empresaId,
        UUID sucursalId,
        UUID rolId,
        boolean esAdministradorCuenta,
        String ip) {

    public boolean tieneEmpresaActiva() {
        return empresaId != null;
    }

    public boolean alcanzaTodasLasSucursales() {
        return sucursalId == null;
    }

    /**
     * Empresa activa, o error si no la hay.
     *
     * <p>Devolver el nulo hacia arriba llevaria a un {@code NullPointerException}
     * en algun punto lejano, o peor, a una consulta con {@code empresa_id is
     * null} que no filtra nada. Fallar aqui produce un 400 con un mensaje que
     * dice exactamente que falta.
     */
    public UUID empresaActivaObligatoria() {
        if (empresaId == null) {
            throw new IllegalStateException(
                    "La peticion no tiene empresa activa: falta la cabecera "
                            + ContextoInterceptor.CABECERA_EMPRESA);
        }
        return empresaId;
    }
}
