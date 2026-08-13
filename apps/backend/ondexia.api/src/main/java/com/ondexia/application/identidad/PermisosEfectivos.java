package com.ondexia.application.identidad;

import com.ondexia.domain.comun.ContextoOperacion;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.identidad.PermisoRepositorio;
import com.ondexia.domain.identidad.Permisos;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Los permisos del usuario en la empresa que tiene activa.
 *
 * <p>Vive en la capa de aplicación y no en seguridad por una razón concreta:
 * <strong>lo necesitan los dos lados</strong>. El evaluador de Spring Security
 * lo usa para autorizar cada endpoint, y {@link ConsultarContexto} para decirle
 * al frontend qué menús pintar. Si estuviera en infraestructura, la aplicación
 * dependería de infraestructura y la flecha se invertiría.
 *
 * <p><strong>Deniega por defecto.</strong> Sin contexto, sin empresa activa o
 * sin rol, devuelve un conjunto vacío.
 */
@Service
public class PermisosEfectivos {

    private final PermisoRepositorio permisos;
    private final ProveedorDeContexto contexto;

    /**
     * Caché por rol. La clave es el rol y el valor lleva la versión de permisos
     * de la cuenta: al leer se compara, y si quedó atrás se recarga. Así la
     * entrada se <strong>reemplaza</strong> en vez de acumularse —queda acotada
     * por el número de roles, no por el de ediciones— y revocar un permiso surte
     * efecto en la petición siguiente, sin coordinación entre contenedores.
     */
    private final Map<UUID, EntradaCache> cache = new ConcurrentHashMap<>();

    private record EntradaCache(long permisosVersion, Permisos permisos) {
    }

    public PermisosEfectivos(PermisoRepositorio permisos, ProveedorDeContexto contexto) {
        this.permisos = permisos;
        this.contexto = contexto;
    }

    public Permisos actuales() {
        return contexto.actual()
                .filter(ContextoOperacion::tieneEmpresaActiva)
                .map(this::cargar)
                .orElseGet(Permisos::ninguno);
    }

    public boolean esAdministradorDeCuenta() {
        return contexto.actual()
                .map(ContextoOperacion::esAdministradorCuenta)
                .orElse(false);
    }

    private Permisos cargar(ContextoOperacion actual) {
        UUID rolId = actual.rolId();
        if (rolId == null) {
            return Permisos.ninguno();
        }

        EntradaCache entrada = cache.get(rolId);
        if (entrada != null && entrada.permisosVersion() == actual.permisosVersion()) {
            return entrada.permisos();
        }

        // Varios hilos pueden recargar el mismo rol a la vez. Es aceptable: la
        // consulta es idempotente y el resultado idéntico, así que un bloqueo
        // costaría más que la consulta repetida.
        Permisos cargados = permisos.permisosDelRol(rolId);
        cache.put(rolId, new EntradaCache(actual.permisosVersion(), cargados));
        return cargados;
    }

    /** Solo para pruebas: en producción la invalidación por versión ya lo resuelve. */
    public void vaciarCache() {
        cache.clear();
    }
}
