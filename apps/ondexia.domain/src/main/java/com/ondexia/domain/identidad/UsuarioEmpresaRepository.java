package com.ondexia.domain.identidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioEmpresaRepository extends JpaRepository<UsuarioEmpresa, UUID> {

    /**
     * Todas las empresas que alcanza un usuario, resueltas en una sola consulta.
     *
     * <p>Se filtra por {@code e.activo} aqui y no en el servicio: una empresa
     * desactivada no debe aparecer siquiera en el selector, y dejar el filtro
     * fuera de la consulta significa traer filas para descartarlas — y olvidar
     * el filtro en la siguiente consulta que alguien escriba.
     *
     * <p>El {@code left join} sobre sucursal es obligatorio: {@code sucursal_id}
     * nulo significa «todas las sucursales», y un {@code join} corriente
     * eliminaria justo a los usuarios con acceso completo, que son los mas.
     */
    @Query("""
            select new com.ondexia.domain.identidad.AsignacionEmpresa(
                e.id, e.ruc, e.razonSocial, e.nombreComercial, e.modoSunat,
                r.id, r.codigo, r.nombre,
                s.id, s.nombre)
            from UsuarioEmpresa ue
                join Empresa e on e.id = ue.empresaId
                join Rol r on r.id = ue.rolId
                left join Sucursal s on s.id = ue.sucursalId
            where ue.usuarioId = :usuarioId
              and e.activo = true
            order by e.razonSocial
            """)
    List<AsignacionEmpresa> findAsignacionesByUsuarioId(UUID usuarioId);

    Optional<UsuarioEmpresa> findByUsuarioIdAndEmpresaId(UUID usuarioId, UUID empresaId);

    List<UsuarioEmpresa> findByEmpresaId(UUID empresaId);
}
