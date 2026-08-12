package com.ondexia.infrastructure.salida.persistencia.comun;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Identidad y marcas de tiempo comunes a las tablas.
 *
 * <p>Vive en infraestructura, no en el dominio: {@code actualizado_en} es un
 * dato de la fila, no del negocio. Al agregado no le importa cuándo se escribió
 * por última vez.
 *
 * <p><strong>Las marcas las pone la base, no la JVM.</strong> Con varias Lambdas
 * concurrentes el reloj de cada contenedor puede diferir, y un
 * {@code creado_en} que retrocede rompe cualquier consulta ordenada por fecha —
 * incluido el kardex, que valoriza en orden cronológico estricto.
 *
 * <p>El identificador lo asigna el dominio, no Hibernate: un agregado sin
 * identidad no es un agregado. Por eso no hay {@code @GeneratedValue}.
 */
@MappedSuperclass
public abstract class EntidadJpaBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    protected UUID id;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "creado_en", nullable = false, updatable = false)
    protected Instant creadoEn;

    @UpdateTimestamp(source = SourceType.DB)
    @Column(name = "actualizado_en", nullable = false)
    protected Instant actualizadoEn;

    public UUID getId() {
        return id;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    @Override
    public final boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (otro == null
                || org.hibernate.Hibernate.getClass(this) != org.hibernate.Hibernate.getClass(otro)) {
            return false;
        }
        return id != null && id.equals(((EntidadJpaBase) otro).id);
    }

    /**
     * Constante por tipo, a propósito: si dependiera del identificador, una
     * entidad metida en un {@code HashSet} antes de guardarse cambiaría de
     * cubeta al guardarse y quedaría irrecuperable dentro de su propia
     * colección.
     */
    @Override
    public final int hashCode() {
        return org.hibernate.Hibernate.getClass(this).hashCode();
    }
}
