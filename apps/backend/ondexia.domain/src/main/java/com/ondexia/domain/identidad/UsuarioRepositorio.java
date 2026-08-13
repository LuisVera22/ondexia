package com.ondexia.domain.identidad;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: cómo el dominio guarda y recupera usuarios.
 *
 * <h2>Por qué el puerto vive aquí y no en la capa de aplicación</h2>
 *
 * Porque expresa <strong>lo que el dominio necesita para existir</strong>. Un
 * usuario no se puede razonar sin la idea de que se guarda y se recupera.
 * Ponerlo en {@code application} obligaría al dominio a depender de
 * {@code application} para nombrar su propio puerto — o a no tener puerto, que
 * lleva al dominio anémico donde toda la lógica migra a los servicios.
 *
 * <h2>Nombres en español, y por qué importa</h2>
 *
 * {@code buscarPorCognitoSub} y no {@code findByCognitoSub}. Esta interfaz es
 * del dominio y habla su idioma. Los nombres que Spring Data <em>analiza</em>
 * para construir la consulta quedan escondidos en el adaptador, que es su sitio.
 *
 * <p>Como efecto colateral desaparece una trampa: los métodos derivados de
 * Spring Data no son transaccionales, y sobre una tabla con RLS eso devuelve
 * vacío sin dar error. En un adaptador que es una clase normal,
 * {@code @Transactional} está a la vista.
 */
public interface UsuarioRepositorio {

    Optional<Usuario> buscarPorId(UUID id);

    /** Punto de entrada de cada petición autenticada: del token a nuestro usuario. */
    Optional<Usuario> buscarPorCognitoSub(String cognitoSub);

    Optional<Usuario> buscarPorEmailEnCuenta(UUID cuentaId, String email);

    Usuario guardar(Usuario usuario);
}
