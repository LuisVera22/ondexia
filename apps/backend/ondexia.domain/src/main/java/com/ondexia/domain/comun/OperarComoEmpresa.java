package com.ondexia.domain.comun;

/**
 * Ejecuta una acción como si el contexto de operación fuera otro, dentro de la
 * misma transacción.
 *
 * <h2>Para qué existe</h2>
 *
 * <p>Dar de alta una empresa tiene que dejarla lista para operar: con su
 * establecimiento matriz, su almacén principal y su primera caja (doc 12 §8,
 * iteración 1 y 2). El establecimiento vive fuera de Row Level Security y se
 * crea sin más; el almacén y la caja no: sus tablas exigen que la fila lleve la
 * empresa del contexto, y en el alta o no hay contexto —el registro de una
 * cuenta nueva— o el contexto es el de OTRA empresa —el alta de una segunda
 * empresa desde la primera—.
 *
 * <p>La alternativa era sacar almacén y caja de RLS, o crearlos «la primera vez
 * que hagan falta». Lo primero abre un agujero por comodidad; lo segundo deja al
 * cliente en un sistema que no vende sin decirle por qué. Esto hace lo honesto:
 * declara que, durante esta acción y solo durante ella, se opera como esa
 * empresa, con el usuario que la está creando.
 *
 * <p>La implementación cambia el contexto que ven los casos de uso y la variable
 * de transacción que leen las políticas de la base, y restaura ambos al salir,
 * pase lo que pase. Quién puede llamarlo lo decide el caso de uso que lo llama:
 * aquí no hay comprobación de permisos porque no hay a quién preguntarle todavía.
 */
public interface OperarComoEmpresa {

    void ejecutar(ContextoOperacion contexto, Runnable accion);
}
