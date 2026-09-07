package com.ondexia.domain.comun;

import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import java.util.Arrays;
import java.util.List;

/**
 * Lo que los dos primeros dígitos del RUC dicen de quién lo tiene.
 *
 * <h2>Por qué una tabla y no una condición</h2>
 *
 * <p>La versión anterior era {@code Ruc.esPersonaJuridica()}: {@code true} si
 * empezaba por {@code 20}, {@code false} para todo lo demás. Eso trataba igual a
 * una persona natural con negocio ({@code 10}) que a una sucesión indivisa
 * ({@code 15}) o a una entidad identificada con otro documento ({@code 17}), y
 * ninguna de las dos últimas es hoy un cliente de Ondexia. El plan del primer
 * producto (doc 12 §3.1) fija quién se registra: {@code 10} y {@code 20}. Los
 * demás prefijos existen, se reconocen, y se rechazan en el alta con un mensaje
 * que dice por qué; el día que aparezca un caso real, es una fila más aquí.
 *
 * <p>Los prefijos vigentes salen de la práctica del padrón: {@code 10} persona
 * natural, {@code 15} y {@code 17} contribuyentes identificados con otro
 * documento —sucesiones indivisas, sociedades conyugales, extranjeros con carné,
 * entidades— y {@code 20} persona jurídica. El {@code 25} que citaba el encargo
 * no existe (decisión 4, doc 12 §10.1). Un prefijo fuera de esta lista es un
 * número que SUNAT no emite, y se rechaza como tal.
 *
 * <p>Lo verifica {@code TipoDeContribuyenteTest}.
 */
public enum TipoDeContribuyente {

    PERSONA_NATURAL(List.of("10"), "persona natural con negocio", true, true),

    OTRO_DOCUMENTO_DE_IDENTIDAD(List.of("15", "17"),
            "contribuyente identificado con otro documento (sucesión indivisa, sociedad "
                    + "conyugal, entidad o extranjero sin DNI)", false, false),

    PERSONA_JURIDICA(List.of("20"), "persona jurídica", true, false);

    private final List<String> prefijos;
    private final String descripcion;
    private final boolean puedeRegistrarse;
    private final boolean puedeEstarEnNuevoRus;

    TipoDeContribuyente(List<String> prefijos, String descripcion, boolean puedeRegistrarse,
            boolean puedeEstarEnNuevoRus) {
        this.prefijos = prefijos;
        this.descripcion = descripcion;
        this.puedeRegistrarse = puedeRegistrarse;
        this.puedeEstarEnNuevoRus = puedeEstarEnNuevoRus;
    }

    public static TipoDeContribuyente de(Ruc ruc) {
        String prefijo = ruc.valor().substring(0, 2);
        return Arrays.stream(values())
                .filter(tipo -> tipo.prefijos.contains(prefijo))
                .findFirst()
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "ruc_prefijo_desconocido",
                        "El RUC " + ruc + " empieza por " + prefijo
                                + ", que no corresponde a ningún tipo de contribuyente "
                                + "que SUNAT emita."));
    }

    public String descripcion() {
        return descripcion;
    }

    /** Si Ondexia admite darle de alta una cuenta o una empresa. */
    public boolean puedeRegistrarse() {
        return puedeRegistrarse;
    }

    /**
     * El Nuevo RUS es solo para personas naturales y sucesiones indivisas; una
     * persona jurídica no puede estar en él, así que declararlo sería un error de
     * quien rellena el formulario.
     */
    public boolean puedeEstarEnNuevoRus() {
        return puedeEstarEnNuevoRus;
    }
}
