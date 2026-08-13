package com.ondexia.application.configuracion;

import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.domain.marca.AlmacenDeMarca;
import com.ondexia.domain.marca.IdentidadVisual;
import com.ondexia.domain.marca.IdentidadVisualRepositorio;
import com.ondexia.domain.marca.LogoDeEmpresa;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los logos de la empresa activa.
 *
 * <h2>La subida ocurre en dos pasos, y no es por comodidad</h2>
 *
 * <ol>
 *   <li>{@link #autorizarSubida} firma un permiso para subir <em>ese</em>
 *       archivo, con su tipo y su tamaño dentro de la firma.</li>
 *   <li>El navegador sube directo al almacén. Nuestra API no ve el archivo.</li>
 *   <li>{@link #confirmarSubida} pregunta al almacén qué llegó de verdad y, solo
 *       entonces, guarda la clave.</li>
 * </ol>
 *
 * <p>El tercer paso es el que hace honesto al primero. Entre la firma y la
 * subida el cliente puede mandar otra cosa —o no mandar nada— y la fila no debe
 * apuntar a un objeto que no existe o que no es lo que se dijo. Quien llama a la
 * API a mano lo intentará.
 *
 * <h2>Reemplazar no borra</h2>
 *
 * <p>El objeto anterior se queda donde estaba. Los comprobantes ya emitidos lo
 * referencian por su clave, y borrarlo dejaría documentos con un hueco donde iba
 * el logo — reescribir la historia en silencio es justo lo que el versionado del
 * bucket pretende evitar (DTE §5.8).
 *
 * <p>«Quitar» sí borra: significa «no quiero logo», no «cambié de logo», y en ese
 * caso lo que hay que retirar es el archivo.
 */
@Service
public class Identidad {

    private final IdentidadVisualRepositorio identidades;
    private final AlmacenDeMarca almacen;
    private final RegistroDeAuditoria auditoria;
    private final ProveedorDeContexto contexto;

    public Identidad(IdentidadVisualRepositorio identidades, AlmacenDeMarca almacen,
            RegistroDeAuditoria auditoria, ProveedorDeContexto contexto) {
        this.identidades = identidades;
        this.almacen = almacen;
        this.auditoria = auditoria;
        this.contexto = contexto;
    }

    /**
     * @param url {@code null} cuando no hay archivo cargado. La pantalla lo
     *            distingue de la cadena vacía para pintar el hueco
     */
    public record EstadoDeLogo(LogoDeEmpresa logo, String url, long maximoBytes) {
    }

    public List<EstadoDeLogo> consultar() {
        var identidad = identidades.buscar().orElse(null);

        return Arrays.stream(LogoDeEmpresa.values())
                .map(logo -> new EstadoDeLogo(
                        logo,
                        identidad == null
                                ? null
                                : identidad.clave(logo).map(almacen::urlPublica).orElse(null),
                        logo.maximoBytes()))
                .toList();
    }

    /**
     * Firma el permiso de subida. No toca la base: hasta que no se confirme, no
     * ha pasado nada.
     *
     * @param bytes tamaño que el cliente declara. Se valida aquí para no firmar
     *              una subida que vamos a rechazar, y otra vez al confirmar
     *              contra lo que el almacén recibió de verdad
     */
    public AlmacenDeMarca.AutorizacionDeSubida autorizarSubida(
            String columna, String tipoContenido, long bytes) {
        var logo = LogoDeEmpresa.porColumna(columna);
        String extension = logo.exigirValido(tipoContenido, bytes);

        /*
         * Clave nueva en cada subida, con UUID.
         *
         * Sobrescribir la anterior sería más simple y rompería dos cosas: los
         * documentos que la referencian, y la caché de CloudFront —que habría
         * que invalidar, y las invalidaciones se pagan a partir de mil al mes—.
         * Con clave nueva, una URL nueva: nada que invalidar nunca.
         */
        String clave = "empresas/%s/%s/%s.%s".formatted(
                empresaActiva(), logo.columna(), UUID.randomUUID(), extension);

        return almacen.autorizarSubida(clave, tipoContenido, bytes);
    }

    /**
     * Da la subida por buena tras comprobar en el almacén qué llegó.
     *
     * @param clave la que devolvió {@link #autorizarSubida}
     */
    @Transactional
    public List<EstadoDeLogo> confirmarSubida(String columna, String clave) {
        var logo = LogoDeEmpresa.porColumna(columna);
        exigirClaveDeEstaEmpresa(logo, clave);

        var objeto = almacen.describir(clave)
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "subida_no_completada",
                        "No se encontró el archivo. Vuelve a intentar la subida."));

        // Segunda validación, ahora contra lo que el almacén dice que tiene. La
        // primera fue sobre lo que el cliente prometía.
        logo.exigirValido(objeto.tipoContenido(), objeto.bytes());

        var identidad = identidades.buscar()
                .orElseGet(() -> new IdentidadVisual(UUID.randomUUID(), empresaActiva()));

        var anterior = identidad.colocar(logo, clave);
        identidades.guardar(identidad);

        auditoria.registrar("identidad_visual", identidad.id(), "COLOCAR_LOGO",
                new Instantanea(logo.columna(), anterior.orElse(null)),
                new Instantanea(logo.columna(), clave));

        // El objeto anterior NO se borra: los documentos emitidos lo referencian.
        return consultar();
    }

    /**
     * Retira el logo y <strong>sí</strong> borra el archivo.
     *
     * <p>A diferencia de reemplazar, esto significa «no quiero logo». Dejar el
     * objeto huérfano en el almacén sería acumular archivos que nadie referencia
     * y que nadie va a revisar.
     */
    @Transactional
    public List<EstadoDeLogo> quitar(String columna) {
        var logo = LogoDeEmpresa.porColumna(columna);

        // Sin fila, no hay nada que quitar y eso no es un error: pedir que se
        // retire algo que ya no está es exactamente lo que se quería. Responder
        // 404 obligaría a la pantalla a distinguir dos casos que para el usuario
        // son el mismo.
        var identidad = identidades.buscar().orElse(null);
        if (identidad == null) {
            return consultar();
        }

        var quitada = identidad.quitar(logo);
        if (quitada.isEmpty()) {
            return consultar(); // Idempotente.
        }

        identidades.guardar(identidad);
        almacen.eliminar(quitada.get());

        auditoria.registrar("identidad_visual", identidad.id(), "QUITAR_LOGO",
                new Instantanea(logo.columna(), quitada.get()),
                new Instantanea(logo.columna(), null));

        return consultar();
    }

    /**
     * La clave viene del cliente, así que hay que comprobar que sea de esta
     * empresa y de este hueco.
     *
     * <p>Sin esto, alguien podría confirmar la clave de <em>otra</em> empresa
     * —las claves son adivinables solo si conoces el UUID, pero «difícil de
     * adivinar» no es un control de acceso— y apuntar su fila al logo ajeno.
     */
    private void exigirClaveDeEstaEmpresa(LogoDeEmpresa logo, String clave) {
        String prefijo = "empresas/%s/%s/".formatted(empresaActiva(), logo.columna());
        if (clave == null || !clave.startsWith(prefijo)) {
            throw new ReglaDeNegocioViolada(
                    "clave_invalida",
                    "Esa referencia de archivo no corresponde a esta empresa.");
        }
    }

    private UUID empresaActiva() {
        return contexto.obligatorio().empresaActivaObligatoria();
    }

    private record Instantanea(String logo, String clave) {
    }
}
