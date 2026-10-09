package com.ondexia.infrastructure.salida.persistencia.identidad;

import com.ondexia.domain.identidad.AsignacionEmpresa;
import com.ondexia.domain.identidad.MiembroEmpresa;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioEmpresaJpaRepository extends JpaRepository<UsuarioEmpresaJpa, UUID> {

    /**
     * Todas las empresas de un usuario, en una consulta.
     *
     * <p>El {@code left join} sobre sucursal es obligatorio: {@code sucursal_id}
     * nulo significa «todas», y un {@code join} corriente eliminaría justo a los
     * usuarios con acceso completo, que son los más.
     *
     * <p>Se filtra por {@code activo} aquí y no en el servicio: una empresa
     * desactivada no debe aparecer ni en el selector, y dejar el filtro fuera
     * significa traer filas para descartarlas.
     */
    @Query("""
            select new com.ondexia.domain.identidad.AsignacionEmpresa(
                e.id, e.ruc, e.razonSocial, e.nombreComercial, e.modoSunat,
                r.id, r.codigo, r.nombre,
                s.id, s.nombre)
            from UsuarioEmpresaJpa ue
                join EmpresaJpa e on e.id = ue.empresaId
                join RolJpa r on r.id = ue.rolId
                left join SucursalJpa s on s.id = ue.sucursalId
            where ue.usuarioId = :usuarioId
              and e.activo = true
            order by e.razonSocial
            """)
    List<AsignacionEmpresa> findAsignacionesByUsuarioId(UUID usuarioId);

    Optional<UsuarioEmpresaJpa> findByUsuarioIdAndEmpresaId(UUID usuarioId, UUID empresaId);

    List<UsuarioEmpresaJpa> findByEmpresaId(UUID empresaId);

    /**
     * La misma unión que arriba, mirada desde la empresa: quién entra aquí.
     *
     * <p>El {@code left join} sobre sucursal vuelve a ser obligatorio por lo
     * mismo — {@code sucursal_id} nulo significa «todos los establecimientos», y
     * un join corriente eliminaría precisamente a quienes tienen acceso completo.
     *
     * <p>No se filtra por {@code u.activo}: esta pantalla existe para
     * administrarlos, y un usuario desactivado es justo el que hay que poder ver
     * para reactivarlo.
     */
    @Query("""
            select new com.ondexia.domain.identidad.MiembroEmpresa(
                ue.id, u.id, u.email, u.nombre, u.apellido, u.activo, u.cognitoSub,
                r.id, r.codigo, r.nombre,
                s.id, s.nombre)
            from UsuarioEmpresaJpa ue
                join UsuarioJpa u on u.id = ue.usuarioId
                join RolJpa r on r.id = ue.rolId
                left join SucursalJpa s on s.id = ue.sucursalId
            where ue.empresaId = :empresaId
            order by coalesce(u.apellido, u.nombre), u.nombre
            """)
    List<MiembroEmpresa> findMiembrosByEmpresaId(UUID empresaId);

    /** Si algún usuario tiene este rol asignado. Impide borrar un rol en uso. */
    boolean existsByRolId(UUID rolId);
}
