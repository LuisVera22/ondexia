package com.ondexia.application.panel;

import com.ondexia.application.identidad.PermisosEfectivos;
import com.ondexia.application.ventas.SesionesDeCaja;
import com.ondexia.domain.comprobante.ComprobanteElectronico;
import com.ondexia.domain.comprobante.ComprobanteElectronicoRepositorio;
import com.ondexia.domain.ventas.SesionCaja;
import com.ondexia.domain.ventas.VentasDelDia;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que el panel enseña: tres cifras y una lista.
 *
 * <h2>Por qué tan poco</h2>
 *
 * <p>El panel anterior era una maqueta con cifras inventadas: siete
 * indicadores, un gráfico de barras, una banda de avisos y un saludo por la
 * hora del día. Ninguna cifra salía de la base. Un panel que miente es peor que
 * no tener panel, porque quien lo mira toma decisiones con él.
 *
 * <p>Estas tres responden a lo que alguien pregunta al abrir la aplicación por
 * la mañana en una tienda (doc 12 §7.2): si su caja está abierta, cuánto lleva
 * vendido, y si hay algo atascado ante SUNAT. El gráfico vuelve cuando haya
 * meses que comparar.
 *
 * <h2>Cada bloque se pide con su permiso</h2>
 *
 * <p>El panel es la primera pantalla de todo el mundo, así que no puede exigir
 * un permiso para entrar: quien solo tenga Configuración se toparía con un 403
 * en la portada. Lo que hace es preguntar por cada bloque y devolver
 * {@code null} donde el usuario no llega. Un bloque ausente y un bloque en cero
 * son cosas distintas, y el frontend los pinta distinto.
 */
@Service
@Transactional(readOnly = true)
public class PanelDelDia {

    /**
     * La zona del negocio. Repetida aquí, como en los demás casos de uso, y no
     * sacada de la máquina: el servidor corre en UTC y «hoy» es el día de Lima.
     */
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    /**
     * Cuántos comprobantes atascados se enumeran.
     *
     * <p>La cifra dice cuántos hay; la lista es para actuar sobre ellos, y
     * actuar sobre cien desde una portada no se hace. Quien tenga más va al
     * listado de comprobantes, que pagina.
     */
    private static final int MAXIMO_EN_LA_LISTA = 10;

    private final SesionesDeCaja sesiones;
    private final VentasDelDia ventas;
    private final ComprobanteElectronicoRepositorio comprobantes;
    private final PermisosEfectivos permisos;
    private final Clock reloj;

    public PanelDelDia(SesionesDeCaja sesiones, VentasDelDia ventas,
            ComprobanteElectronicoRepositorio comprobantes, PermisosEfectivos permisos,
            Clock reloj) {
        this.sesiones = sesiones;
        this.ventas = ventas;
        this.comprobantes = comprobantes;
        this.permisos = permisos;
        this.reloj = reloj;
    }

    public Resumen consultar() {
        LocalDate hoy = LocalDate.ofInstant(reloj.instant(), LIMA);
        var actuales = permisos.actuales();

        List<SesionCaja> cajas = actuales.puede("ventas.caja", "consultar")
                ? sesiones.abiertas()
                : null;

        VentasDelDia.Totales delDia = actuales.puede("ventas.nota_venta", "consultar")
                ? ventas.de(hoy)
                : null;

        List<ComprobanteElectronico> atascados = actuales.puede("ventas.comprobante", "consultar")
                ? comprobantes.listarPorAtender()
                : null;

        return new Resumen(hoy, cajas, delDia,
                atascados == null ? null : atascados.size(),
                atascados == null
                        ? null
                        : atascados.stream().limit(MAXIMO_EN_LA_LISTA).toList());
    }

    /**
     * @param cajasAbiertas null si el usuario no ve cajas
     * @param ventas null si el usuario no ve ventas
     * @param cuantosPorAtender el total, null si el usuario no ve comprobantes
     * @param primeros los que caben en la portada; pueden ser menos que el total
     */
    public record Resumen(
            LocalDate fecha,
            List<SesionCaja> cajasAbiertas,
            VentasDelDia.Totales ventas,
            Integer cuantosPorAtender,
            List<ComprobanteElectronico> primeros) {
    }
}
