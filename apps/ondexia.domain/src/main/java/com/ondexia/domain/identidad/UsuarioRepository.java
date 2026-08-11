package com.ondexia.domain.identidad;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Puerto de persistencia de usuarios.
 *
 * <p>Los nombres de metodo van en ingles porque Spring Data <em>los analiza</em>
 * para construir la consulta: {@code findByCognitoSub} genera el
 * {@code WHERE cognito_sub = ?}, y {@code buscarPorCognitoSub} no genera nada —
 * falla al arrancar el contexto. Es la excepcion a la regla de nomenclatura, y
 * esta anotada en {@code com.ondexia.domain.package-info}.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    /**
     * Punto de entrada de cada peticion autenticada: del {@code sub} del token
     * al usuario de nuestra base.
     */
    Optional<Usuario> findByCognitoSub(String cognitoSub);

    Optional<Usuario> findByCuentaIdAndEmailIgnoreCase(UUID cuentaId, String email);

    boolean existsByCuentaIdAndEmailIgnoreCase(UUID cuentaId, String email);
}
