package com.ondexia.infrastructure.salida.persistencia.marca;

import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.marca.IdentidadVisual;
import com.ondexia.domain.marca.IdentidadVisualRepositorio;
import com.ondexia.domain.marca.LogoDeEmpresa;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de la identidad visual.
 *
 * <p>{@code @Transactional} en cada método por lo mismo de siempre: sin
 * transacción no hay variable de sesión y la política de RLS, que falla cerrado,
 * devuelve cero filas sin lanzar nada. Aquí el síntoma sería «esta empresa no
 * tiene logos» sobre una que sí los tiene.
 */
@Repository
@Transactional(readOnly = true)
public class IdentidadVisualAdaptador implements IdentidadVisualRepositorio {

    private final IdentidadVisualJpaRepository filas;
    private final ProveedorDeContexto contexto;

    public IdentidadVisualAdaptador(IdentidadVisualJpaRepository filas,
            ProveedorDeContexto contexto) {
        this.filas = filas;
        this.contexto = contexto;
    }

    @Override
    public Optional<IdentidadVisual> buscar() {
        return filas.findAll().stream().findFirst().map(IdentidadVisualAdaptador::aDominio);
    }

    @Override
    @Transactional
    public IdentidadVisual guardar(IdentidadVisual identidad) {
        var fila = filas.findById(identidad.id()).orElse(null);

        String principal = identidad.clave(LogoDeEmpresa.PRINCIPAL).orElse(null);
        String ticket = identidad.clave(LogoDeEmpresa.TICKET).orElse(null);
        String simbolo = identidad.clave(LogoDeEmpresa.SIMBOLO).orElse(null);

        if (fila == null) {
            fila = new IdentidadVisualJpa(
                    identidad.id(),
                    // La empresa sale del contexto, nunca del agregado: es la
                    // misma contra la que la política va a comprobar.
                    contexto.obligatorio().empresaActivaObligatoria(),
                    principal, ticket, simbolo);
        } else {
            fila.actualizarDesde(principal, ticket, simbolo);
        }

        return aDominio(filas.save(fila));
    }

    private static IdentidadVisual aDominio(IdentidadVisualJpa fila) {
        return new IdentidadVisual(
                fila.getId(),
                fila.getEmpresaId(),
                fila.getLogoPrincipal(),
                fila.getLogoTicket(),
                fila.getSimbolo());
    }
}
