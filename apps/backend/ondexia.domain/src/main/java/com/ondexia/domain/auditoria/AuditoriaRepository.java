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

    /**
     * Anotado a propósito, y no por costumbre.
     *
     * <p><strong>Los métodos de consulta derivados de Spring Data no son
     * transaccionales.</strong> Solo lo son los heredados de
     * {@code SimpleJpaRepository} —{@code findAll}, {@code findById},
     * {@code count}—, que traen su propio {@code @Transactional}. Un método
     * declarado aquí, sin anotar, se ejecuta fuera de toda transacción.
     *
     * <p>Sobre una tabla con Row Level Security eso no da error: el gestor de
     * transacciones nunca llega a fijar el inquilino, {@code empresa_actual()}
     * devuelve NULL y la política no deja pasar ninguna fila.
     * <strong>Devuelve vacío.</strong> Es el fallo cerrado funcionando, pero
     * desde fuera parece que el dato no existe.
     *
     * <p>En la práctica los servicios ya abren transacción, así que la ruta
     * normal está cubierta; esto protege a quien llame al repositorio directo.
     * Toda consulta nueva sobre una tabla con RLS debe llevarlo.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    List<Auditoria> findByEmpresaIdAndEntidadAndEntidadIdOrderByCreadoEnDesc(
            UUID empresaId, String entidad, UUID entidadId);
}
