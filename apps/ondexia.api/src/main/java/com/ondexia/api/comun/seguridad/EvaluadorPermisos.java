package com.ondexia.api.comun.seguridad;

import com.ondexia.domain.identidad.Permiso;
import com.ondexia.domain.identidad.PermisoRepository;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Responde la unica pregunta de autorizacion del sistema: ¿puede este usuario
 * ejecutar esta accion sobre este modulo, en la empresa que tiene activa?
 *
 * <p>Se registra con el nombre {@code permisos} para poder escribirlo corto en
 * las anotaciones:
 *
 * <pre>{@code
 * @PreAuthorize("@permisos.puede('almacen.producto', 'registrar')")
 * }</pre>
 *
 * <p><strong>Denegar por defecto.</strong> Cualquier camino que no llegue a
 * encontrar el permiso devuelve {@code false}: sin contexto, sin empresa
 * activa, sin rol, rol vacio. Un fallo de configuracion debe cerrar la puerta,
 * no abrirla.
 *
 * <p>Que el menu del frontend oculte lo que no corresponde es comodidad, no
 * seguridad: la API se puede llamar sin pasar por el SPA.
 */
@Component("permisos")
public class EvaluadorPermisos {

    private final PermisoRepository permisos;

    /**
     * Cache de los codigos de permiso por rol.
     *
     * <p>Sin el, cada peticion consultaria la union rol-permiso, que para un rol
     * amplio son ~200 filas. Con el, lo normal es cero consultas.
     *
     * <p>La clave es el rol y el valor incluye la version de permisos de la
     * cuenta. Se compara al leer: si la version guardada quedo atras, la entrada
     * se recarga. Asi <strong>la entrada se reemplaza en vez de acumularse</strong>
     * —el cache queda acotado por el numero de roles, no por el numero de
     * ediciones— y revocar un permiso surte efecto en la peticion siguiente sin
     * ninguna coordinacion entre contenedores de Lambda.
     *
     * <p>Es un {@code ConcurrentHashMap} y no un cache con expiracion porque
     * aqui no hay nada que expirar: la invalidacion es exacta, no por tiempo.
     */
    private final Map<UUID, EntradaCache> cache = new ConcurrentHashMap<>();

    private record EntradaCache(long permisosVersion, Set<String> codigos) {
    }

    public EvaluadorPermisos(PermisoRepository permisos) {
        this.permisos = permisos;
    }

    /**
     * @param modulo por ejemplo {@code almacen.producto}
     * @param accion por ejemplo {@code registrar}, {@code aprobar},
     *               {@code anular}. Son permisos distintos a proposito: quien
     *               registra una venta casi nunca debe poder anularla
     */
    @Transactional(readOnly = true)
    public boolean puede(String modulo, String accion) {
        return ContextoActual.obtener()
                .filter(ContextoPeticion::tieneEmpresaActiva)
                .map(contexto -> codigosDe(contexto)
                        .contains(Permiso.componerCodigo(modulo, accion)))
                .orElse(false);
    }

    /**
     * Acciones sin empresa: gestion de la suscripcion, alta de empresas,
     * asignacion de usuarios.
     *
     * <p>No pasan por la matriz de permisos porque la matriz se evalua sobre el
     * par (usuario, empresa) y estas acciones existen antes de que haya
     * ninguna empresa. Ver {@code CuentaAdministrador}.
     */
    public boolean esAdministradorDeCuenta() {
        return ContextoActual.obtener()
                .map(ContextoPeticion::esAdministradorCuenta)
                .orElse(false);
    }

    /** Todos los codigos del rol activo. Lo consume {@code /contexto}. */
    @Transactional(readOnly = true)
    public Set<String> codigosDelContexto() {
        return ContextoActual.obtener()
                .filter(ContextoPeticion::tieneEmpresaActiva)
                .map(this::codigosDe)
                .orElseGet(Set::of);
    }

    private Set<String> codigosDe(ContextoPeticion contexto) {
        UUID rolId = contexto.rolId();
        if (rolId == null) {
            return Set.of();
        }

        EntradaCache entrada = cache.get(rolId);
        if (entrada != null && entrada.permisosVersion() == contexto.permisosVersion()) {
            return entrada.codigos();
        }

        // Puede haber varios hilos recargando el mismo rol a la vez. Es
        // aceptable: la consulta es idempotente y el resultado identico, asi que
        // el coste de un bloqueo seria mayor que el de la consulta repetida.
        Set<String> codigos = Set.copyOf(permisos.findCodigosByRolId(rolId));
        cache.put(rolId, new EntradaCache(contexto.permisosVersion(), codigos));
        return codigos;
    }

    /**
     * Vacia el cache. Solo para las pruebas.
     *
     * <p>En produccion no hay que llamarlo: la invalidacion por version ya lo
     * resuelve, y vaciarlo a mano en un contenedor no afecta a los demas.
     */
    void vaciarCache() {
        cache.clear();
    }
}
