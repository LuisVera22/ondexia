package com.ondexia.infrastructure.salida.persistencia.identidad;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data. Los nombres van en inglés porque el framework <em>analiza</em>
 * el nombre para construir la consulta — y aquí, escondidos en el adaptador, es
 * donde eso no molesta a nadie.
 */
public interface UsuarioJpaRepository extends JpaRepository<UsuarioJpa, UUID> {

    Optional<UsuarioJpa> findByCognitoSub(String cognitoSub);

    Optional<UsuarioJpa> findByCuentaIdAndEmailIgnoreCase(UUID cuentaId, String email);
}
