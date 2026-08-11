package com.ondexia.domain.auditoria;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Puerto de la bitacora.
 *
 * <p>Hereda {@code delete} y {@code deleteAll} de {@code JpaRepository} y no
 * hay forma limpia de quitarlos de la interfaz. La barrera real esta en la base
 * de datos: la migracion V1 instala un disparador que rechaza todo UPDATE y
 * DELETE sobre la tabla, asi que llamar a {@code delete} aqui produce un error,
 * no un borrado silencioso.
 *
 * <p>Se hace con disparador y no revocando privilegios porque la aplicacion se
 * conecta como propietario de las tablas, y un propietario puede volver a
 * concederse lo que se le revoco. Es la unica defensa que sobrevive a un
 * descuido.
 */
public interface AuditoriaRepository extends JpaRepository<Auditoria, UUID> {

    List<Auditoria> findByEmpresaIdAndEntidadAndEntidadIdOrderByCreadoEnDesc(
            UUID empresaId, String entidad, UUID entidadId);
}
