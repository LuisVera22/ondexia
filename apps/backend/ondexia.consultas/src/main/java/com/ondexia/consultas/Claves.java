package com.ondexia.consultas;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.SsmException;

/**
 * De dónde salen las claves de los proveedores y el secreto de firma.
 *
 * <h2>SSM y no Secrets Manager</h2>
 *
 * <p>Un parámetro {@code SecureString} es gratis; un secreto de Secrets Manager
 * cuesta 0,40 USD al mes, y aquí hacen falta tres — un 8 % del presupuesto de
 * infraestructura del proyecto entero.
 *
 * <p>Secrets Manager se gana su precio con la rotación automática y con secretos
 * por cliente; es lo correcto para los certificados digitales, y ahí se usa.
 * Estas claves son nuestras, son tres, y no rotan solas.
 *
 * <h2>Y no una variable de entorno de Lambda</h2>
 *
 * <p>Aunque DT-19 dijera «variable cifrada». Ese cifrado es en reposo y Lambda
 * la descifra sola: cualquiera con {@code lambda:GetFunctionConfiguration} la ve
 * en texto plano en la consola. No es equivalente a un parámetro cifrado con
 * IAM delante.
 *
 * <h2>Por qué hay caché</h2>
 *
 * <p>Porque leer SSM en cada invocación es latencia que paga quien está
 * esperando en el formulario, y cuenta contra el límite de 40 peticiones por
 * segundo del nivel estándar. Un contenedor tibio no vuelve a preguntar.
 *
 * <h2>Por qué sigue existiendo el camino de la variable de entorno</h2>
 *
 * <p>Para desarrollo. Sin él haría falta credenciales de AWS y un parámetro
 * creado para ejecutar la consulta en un portátil, y la alternativa habitual a
 * eso es que alguien pegue la clave en el código «solo un momento».
 */
final class Claves {

    private final SsmClient ssm;
    private final Function<String, String> entorno;
    private final Map<String, String> cache = new HashMap<>();

    Claves(SsmClient ssm, Function<String, String> entorno) {
        this.ssm = ssm;
        this.entorno = entorno;
    }

    /**
     * Resuelve una clave.
     *
     * <p>Manda el parámetro de SSM si su nombre está configurado; si no, la
     * variable de entorno. Ese orden importa: en un entorno desplegado, una
     * variable de entorno olvidada no debe poder ganarle al parámetro.
     *
     * @param variableNombreParametro variable que contiene el <em>nombre</em> del
     *     parámetro, no su valor
     * @param variableValorDirecto variable que contiene el valor, para desarrollo
     * @return el valor, o {@code null} si no hay ninguno de los dos
     */
    String resolver(String variableNombreParametro, String variableValorDirecto) {
        String nombreParametro = limpio(entorno.apply(variableNombreParametro));
        if (nombreParametro == null) {
            return limpio(entorno.apply(variableValorDirecto));
        }
        return cache.computeIfAbsent(nombreParametro, this::leerDeSsm);
    }

    private String leerDeSsm(String nombre) {
        if (ssm == null) {
            throw new IllegalStateException(
                    "Hay un parámetro configurado (" + nombre + ") pero no cliente de SSM.");
        }
        try {
            return ssm.getParameter(GetParameterRequest.builder()
                            .name(nombre)
                            .withDecryption(true)
                            .build())
                    .parameter()
                    .value();
        } catch (SsmException falloDeSsm) {
            // Sin el valor no se puede consultar ni firmar, asi que no hay una
            // degradacion elegante posible. Lo que si se puede es decir cual
            // parametro falta: el error de AWS por si solo no lo nombra, y con
            // tres parametros eso son tres sitios donde mirar.
            throw new IllegalStateException(
                    "No se pudo leer el parámetro " + nombre + " de SSM. "
                            + "Revisa que exista y que el rol tenga ssm:GetParameter "
                            + "y kms:Decrypt.", falloDeSsm);
        }
    }

    /**
     * Una variable declarada y sin valor llega como cadena vacía, no como nulo.
     * Sin esto, {@code CONSULTAS_DECOLECTA_TOKEN=} contaría como configurada y el
     * proveedor se llamaría con un Bearer vacío — un 401 con aspecto de problema
     * ajeno.
     */
    private static String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
