package com.ondexia.application.panel;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondexia.application.identidad.PermisosEfectivos;
import com.ondexia.application.ventas.SesionesDeCaja;
import com.ondexia.domain.comprobante.ComprobanteElectronico;
import com.ondexia.domain.comprobante.ComprobanteElectronicoRepositorio;
import com.ondexia.domain.comprobante.EstadoSunat;
import com.ondexia.domain.comprobante.TipoDocumento;
import com.ondexia.domain.identidad.Permisos;
import com.ondexia.domain.ventas.SesionCaja;
import com.ondexia.domain.ventas.VentasDelDia;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que el panel enseña según lo que el usuario alcanza.
 *
 * <p>La distinción que estas pruebas fijan es <strong>ausente contra cero</strong>.
 * Un bloque en {@code null} significa «no te corresponde verlo» y el frontend no
 * lo pinta; un bloque en cero significa «hoy no has vendido nada» y sí se pinta.
 * Colapsarlos —devolver ceros a quien no tiene permiso— haría que la portada le
 * dijera a un usuario de Configuración que la empresa no vende.
 *
 * <p>Va aparte de {@code PanelIT} porque comprobarlo allí exigiría quitarle el
 * rol al usuario de ejemplo, y esa suite comparte una sola base.
 */
class PanelDelDiaTest {

    private static final Instant MEDIANOCHE_UTC = Instant.parse("2026-09-09T03:30:00Z");

    /** Ese instante en Lima —UTC-5— todavía es el día 8. */
    private static final LocalDate DIA_EN_LIMA = LocalDate.of(2026, 9, 8);

    private static final UUID CAJA = UUID.randomUUID();
    private static final UUID EMPRESA = UUID.randomUUID();

    /**
     * {@link PermisosEfectivos} es una clase concreta; se le fija la respuesta.
     *
     * <p>Los códigos van completos —módulo, submódulo y función— porque
     * {@link Permisos#puede} es conjuntivo sobre los tres niveles: tener la
     * función suelta no basta, y escribirla sola aquí probaría otra cosa.
     */
    private static PermisosEfectivos permisosDe(String... codigos) {
        return new PermisosEfectivos(null, null, null) {
            @Override
            public Permisos actuales() {
                return new Permisos(Set.of(codigos));
            }
        };
    }

    private static SesionesDeCaja cajasAbiertas(SesionCaja... sesiones) {
        return new SesionesDeCaja(null, null, null, null, null, null) {
            @Override
            public List<SesionCaja> abiertas() {
                return List.of(sesiones);
            }
        };
    }

    private static final VentasDelDia VENTAS = fecha -> {
        assertThat(fecha).isEqualTo(DIA_EN_LIMA);
        return new VentasDelDia.Totales(3, new BigDecimal("435.00"));
    };

    private static final ComprobanteElectronicoRepositorio COMPROBANTES =
            new ComprobanteElectronicoRepositorio() {
                @Override
                public Optional<ComprobanteElectronico> buscarPorId(UUID id) {
                    return Optional.empty();
                }

                @Override
                public Optional<ComprobanteElectronico> buscarPorDocumento(UUID documentoId) {
                    return Optional.empty();
                }

                @Override
                public Map<UUID, EstadoSunat> estadosDe(Collection<UUID> documentoIds) {
                    return Map.of();
                }

                @Override
                public List<ComprobanteElectronico> listarPorAtender() {
                    return List.of(rechazado("B001", 12), rechazado("B001", 13));
                }

                @Override
                public ComprobanteElectronico guardar(ComprobanteElectronico comprobante) {
                    return comprobante;
                }
            };

    private static ComprobanteElectronico rechazado(String serie, long numero) {
        return ComprobanteElectronico.reconstruir(UUID.randomUUID(), EMPRESA, UUID.randomUUID(),
                TipoDocumento.BOLETA, serie, numero, EstadoSunat.RECHAZADO, 1,
                MEDIANOCHE_UTC, MEDIANOCHE_UTC, "2335", "El documento ya fue informado",
                List.of(), null, null, null);
    }

    private PanelDelDia panelCon(PermisosEfectivos permisos, SesionesDeCaja sesiones) {
        return new PanelDelDia(sesiones, VENTAS, COMPROBANTES, permisos,
                Clock.fixed(MEDIANOCHE_UTC, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Con los tres permisos vienen los tres bloques, y la fecha es la de Lima")
    void losTresBloques() {
        var sesion = SesionCaja.abrir(UUID.randomUUID(), CAJA, UUID.randomUUID(),
                new BigDecimal("100.00"), MEDIANOCHE_UTC);

        var resumen = panelCon(
                permisosDe("ventas:acceder",
                        "ventas.caja:acceder", "ventas.caja:consultar",
                        "ventas.nota_venta:acceder", "ventas.nota_venta:consultar",
                        "ventas.comprobante:acceder", "ventas.comprobante:consultar"),
                cajasAbiertas(sesion)).consultar();

        // Las 22:30 del 8 en Lima son las 03:30 del 9 en UTC: el servidor corre
        // en UTC y la portada tiene que decir el 8, que es el día del negocio.
        assertThat(resumen.fecha()).isEqualTo(DIA_EN_LIMA);
        assertThat(resumen.cajasAbiertas()).containsExactly(sesion);
        assertThat(resumen.ventas().documentos()).isEqualTo(3);
        assertThat(resumen.ventas().importe()).isEqualByComparingTo("435.00");
        assertThat(resumen.cuantosPorAtender()).isEqualTo(2);
        assertThat(resumen.primeros()).hasSize(2);
    }

    @Test
    @DisplayName("Sin ningun permiso de ventas los bloques son nulos, no ceros")
    void sinPermisosLosBloquesSonNulos() {
        var resumen = panelCon(permisosDe("configuracion:acceder",
                "configuracion.empresa:acceder", "configuracion.empresa:consultar"),
                cajasAbiertas()).consultar();

        assertThat(resumen.fecha()).isEqualTo(DIA_EN_LIMA);
        assertThat(resumen.cajasAbiertas()).isNull();
        assertThat(resumen.ventas()).isNull();
        assertThat(resumen.cuantosPorAtender()).isNull();
        assertThat(resumen.primeros()).isNull();
    }

    @Test
    @DisplayName("Cada bloque va por su cuenta: quien solo consulta cajas ve solo cajas")
    void cadaBloqueConSuPermiso() {
        var resumen = panelCon(
                permisosDe("ventas:acceder", "ventas.caja:acceder", "ventas.caja:consultar"),
                cajasAbiertas()).consultar();

        assertThat(resumen.cajasAbiertas()).isEmpty();
        assertThat(resumen.ventas()).isNull();
        assertThat(resumen.cuantosPorAtender()).isNull();
    }
}
