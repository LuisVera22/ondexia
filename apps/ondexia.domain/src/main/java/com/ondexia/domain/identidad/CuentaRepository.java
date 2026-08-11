package com.ondexia.domain.identidad;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CuentaRepository extends JpaRepository<Cuenta, UUID> {

    /**
     * Incrementa la version de permisos sin cargar la cuenta.
     *
     * <p>Se hace con un UPDATE atomico en la base y no leyendo-modificando-
     * guardando la entidad: dos administradores tocando roles a la vez leerian
     * el mismo valor y escribirian el mismo incremento, con lo que una de las
     * dos invalidaciones se perderia y ese contenedor seguiria sirviendo
     * permisos revocados.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Cuenta c set c.permisosVersion = c.permisosVersion + 1 where c.id = :cuentaId")
    void incrementarPermisosVersion(UUID cuentaId);

    @Query("select c.permisosVersion from Cuenta c where c.id = :cuentaId")
    Long findPermisosVersionById(UUID cuentaId);
}
