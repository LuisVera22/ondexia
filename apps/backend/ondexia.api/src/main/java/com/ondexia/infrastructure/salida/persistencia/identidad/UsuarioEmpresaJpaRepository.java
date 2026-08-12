package com.ondexia.infrastructure.salida.persistencia.identidad;

import com.ondexia.domain.identidad.AsignacionEmpresa;
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
}
