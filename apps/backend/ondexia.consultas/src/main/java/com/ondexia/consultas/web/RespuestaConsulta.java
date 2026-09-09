package com.ondexia.consultas.web;

import com.ondexia.domain.consultas.DatosDeRuc;

/**
 * Lo que se devuelve: los datos para el formulario y la prueba firmada.
 *
 * <h2>Se escribe campo a campo y no se refleja el record del dominio</h2>
 *
 * <p>Reflejándolo, el JSON público cambiaría de forma cada vez que alguien añada
 * o renombre un campo en {@code DatosDeRuc}, y el frontend se rompería sin que el
 * cambio pareciera tener nada que ver con él. Aquí la frontera es explícita.
 *
 * <p>Los nombres son los del resto de la API —camelCase— y no los
 * {@code snake_case} de los proveedores.
 *
 * <h2>`datos` no es lo que la API cree</h2>
 *
 * <p>Viaja para que el formulario se rellene sin decodificar nada, y no se usa
 * para decidir: cualquiera puede cambiarlo antes de reenviarlo. Lo único que la
 * API cree es {@code atestacion}.
 */
public record RespuestaConsulta(Datos datos, String atestacion) {

    public record Datos(
            String ruc,
            String razonSocial,
            String estado,
            String condicion,
            String domicilioFiscal,
            String ubigeo,
            String distrito,
            String provincia,
            String departamento,
            boolean esAgenteRetencion,
            boolean esBuenContribuyente,
            String tipoSocietario,
            String consultadoEn,

            /**
             * Si puede darse de alta, calculado aquí.
             *
             * <p>Se envía en lugar de dejar que el frontend repita la regla
             * —«ACTIVO y HABIDO»—: dos implementaciones de la puerta del registro
             * acabarían discrepando, y la que manda es la del servidor.
             */
            boolean aptaParaRegistro,

            /** Por qué no, en el idioma de quien lo lee. Nulo si es apta. */
            String motivoDeRechazo) {
    }

    static RespuestaConsulta de(DatosDeRuc datos, String atestacion) {
        return new RespuestaConsulta(
                new Datos(
                        datos.ruc().valor(),
                        datos.razonSocial(),
                        datos.estado().name(),
                        datos.condicion().name(),
                        datos.domicilioFiscal(),
                        datos.ubigeo() == null ? null : datos.ubigeo().valor(),
                        datos.distrito(),
                        datos.provincia(),
                        datos.departamento(),
                        datos.esAgenteRetencion(),
                        datos.esBuenContribuyente(),
                        datos.tipoSocietario(),
                        // En ISO, para que el cliente lo formatee en su zona. El
                        // estado es una foto y sin esta fecha invita a tratarlo
                        // como verdad permanente.
                        datos.consultadoEn().toString(),
                        datos.aptaParaRegistro(),
                        datos.motivoDeRechazo()),
                atestacion);
    }
}
