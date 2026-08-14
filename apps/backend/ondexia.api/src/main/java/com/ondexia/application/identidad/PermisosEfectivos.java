package com.ondexia.application.identidad;

import com.ondexia.domain.comun.ContextoOperacion;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.identidad.ModulosContratadosRepositorio;
import com.ondexia.domain.identidad.PermisoRepositorio;
import com.ondexia.domain.identidad.Permisos;
import java.util.Map;
import java.util.Set;
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
    private final ModulosContratadosRepositorio modulos;

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

    /**
     * Los módulos contratados, cacheados <strong>por cuenta</strong> y no por rol.
     *
     * <h2>Aquí hay una fuga que evitar y no es evidente</h2>
     *
     * <p>Lo natural sería recortar los permisos antes de guardarlos en la caché de
     * arriba y quedarse con un solo mapa. <strong>Sería una fuga entre
     * inquilinos.</strong> Esa
     * caché se indexa por rol, y los roles del sistema —{@code ADMINISTRADOR},
     * {@code CONTADOR}, {@code VENDEDOR}— tienen {@code cuenta_id} nulo: son
     * compartidos por todas las cuentas. La primera cuenta en pedirlos dejaría su
     * recorte cacheado y la siguiente lo heredaría, con los módulos de otra.
     *
     * <p>Por eso son dos mapas: el de roles guarda los permisos <em>en bruto</em>,
     * como hasta ahora, y el recorte se aplica en cada llamada con la máscara de la
     * cuenta que está pidiendo. Intersecar dos conjuntos de decenas de cadenas es
     * irrelevante al lado de una consulta.
     *
     * <p>Se invalida con la misma versión: {@code cuenta.permisos_version} se
     * incrementa también al cambiar el plan o un módulo, no solo al editar un rol.
     */
    private final Map<UUID, EntradaModulos> modulosPorCuenta = new ConcurrentHashMap<>();

    private record EntradaModulos(long permisosVersion, Set<String> contratados) {
    }

    public PermisosEfectivos(PermisoRepositorio permisos, ProveedorDeContexto contexto,
            ModulosContratadosRepositorio modulos) {
        this.permisos = permisos;
        this.contexto = contexto;
        this.modulos = modulos;
    }

    public Permisos actuales() {
        return contexto.actual()
                .filter(ContextoOperacion::tieneEmpresaActiva)
                .map(actual -> limitarAContratado(actual, cargar(actual)))
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

    /**
     * Recorta a lo contratado por la cuenta.
     *
     * <p>Va después de la caché de roles y no dentro, por el motivo del comentario
     * de {@link #modulosPorCuenta}: el rol puede ser compartido entre cuentas y la
     * máscara nunca lo es.
     */
    private Permisos limitarAContratado(ContextoOperacion actual, Permisos delRol) {
        if (delRol.vacio()) {
            return delRol;
        }
        return delRol.limitadoA(contratadosDe(actual));
    }

    private Set<String> contratadosDe(ContextoOperacion actual) {
        UUID cuentaId = actual.cuentaId();

        EntradaModulos entrada = modulosPorCuenta.get(cuentaId);
        if (entrada != null && entrada.permisosVersion() == actual.permisosVersion()) {
            return entrada.contratados();
        }

        Set<String> contratados = modulos.contratadosDe(cuentaId);
        modulosPorCuenta.put(cuentaId, new EntradaModulos(actual.permisosVersion(), contratados));
        return contratados;
    }

    /** Solo para pruebas: en producción la invalidación por versión ya lo resuelve. */
    public void vaciarCache() {
        cache.clear();
        modulosPorCuenta.clear();
    }
}
