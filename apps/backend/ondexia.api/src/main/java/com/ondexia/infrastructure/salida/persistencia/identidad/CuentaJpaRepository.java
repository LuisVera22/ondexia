package com.ondexia.infrastructure.salida.persistencia.identidad;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CuentaJpaRepository extends JpaRepository<CuentaJpa, UUID> {

    /**
     * UPDATE atómico, no leer-modificar-guardar: dos administradores tocando
     * roles a la vez leerían el mismo valor y escribirían el mismo incremento,
     * con lo que una invalidación se perdería y ese contenedor seguiría
     * sirviendo permisos revocados.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CuentaJpa c set c.permisosVersion = c.permisosVersion + 1 where c.id = :cuentaId")
    void incrementarPermisosVersion(UUID cuentaId);
}
