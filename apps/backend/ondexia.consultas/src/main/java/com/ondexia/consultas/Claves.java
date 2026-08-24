package com.ondexia.consultas;

import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.SsmException;

/**
 * De dónde salen las claves.
 *
 * <h2>SSM y no Secrets Manager</h2>
 *
 * <p>Un parámetro {@code SecureString} es gratis; un secreto de Secrets Manager
 * cuesta 0,40 USD al mes, y aquí hacen falta tres — un 8 % del presupuesto de
 * infraestructura del proyecto entero. Secrets Manager se gana su precio con la
 * rotación automática y con secretos por cliente: es lo correcto para los
 * certificados digitales, y ahí se usa. Estas claves son nuestras, son tres, y no
 * rotan solas.
 *
 * <h2>Y no una variable de entorno de Lambda</h2>
 *
 * <p>Aunque DT-19 dijera «variable cifrada». Ese cifrado es en reposo y Lambda la
 * descifra antes de ejecutar el código: cualquiera con
 * {@code lambda:GetFunctionConfiguration} la ve en texto plano en la consola. Un
 * parámetro tiene IAM delante.
 *
 * <h2>La caché</h2>
 *
 * <p>Dos parámetros distintos son dos llamadas, pero el mismo parámetro pedido
 * dos veces no. Importa poco con tres claves resueltas al arrancar; importaría si
 * algún día algo las pide por petición.
 */
public class Claves {

    private final SsmClient ssm;
    private final Map<String, String> cache = new HashMap<>();

    public Claves(SsmClient ssm) {
        this.ssm = ssm;
    }

    /**
     * Resuelve una clave.
     *
     * <p>Manda el nombre del parámetro si está; si no, el valor directo. En ese
     * orden: en un entorno desplegado, una variable de entorno olvidada no debe
     * poder ganarle al parámetro.
     *
     * @param nombreParametro nombre en SSM, o {@code null}
     * @param valorDirecto el valor, para desarrollo, o {@code null}
     * @return el valor, o {@code null} si no hay ninguno de los dos
     */
    public String resolver(String nombreParametro, String valorDirecto) {
        if (nombreParametro == null) {
            return valorDirecto;
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
            /*
             * Sin el valor no se puede consultar ni firmar, asi que no hay una
             * degradacion elegante posible. Lo que si se puede es decir cual
             * parametro falta: el error de AWS por si solo no lo nombra, y con
             * tres parametros eso son tres sitios donde mirar.
             */
            throw new IllegalStateException(
                    "No se pudo leer el parámetro " + nombre + " de SSM. Revisa que exista "
                            + "y que el rol tenga ssm:GetParameter y kms:Decrypt.", falloDeSsm);
        }
    }
}
