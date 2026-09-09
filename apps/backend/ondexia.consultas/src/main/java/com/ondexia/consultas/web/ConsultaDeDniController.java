package com.ondexia.consultas.web;

import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.consultas.ConsultaDeDni;
import com.ondexia.domain.consultas.DatosDeDni;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de DNI a RENIEC, para no teclear el nombre del adquirente.
 *
 * <p>Sin atestación, a diferencia del RUC: SUNAT no valida el nombre de una
 * boleta, solo el número, y el número lo teclea quien vende. Lo que sí se
 * comparte con el RUC es la cuota por identidad: es la misma función y el mismo
 * proveedor de pago, y la puerta de abuso es la misma.
 */
@RestController
@RequestMapping("/consultas/dni")
@Validated
public class ConsultaDeDniController {

    private final ConsultaDeDni reniec;
    private final CuotaPorSolicitante cuota;

    public ConsultaDeDniController(ConsultaDeDni reniec, CuotaPorSolicitante cuota) {
        this.reniec = reniec;
        this.cuota = cuota;
    }

    public record RespuestaDni(String dni, String nombres, String apellidoPaterno,
            String apellidoMaterno, String nombreCompleto, String consultadoEn) {

        static RespuestaDni de(DatosDeDni datos) {
            return new RespuestaDni(datos.dni(), datos.nombres(), datos.apellidoPaterno(),
                    datos.apellidoMaterno(), datos.nombreCompleto(), datos.consultadoEn().toString());
        }
    }

    @GetMapping("/{dni}")
    public RespuestaDni consultar(
            @PathVariable @Pattern(regexp = "\\d{8}", message = "El DNI son ocho dígitos.")
            String dni,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String autorizacion) {

        String solicitante = Solicitante.de(autorizacion);
        cuota.registrar(solicitante);

        return reniec.consultar(dni)
                .map(RespuestaDni::de)
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "dni_no_encontrado", "RENIEC no tiene registrado el DNI " + dni + "."));
    }
}
