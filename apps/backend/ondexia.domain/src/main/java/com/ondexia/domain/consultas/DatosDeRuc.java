package com.ondexia.domain.consultas;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.comun.Ubigeo;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.time.Instant;
import java.util.Objects;

/**
 * Lo que SUNAT dice de un RUC, en el instante en que se preguntó.
 *
 * <h2>Por qué lleva la fecha dentro</h2>
 *
 * <p>Porque sin ella el resto miente. {@code ACTIVO} no es una propiedad del
 * contribuyente, es el resultado de una consulta con fecha: un RUC activo hoy
 * puede estar de baja en tres meses. Un objeto que afirma «ACTIVO» sin decir
 * cuándo invita a tratarlo como verdad permanente, y esa afirmación se usa para
 * decidir si una empresa puede emitir.
 *
 * <h2>Por qué el ubigeo puede faltar</h2>
 *
 * <p>Porque el respaldo devuelve menos que el principal. Decolecta trae ubigeo y
 * distrito; apiperu.dev solo la dirección y el departamento. El campo es
 * opcional para que ese hueco sea visible en el tipo en vez de aparecer como un
 * {@code null} inesperado en el sitio donde se construye el comprobante — y para
 * que quien orquesta la cascada sepa que le toca completarlo desde el padrón.
 *
 * <p>Lo que <strong>no</strong> es opcional son los cuatro datos con los que se
 * decide: RUC, razón social, estado y condición. Sin ellos no hay respuesta que
 * valga, y un {@code DatosDeRuc} a medias no debe poder existir.
 *
 * @param ubigeo puede ser {@code null}: ver arriba
 * @param tipoSocietario forma societaria; solo la trae el endpoint extendido
 */
public record DatosDeRuc(
        Ruc ruc,
        String razonSocial,
        EstadoContribuyente estado,
        CondicionDomicilio condicion,
        String domicilioFiscal,
        Ubigeo ubigeo,
        String distrito,
        String provincia,
        String departamento,
        boolean esAgenteRetencion,
        boolean esBuenContribuyente,
        String tipoSocietario,
        Instant consultadoEn) {

    public DatosDeRuc {
        Objects.requireNonNull(ruc, "ruc");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(condicion, "condicion");
        Objects.requireNonNull(consultadoEn, "consultadoEn");

        if (razonSocial == null || razonSocial.isBlank()) {
            throw new ReglaDeNegocioViolada(
                    "consulta_sin_razon_social",
                    "La consulta del RUC " + ruc + " no devolvió razón social.");
        }
        razonSocial = razonSocial.trim();
        domicilioFiscal = domicilioFiscal == null ? null : domicilioFiscal.trim();
    }

    /**
     * Si este RUC puede darse de alta como empresa emisora.
     *
     * <p>Las dos condiciones a la vez. Están juntas aquí y no repartidas por los
     * casos de uso porque son <strong>la</strong> regla del registro: escrita en
     * un solo sitio, el día que cambie cambia una vez.
     */
    public boolean aptaParaRegistro() {
        return estado.permiteEmitir() && condicion.esHabido();
    }

    /**
     * Motivo del rechazo, en el idioma de quien lo lee.
     *
     * <p>Devuelve {@code null} si es apta. No lanza: quien pregunta por qué no
     * pasó ya sabe que no pasó, y una excepción aquí obligaría a envolver la
     * consulta del motivo en un try.
     *
     * <p>El orden importa: si el RUC está de baja, decir además que no es habido
     * es ruido. Se nombra el impedimento que hay que resolver primero.
     */
    public String motivoDeRechazo() {
        if (!estado.permiteEmitir()) {
            return switch (estado) {
                case BAJA_DEFINITIVA -> "El RUC tiene baja definitiva en SUNAT.";
                case BAJA_PROVISIONAL, BAJA_PROVISIONAL_OFICIO ->
                        "El RUC está de baja provisional en SUNAT.";
                case SUSPENSION_TEMPORAL -> "El RUC está en suspensión temporal.";
                case INSCRIPCION_OFICIO ->
                        "El RUC fue inscrito de oficio y no está activo.";
                case ACTIVO -> null;
            };
        }
        if (!condicion.esHabido()) {
            return switch (condicion) {
                case NO_HABIDO -> "El domicilio fiscal está como NO HABIDO en SUNAT.";
                case NO_HALLADO -> "El domicilio fiscal está como NO HALLADO en SUNAT.";
                case POR_VERIFICAR ->
                        "SUNAT todavía no ha verificado el domicilio fiscal.";
                case HABIDO -> null;
            };
        }
        return null;
    }

    /** Si hay que completar la geografía desde otra fuente. */
    public boolean faltaUbigeo() {
        return ubigeo == null;
    }
}
