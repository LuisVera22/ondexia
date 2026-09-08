-- =============================================================================
-- V21 · Anular y canjear: la nota de credito y el canje de nota de venta
-- =============================================================================
--
-- Plan del primer producto, iteracion 6 (doc 12 §3.2 y §3.3, doc 13 §5). Los
-- dos casos son un documento_venta mas, con la referencia a lo que modifican:
--
--   nota de credito (07) ──documento_origen_id──▶ boleta o factura aceptada
--   boleta o factura     ──documento_origen_id──▶ nota de venta canjeada
--
-- Por eso no hay tabla nueva. Lo que se anade son las cuatro columnas que
-- describen esa referencia, y estan denormalizadas a proposito: tipo, serie y
-- numero del original son lo que se IMPRIME y lo que viaja en el XML, y tienen
-- que decir lo mismo dentro de diez anos aunque el original cambie de estado.
-- =============================================================================

ALTER TABLE documento_venta
    -- Catalogo 09 de SUNAT. Solo en una nota de credito.
    ADD COLUMN motivo_nota  varchar(2),
    -- El documento al que este se refiere, copiado.
    ADD COLUMN origen_tipo   varchar(2),
    ADD COLUMN origen_serie  varchar(4),
    ADD COLUMN origen_numero bigint;

-- El motivo va exactamente en las notas de credito: en cualquier otro
-- documento no significa nada, y una nota sin el no se puede declarar.
ALTER TABLE documento_venta ADD CONSTRAINT documento_venta_motivo_coherente CHECK (
    (tipo_documento = '07') = (motivo_nota IS NOT NULL)
);

-- La referencia va entera o no va. Media referencia —serie sin numero— seria
-- un documento que apunta a ninguna parte y que SUNAT rechazaria al recibirlo.
ALTER TABLE documento_venta ADD CONSTRAINT documento_venta_origen_completo CHECK (
    (documento_origen_id IS NULL
     AND origen_tipo IS NULL AND origen_serie IS NULL AND origen_numero IS NULL)
    OR (documento_origen_id IS NOT NULL
     AND origen_tipo IS NOT NULL AND origen_serie IS NOT NULL AND origen_numero IS NOT NULL)
);

-- Una nota de credito SIEMPRE se refiere a algo. Es la diferencia con una
-- boleta, que puede no tener origen.
ALTER TABLE documento_venta ADD CONSTRAINT documento_venta_nota_con_origen CHECK (
    tipo_documento <> '07' OR documento_origen_id IS NOT NULL
);

CREATE INDEX documento_venta_por_origen ON documento_venta (documento_origen_id)
    WHERE documento_origen_id IS NOT NULL;

COMMENT ON COLUMN documento_venta.origen_serie IS
    'Serie del documento al que este se refiere, copiada. Denormalizada a proposito: '
    'es lo que se imprime y lo que viaja en el XML de la nota de credito.';


/*
 * El disparador de inmutabilidad, rehecho con las cuatro columnas nuevas.
 *
 * No basta con que existan: la funcion compara una lista EXPLICITA de columnas,
 * asi que una columna que no este en la lista se podria modificar despues de
 * emitido sin que nada lo impidiera. Es justo lo que el disparador existe para
 * evitar, y el precio de la lista explicita es tener que tocarla aqui.
 */
CREATE OR REPLACE FUNCTION impedir_modificacion_documento_venta() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Un documento de venta no se borra; se anula'
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF row(NEW.*) IS DISTINCT FROM row(OLD.*)
       AND (NEW.id, NEW.empresa_id, NEW.sucursal_id, NEW.sesion_caja_id, NEW.tipo_documento,
            NEW.fiscal, NEW.serie, NEW.numero, NEW.cliente_id, NEW.fecha_emision,
            NEW.emitido_en, NEW.emitido_por, NEW.moneda, NEW.total_gravado,
            NEW.total_exonerado, NEW.total_inafecto, NEW.total_descuento, NEW.total_igv,
            NEW.total, NEW.observaciones, NEW.documento_origen_id, NEW.creado_en,
            NEW.motivo_nota, NEW.origen_tipo, NEW.origen_serie, NEW.origen_numero)
        IS DISTINCT FROM
           (OLD.id, OLD.empresa_id, OLD.sucursal_id, OLD.sesion_caja_id, OLD.tipo_documento,
            OLD.fiscal, OLD.serie, OLD.numero, OLD.cliente_id, OLD.fecha_emision,
            OLD.emitido_en, OLD.emitido_por, OLD.moneda, OLD.total_gravado,
            OLD.total_exonerado, OLD.total_inafecto, OLD.total_descuento, OLD.total_igv,
            OLD.total, OLD.observaciones, OLD.documento_origen_id, OLD.creado_en,
            OLD.motivo_nota, OLD.origen_tipo, OLD.origen_serie, OLD.origen_numero) THEN
        RAISE EXCEPTION 'Un documento de venta emitido no se modifica; solo cambia su estado'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;


-- -----------------------------------------------------------------------------
-- Permisos: las funciones de ventas.nota_credito
-- -----------------------------------------------------------------------------
--
-- El submodulo y sus tres funciones ya existen desde la V2
-- (consultar, registrar, emitir). Lo que falta es 'anular' —anular un
-- comprobante ya emitido tiene efecto tributario y quien atiende el mostrador
-- casi nunca debe poder hacerlo, que es la separacion que la V2 explica— y
-- 'canjear', que es del submodulo de la nota de venta y ya se creo en la V19.
--
-- Mismo idioma que la V17 y la V19: la funcion nueva, solo al Administrador
-- del sistema; el submodulo, a todo rol que ya alcance el modulo.

INSERT INTO permiso (id, codigo, modulo, accion, nivel, nombre, descripcion)
VALUES (gen_random_uuid(), 'ventas.nota_credito:anular', 'ventas.nota_credito', 'anular',
        'FUNCION', 'Anular un comprobante',
        'Emitir la nota de credito que deja sin efecto una boleta o factura')
ON CONFLICT DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id, p.id
FROM rol r, permiso p
WHERE r.cuenta_id IS NULL AND r.codigo = 'ADMINISTRADOR'
  AND p.modulo = 'ventas.nota_credito'
ON CONFLICT DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT rp.rol_id, sub.id
FROM rol_permiso rp
    JOIN permiso m   ON m.id = rp.permiso_id AND m.nivel = 'MODULO' AND m.modulo = 'ventas'
    JOIN permiso sub ON sub.codigo = 'ventas.nota_credito:acceder'
ON CONFLICT DO NOTHING;

UPDATE cuenta SET permisos_version = permisos_version + 1;
