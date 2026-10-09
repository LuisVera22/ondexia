package com.ondexia.infrastructure.salida.persistencia.identidad;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CuentaAdministradorJpaRepository
        extends JpaRepository<CuentaAdministradorJpa, UUID> {

    boolean existsByCuentaIdAndUsuarioId(UUID cuentaId, UUID usuarioId);

    List<CuentaAdministradorJpa> findByCuentaId(UUID cuentaId);

    long countByCuentaId(UUID cuentaId);

    /**
     * Toma el candado de fila de la cuenta (hallazgo M6).
     *
     * <p>Dos administradores desactivandose el uno al otro a la vez leian ambos
     * «hay dos» y los dos pasaban: la cuenta quedaba sin nadie. Con la fila de
     * `cuenta` bloqueada, el segundo espera a que el primero confirme y ya
     * cuenta uno.
     */
    @Query(value = "select id from cuenta where id = :cuentaId for update", nativeQuery = true)
    UUID bloquearCuenta(UUID cuentaId);

    /**
     * Administradores que ademas estan ACTIVOS. `countByCuentaId` cuenta filas
     * de `cuenta_administrador`, y desactivar a alguien no borra su fila: con
     * ella, un administrador desactivado seguia contando como salida.
     */
    @Query(value = """
            select count(*)
              from cuenta_administrador ca
              join usuario u on u.id = ca.usuario_id
             where ca.cuenta_id = :cuentaId
               and u.activo
            """, nativeQuery = true)
    long contarActivosEnCuenta(UUID cuentaId);
}
