/**
 * El dominio de Ondexia: reglas de negocio, sin tecnología.
 *
 * <h2>Este paquete no depende de nada</h2>
 *
 * Ni JPA, ni Spring, ni Bean Validation. No es disciplina: el módulo Maven
 * {@code ondexia-domain} no declara esas dependencias, así que una entidad con
 * {@code @Entity} aquí <strong>no compila</strong>.
 *
 * <p>Las reglas se comprueban en el constructor, no con anotaciones que solo
 * actúan si alguien invoca al validador. {@code new Ruc("2010000000")} lanza:
 * no existe un RUC inválido en memoria, y por eso ningún método aguas abajo
 * necesita volver a comprobarlo.
 *
 * <h2>Por qué es un módulo aparte</h2>
 *
 * Porque {@code ondexia-facturacion} también lo necesita: el Emisor lee el
 * mismo {@code Comprobante} que escribe el núcleo comercial (DT-13). La
 * frontera de módulo existe donde hay una necesidad real de compartir, no
 * porque la arquitectura hexagonal se dibuje con varias cajas.
 *
 * <h2>Qué hay dentro</h2>
 *
 * <pre>
 *   comun/      value objects (Ruc, Ubigeo), contexto de operación, errores
 *   identidad/  cuenta, empresa, usuario, roles y permisos
 *   auditoria/  la bitácora
 *   almacen/    (pendiente)
 *   ventas/     (pendiente)
 * </pre>
 *
 * <p>Cada dominio contiene sus agregados <strong>y sus puertos de salida</strong>
 * —los {@code *Repositorio}—, porque el puerto expresa lo que el dominio
 * necesita para existir. Ponerlos en la capa de aplicación obligaría al dominio
 * a depender de ella para nombrar su propio puerto.
 *
 * <h2>Los errores no llevan código HTTP</h2>
 *
 * Deliberado. {@code ondexia-facturacion} consume de una cola, no atiende
 * peticiones web: si las excepciones trajeran un estado HTTP dentro, ese módulo
 * heredaría un concepto que no significa nada en su contexto. Cada adaptador
 * decide cómo se representa el error en su medio.
 *
 * <p>Ver {@code ondexia.docs/08-arquitectura-backend.md}.
 */
package com.ondexia.domain;
