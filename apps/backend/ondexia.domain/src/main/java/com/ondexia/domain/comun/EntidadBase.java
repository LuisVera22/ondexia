package com.ondexia.domain.comun;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Identidad y marcas de tiempo comunes a toda entidad persistente.
 *
 * <p><strong>UUID y no un entero autoincremental.</strong> Dos razones, y la
 * segunda es la que decide. La primera es que un identificador secuencial
 * expuesto en una URL permite enumerar: {@code /clientes/41} le dice a
 * cualquiera que existe un cliente 40. La segunda es que en un sistema
 * multiempresa el identificador viaja entre empresas —en referencias de nota de
 * credito, en trazas, en soporte— y un entero que se repite en cada tabla hace
 * ambiguo un numero suelto.
 *
 * <p><strong>UUID ordenado por tiempo, no aleatorio.</strong> Un UUID v4 puro
 * cae en una posicion arbitraria del indice en cada insercion, lo que fragmenta
 * el arbol y multiplica la escritura. El estilo {@code TIME} de Hibernate
 * genera identificadores monotonos, asi que las filas nuevas caen juntas al
 * final del indice, como haria una secuencia. Se conserva el beneficio del UUID
 * sin pagar su costo en el indice.
 *
 * <p><strong>Las marcas de tiempo las pone la base, no la JVM.</strong>
 * {@code CreationTimestamp} y {@code UpdateTimestamp} de Hibernate resuelven
 * contra el reloj de la sesion de base de datos. Con varias Lambdas
 * concurrentes, el reloj de cada contenedor puede diferir, y un
 * {@code creado_en} que retrocede rompe cualquier consulta ordenada por fecha —
 * incluido el kardex, que valoriza en orden cronologico estricto.
 */
@MappedSuperclass
public abstract class EntidadBase {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp(source = org.hibernate.annotations.SourceType.DB)
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp(source = org.hibernate.annotations.SourceType.DB)
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    public UUID getId() {
        return id;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    /**
     * Igualdad por identificador, nunca por campos.
     *
     * <p>Se compara {@code Hibernate.getClass} y no {@code getClass} porque una
     * entidad cargada de forma perezosa es un proxy de una subclase generada:
     * comparar la clase directa haria que una entidad y su propio proxy no
     * fueran iguales, con consecuencias silenciosas dentro de un {@code Set}.
     *
     * <p>Una entidad todavia sin persistir solo es igual a si misma. Es
     * deliberado: dos entidades nuevas con los mismos campos son dos filas
     * distintas.
     */
    @Override
    public final boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (otro == null || org.hibernate.Hibernate.getClass(this) != org.hibernate.Hibernate.getClass(otro)) {
            return false;
        }
        EntidadBase otraEntidad = (EntidadBase) otro;
        return id != null && id.equals(otraEntidad.id);
    }

    /**
     * Constante por tipo, a proposito.
     *
     * <p>El identificador lo asigna la persistencia, asi que si el hash
     * dependiera de el, una entidad metida en un {@code HashSet} antes de
     * guardarse cambiaria de cubeta al guardarse y quedaria irrecuperable
     * dentro de su propia coleccion. Es un fallo clasico y dificil de ver.
     */
    @Override
    public final int hashCode() {
        return org.hibernate.Hibernate.getClass(this).hashCode();
    }
}
