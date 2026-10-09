package com.ondexia.domain.comun;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.util.UUID;

/**
 * Quién está operando, sobre qué empresa y con qué alcance.
 *
 * <p>Vive en el dominio, no en la capa web, porque es un concepto de negocio:
 * «este usuario, sobre esta empresa» es lo que decide qué se puede hacer y
 * **con qué certificado digital se firma**. Que llegue por HTTP es un detalle
 * del adaptador.
 *
 * <p>Se resuelve en la base en cada petición. El token de Cognito porta
 * identidad y nada más (DTE §8.1): un JWT vale hasta que caduca, y revocar
 * «anular comprobante» no puede esperar a la renovación.
 *
 * @param empresaId       empresa activa; {@code null} si el usuario tiene
 *                        varias y aún no ha elegido
 * @param sucursalId      alcance dentro de la empresa; {@code null} = todas
 * @param rolId           rol <em>en esa empresa</em>. Cambia al cambiar de empresa
 * @param permisosVersion versión del catálogo de permisos de la cuenta
 * @param soloLectura     la suscripción no está vigente. El usuario entra y
 *                        consulta, pero no escribe. Ver doc 09 §5.1: cortar el
 *                        acceso a los datos por una factura impaga le
 *                        trasladaría al cliente un problema tributario, porque
 *                        responde ante SUNAT de comprobantes que debe conservar
 *                        cinco años
 */
public record ContextoOperacion(
        UUID usuarioId,
        /**
         * El {@code sub} de Cognito de quien opera (hallazgo M17).
         *
         * <p>Es lo que ata una atestación de RUC a quien la pidió: la firma de
         * {@code ondexia.consultas} incluye el {@code sub} del solicitante y la
         * API exige que coincida con el de quien la presenta. Sin esto, una
         * atestación era reenviable por cualquiera durante sus diez minutos.
         */
        String sub,
        UUID cuentaId,
        long permisosVersion,
        UUID empresaId,
        UUID sucursalId,
        UUID rolId,
        boolean esAdministradorCuenta,
        boolean soloLectura,
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
     * <p>Devolver el nulo hacia arriba acabaría en un {@code NullPointerException}
     * lejano o, peor, en una consulta con {@code empresa_id is null} que no
     * filtra nada.
     */
    public UUID empresaActivaObligatoria() {
        if (empresaId == null) {
            throw new ReglaDeNegocioViolada(
                    "sin_empresa_activa",
                    "La operación necesita una empresa activa y no se indicó ninguna.");
        }
        return empresaId;
    }
}
