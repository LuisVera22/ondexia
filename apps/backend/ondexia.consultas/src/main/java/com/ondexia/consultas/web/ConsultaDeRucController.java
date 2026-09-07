package com.ondexia.consultas.web;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.consultas.Atestacion;
import com.ondexia.domain.consultas.ConsultaDeRuc;
import com.ondexia.domain.consultas.DatosDeRuc;
import jakarta.validation.constraints.Pattern;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * La única ruta de este desplegable.
 *
 * <h2>Qué NO hace, y es lo importante</h2>
 *
 * <p>No comprueba quién llama. Lo hace el autorizador JWT de Cognito de la
 * pasarela, que ya existe para el resto de la API: esta función cuelga de una
 * ruta de la <strong>misma</strong> HTTP API, así que llega invocada solo si el
 * token era válido. Repetir la validación aquí sería una segunda implementación
 * de la autenticación, y dos implementaciones acaban teniendo dos
 * comportamientos.
 *
 * <p>Tampoco decide si la empresa puede registrarse. Devuelve lo que SUNAT dice,
 * firmado; quien decide es la API al validar la atestación.
 *
 * <h2>La caducidad de la firma</h2>
 *
 * <p>Diez minutos: el tiempo entre consultar el RUC y enviar el formulario, con
 * margen para quien se distrae. Más corto obligaría a repetir la consulta a gente
 * normal; mucho más largo permitiría guardar una atestación de cuando la empresa
 * estaba habida y usarla cuando ya no lo está.
 */
@RestController
@RequestMapping("/consultas/ruc")
@Validated
public class ConsultaDeRucController {

    /** Ver la cabecera: ni tan corto que estorbe ni tan largo que envejezca. */
    static final Duration VALIDEZ = Duration.ofMinutes(10);

    private final ConsultaDeRuc padron;
    private final PrivateKey clavePrivada;
    private final CuotaPorSolicitante cuota;

    public ConsultaDeRucController(ConsultaDeRuc padron, PrivateKey clavePrivada,
            CuotaPorSolicitante cuota) {
        this.padron = padron;
        this.clavePrivada = clavePrivada;
        this.cuota = cuota;
    }

    /**
     * @param ruc once dígitos. El dígito verificador lo comprueba el value object
     *     {@code Ruc}, y se responde <strong>sin salir a la red</strong>: la
     *     errata de tecleo es el error más frecuente y no hay razón para gastar
     *     una consulta de un plan de pago en ella
     */
    @GetMapping("/{ruc}")
    public RespuestaConsulta consultar(
            @PathVariable @Pattern(regexp = "\\d{11}", message = "El RUC son once dígitos.")
            String ruc,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String autorizacion) {

        // Antes de gastar una consulta del proveedor: sin solicitante no hay
        // para quien emitir la atestacion (M17).
        String solicitante = Solicitante.de(autorizacion);

        // Y antes tambien de gastarla: la cuota por identidad es lo que impide
        // usar esta funcion como proxy hacia el proveedor de pago (doc 12 §6.2).
        cuota.registrar(solicitante);

        DatosDeRuc datos = padron.consultar(new Ruc(ruc))
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "ruc_no_encontrado",
                        "SUNAT no tiene registrado el RUC " + ruc + "."));

        String atestacion = Atestacion.emitir(
                datos, Instant.now().plus(VALIDEZ), clavePrivada, solicitante);

        return RespuestaConsulta.de(datos, atestacion);
    }
}
