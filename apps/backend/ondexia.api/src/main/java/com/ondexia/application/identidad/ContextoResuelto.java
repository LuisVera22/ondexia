package com.ondexia.application.identidad;

import com.ondexia.domain.identidad.AsignacionEmpresa;
import com.ondexia.domain.identidad.Sucursal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Salida de {@link ConsultarContexto}. Modelo de la capa de aplicación, no de
 * transporte: el DTO que sale por HTTP se construye a partir de este.
 *
 * @param empresas todas a las que el usuario tiene acceso. Alimenta el selector,
 *                 que solo aparece si hay más de una
 * @param establecimientos los de la empresa activa que el usuario alcanza, ya
 *                 recortados a su asignación. Alimenta el otro selector, y va
 *                 aquí y no en una llamada aparte a la configuración porque
 *                 elegir establecimiento no es configurar: determina la serie
 *                 del comprobante y el almacén que descarga existencias, de modo
 *                 que quien solo vende tiene que poder cambiarlo sin alcanzar el
 *                 módulo de configuración
 * @param permisos códigos {@code modulo:accion} del rol en la empresa activa. El
 *                 frontend los usa para ocultar lo que no corresponde, que es
 *                 <strong>comodidad, no seguridad</strong>: cada endpoint los
 *                 vuelve a comprobar
 * @param estadoSuscripcion estado de la cuenta. Lo necesita el SPA para pintar el
 *                 anuncio del doc 09 §5.1, y va aquí y no deducido de
 *                 {@code soloLectura} porque el aviso de una cuenta cancelada no
 *                 dice lo mismo que el de una suspendida
 * @param soloLectura si la suscripción no permite escribir. Es redundante con el
 *                 estado a propósito: el frontend no debe replicar la regla de
 *                 qué estados escriben, porque entonces habría dos
 */
public record ContextoResuelto(
        UUID usuarioId,
        String nombre,
        String email,
        UUID cuentaId,
        boolean esAdministradorCuenta,
        UUID empresaActivaId,
        UUID sucursalActivaId,
        List<AsignacionEmpresa> empresas,
        List<Sucursal> establecimientos,
        Set<String> permisos,
        String estadoSuscripcion,
        boolean soloLectura) {
}
