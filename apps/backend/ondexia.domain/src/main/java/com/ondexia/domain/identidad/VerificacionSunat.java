package com.ondexia.domain.identidad;

import com.ondexia.domain.consultas.CondicionDomicilio;
import com.ondexia.domain.consultas.DatosDeRuc;
import com.ondexia.domain.consultas.EstadoContribuyente;
import java.time.Instant;
import java.util.Objects;

/**
 * Lo que SUNAT dijo de esta empresa, y cuándo.
 *
 * <h2>Por qué la fecha va dentro y no al lado</h2>
 *
 * <p>Porque sin ella los otros dos campos mienten. {@code ACTIVO} no es una
 * propiedad del contribuyente: es el resultado de una consulta con fecha, y un
 * RUC activo hoy puede estar de baja en tres meses. Un objeto que afirma
 * «ACTIVO» sin decir cuándo invita a tratarlo como verdad permanente — y esa
 * afirmación decide si la empresa puede emitir.
 *
 * <p>Juntos en un objeto, no hay forma de guardar uno sin el otro. La base lo
 * refuerza con {@code empresa_verificacion_completa} (V12), que es la misma
 * regla escrita dos veces a propósito: aquí para que el código no pueda, y allí
 * para que un script de soporte tampoco.
 *
 * <h2>Por qué puede no existir</h2>
 *
 * <p>Las empresas dadas de alta antes de que existiera la consulta del padrón no
 * la tienen, y no se les puede inventar. {@code null} significa «nunca se
 * comprobó», que no es lo mismo que «está mal» — y la interfaz debe poder
 * distinguirlo para ofrecer comprobarlo en vez de acusar.
 */
public record VerificacionSunat(
        EstadoContribuyente estado,
        CondicionDomicilio condicion,
        String distrito,
        String provincia,
        String departamento,
        boolean esAgenteRetencion,
        boolean esBuenContribuyente,
        String tipoSocietario,
        Instant verificadoEn) {

    public VerificacionSunat {
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(condicion, "condicion");
        Objects.requireNonNull(verificadoEn, "verificadoEn");
    }

    /** Lo que se guarda a partir de una consulta ya comprobada. */
    public static VerificacionSunat de(DatosDeRuc datos) {
        return new VerificacionSunat(
                datos.estado(),
                datos.condicion(),
                datos.distrito(),
                datos.provincia(),
                datos.departamento(),
                datos.esAgenteRetencion(),
                datos.esBuenContribuyente(),
                datos.tipoSocietario(),
                datos.consultadoEn());
    }

    /**
     * Si en el momento de la consulta el RUC podía emitir.
     *
     * <p>Ojo con el tiempo verbal: dice qué pasaba entonces, no qué pasa ahora.
     * Para lo segundo hay que volver a consultar, y {@link #verificadoEn()} es
     * lo que permite decidir si merece la pena.
     */
    public boolean estabaApta() {
        return estado.permiteEmitir() && condicion.esHabido();
    }
}
