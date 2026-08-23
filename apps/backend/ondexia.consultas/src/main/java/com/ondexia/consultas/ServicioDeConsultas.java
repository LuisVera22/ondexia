package com.ondexia.consultas;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.consultas.Atestacion;
import com.ondexia.domain.consultas.ConsultaDeRuc;
import com.ondexia.domain.consultas.DatosDeRuc;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import software.amazon.awssdk.services.ssm.SsmClient;

/**
 * Consulta el RUC y firma el resultado.
 *
 * <h2>Por qué está separado del manejador de Lambda</h2>
 *
 * <p>Para poder ejecutarlo sin Lambda. Es lo que permite probar contra el
 * proveedor real desde un portátil ({@link Consola}) y escribir pruebas que no
 * fabriquen un evento de la pasarela para comprobar una decisión de negocio.
 *
 * <h2>La caducidad</h2>
 *
 * <p>Diez minutos. Es el tiempo que hay entre consultar el RUC y enviar el
 * formulario, con margen para quien se distrae. Más corto obligaría a repetir la
 * consulta a gente normal; mucho más largo permitiría guardar una atestación de
 * cuando la empresa estaba habida y usarla cuando ya no lo está.
 */
public final class ServicioDeConsultas {

    /** Ver la cabecera: ni tan corto que estorbe ni tan largo que envejezca. */
    static final Duration VALIDEZ = Duration.ofMinutes(10);

    private static final Duration ESPERA_POR_PROVEEDOR = Duration.ofSeconds(6);

    private final ConsultaDeRuc padron;
    private final byte[] secretoDeFirma;
    private final Clock reloj;

    ServicioDeConsultas(ConsultaDeRuc padron, byte[] secretoDeFirma, Clock reloj) {
        this.padron = padron;
        this.secretoDeFirma = secretoDeFirma;
        this.reloj = reloj;
    }

    /** Lo que se devuelve: los datos para el formulario y la prueba firmada. */
    public record Resultado(DatosDeRuc datos, String atestacion) {
    }

    /**
     * @return vacío si el padrón no conoce el RUC
     */
    public Optional<Resultado> consultar(Ruc ruc) {
        return padron.consultar(ruc).map(datos -> {
            Instant expira = reloj.instant().plus(VALIDEZ);
            return new Resultado(datos, Atestacion.emitir(datos, expira, secretoDeFirma));
        });
    }

    /**
     * Monta el servicio leyendo la configuración del entorno.
     *
     * <p>Se construye una vez por contenedor de Lambda, fuera del manejador: el
     * cliente de SSM y los de HTTP tardan en crearse, y hacerlo por invocación
     * paga ese coste en cada registro en vez de en el primero.
     */
    public static ServicioDeConsultas desdeElEntorno(Function<String, String> entorno) {
        Claves claves = new Claves(ssmSiSeNecesita(entorno), entorno);

        String secreto = claves.resolver("CONSULTAS_FIRMA_PARAMETRO", "CONSULTAS_FIRMA_SECRETO");
        if (secreto == null) {
            // Se para aqui en vez de firmar con una clave vacia. Firmando, las
            // dos partes «funcionarian» y la firma no protegeria de nada, porque
            // cualquiera podria reproducirla — el peor estado posible, porque
            // parece que hay verificacion.
            throw new IllegalStateException(
                    "Falta el secreto de firma: define CONSULTAS_FIRMA_PARAMETRO "
                            + "(nombre del parámetro en SSM) o CONSULTAS_FIRMA_SECRETO.");
        }

        String tokenDecolecta =
                claves.resolver("CONSULTAS_DECOLECTA_PARAMETRO", "CONSULTAS_DECOLECTA_TOKEN");
        String tokenApiPeru =
                claves.resolver("CONSULTAS_APIPERU_PARAMETRO", "CONSULTAS_APIPERU_TOKEN");

        List<ConsultaDeRuc> cascada = new ArrayList<>();
        List<String> nombres = new ArrayList<>();

        if (tokenDecolecta != null) {
            cascada.add(new ProveedorDecolecta(
                    new ClienteDelPadron("decolecta", tokenDecolecta, ESPERA_POR_PROVEEDOR),
                    url(entorno, "CONSULTAS_DECOLECTA_URL", "https://api.decolecta.com/v1")));
            nombres.add("decolecta");
        }
        if (tokenApiPeru != null) {
            cascada.add(new ProveedorApiPeru(
                    new ClienteDelPadron("apiperu", tokenApiPeru, ESPERA_POR_PROVEEDOR),
                    url(entorno, "CONSULTAS_APIPERU_URL", "https://api.apiperu.dev")));
            nombres.add("apiperu");
        }

        if (cascada.isEmpty()) {
            throw new IllegalStateException(
                    "Ningún proveedor de consulta configurado. Define al menos "
                            + "CONSULTAS_DECOLECTA_PARAMETRO o CONSULTAS_DECOLECTA_TOKEN.");
        }

        // Se deja constancia de quien quedo activo. Sin esta linea, un entorno con
        // el nombre de un parametro mal escrito se comporta igual que uno con un
        // solo proveedor y no hay forma de distinguirlo mirando.
        System.out.println("[consultas] cascada: " + String.join(" -> ", nombres));

        return new ServicioDeConsultas(new CascadaDeProveedores(cascada),
                secreto.getBytes(StandardCharsets.UTF_8), Clock.systemUTC());
    }

    /**
     * El cliente de SSM solo se crea si algún parámetro lo necesita.
     *
     * <p>Crearlo siempre buscaría credenciales de AWS al arrancar, y en un
     * portátil sin ellas la ejecución fallaría antes de intentar nada — por una
     * dependencia que en ese entorno no se usa.
     */
    private static SsmClient ssmSiSeNecesita(Function<String, String> entorno) {
        boolean alguno = tiene(entorno, "CONSULTAS_FIRMA_PARAMETRO")
                || tiene(entorno, "CONSULTAS_DECOLECTA_PARAMETRO")
                || tiene(entorno, "CONSULTAS_APIPERU_PARAMETRO");
        return alguno ? SsmClient.create() : null;
    }

    private static boolean tiene(Function<String, String> entorno, String variable) {
        String valor = entorno.apply(variable);
        return valor != null && !valor.isBlank();
    }

    private static String url(Function<String, String> entorno, String variable, String defecto) {
        String valor = entorno.apply(variable);
        return valor == null || valor.isBlank() ? defecto : valor.trim();
    }
}
