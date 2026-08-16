package com.ondexia.application.configuracion;

import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.AccesoDenegado;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.identidad.AsignacionEmpresa;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.identidad.EmpresaRepositorio;
import com.ondexia.domain.identidad.UsuarioEmpresaRepositorio;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Las empresas que el usuario alcanza, y el único sitio que traduce un id
 * recibido por HTTP en una empresa.
 *
 * <h2>Por qué existe</h2>
 *
 * <p>{@link ConsultarEmpresa} no recibe identificador: la empresa sale del
 * contexto, así que no hay forma de pedir otra y no hay nada que olvidar. Esa
 * propiedad se pierde en cuanto una pantalla necesita listar las empresas y
 * abrir cualquiera de ellas, porque entonces el id sí viaja en la URL.
 *
 * <p>La respuesta no es repetir la comprobación en cada caso de uso —que es
 * exactamente el escenario que aquel diseño quería evitar—, sino que exista un
 * único camino: quien quiera una empresa por id pasa por
 * {@link #exigirAcceso(UUID)}. Un caso de uso nuevo que llame directamente a
 * {@code EmpresaRepositorio.buscarPorId} salta la comprobación, y por eso ese
 * repositorio no debe usarse con un id que venga de fuera.
 *
 * <h2>El alcance es la asignación, no la cuenta</h2>
 *
 * <p>Se filtra por las asignaciones del usuario y no por
 * {@code listarDeCuenta} a secas. Dos usuarios de la misma cuenta pueden
 * alcanzar empresas distintas —es lo que decide el selector de contexto—, y
 * listar todas las de la cuenta enseñaría aquí lo que aquel selector oculta.
 *
 * <p>Ninguna de las dos consultas depende del aislamiento por filas: {@code
 * empresa} no tiene columna {@code empresa_id} y por tanto no lleva política
 * (ver {@code V1__identidad_auditoria_y_aislamiento.sql}). La comprobación de
 * esta clase es la única que hay.
 */
@Service
public class EmpresasDelUsuario {

    private final EmpresaRepositorio empresas;
    private final UsuarioEmpresaRepositorio asignaciones;
    private final ProveedorDeContexto contexto;

    public EmpresasDelUsuario(EmpresaRepositorio empresas, UsuarioEmpresaRepositorio asignaciones,
            ProveedorDeContexto contexto) {
        this.empresas = empresas;
        this.asignaciones = asignaciones;
        this.contexto = contexto;
    }

    /**
     * Empresas del usuario con sus datos fiscales completos.
     *
     * <p>Devuelve {@link Empresa} y no {@link AsignacionEmpresa} porque el
     * listado muestra el domicilio y si la empresa está activa, y la proyección
     * de asignaciones no los trae: se resolvió para el selector de contexto,
     * que solo necesita nombre y RUC.
     */
    public List<Empresa> listar() {
        var alcanzadas = idsAlcanzados();

        return empresas.listarDeCuenta(contexto.obligatorio().cuentaId()).stream()
                .filter(empresa -> alcanzadas.contains(empresa.id()))
                .toList();
    }

    /**
     * La empresa, si el usuario llega a ella.
     *
     * <p>Distingue los dos fallos a propósito. Un id inexistente es un 404 y un
     * id ajeno es un 403, pero ninguno de los dos mensajes dice nada de la otra
     * cuenta: enterarse de que «esa empresa existe pero no es tuya» convertiría
     * el endpoint en un buscador de qué RUC están dados de alta en Ondexia.
     */
    public Empresa exigirAcceso(UUID empresaId) {
        if (!idsAlcanzados().contains(empresaId)) {
            throw new AccesoDenegado("No tienes acceso a la empresa solicitada.");
        }

        return empresas.buscarPorId(empresaId)
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "empresa_no_encontrada", "La empresa solicitada ya no existe."));
    }

    private Set<UUID> idsAlcanzados() {
        return asignaciones.listarAsignacionesDe(contexto.obligatorio().usuarioId()).stream()
                .map(AsignacionEmpresa::empresaId)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }
}
