-- Las filas existentes conservan NULL: no se inventa una identidad histórica.
ALTER TABLE documento_venta
    ADD COLUMN logo_principal varchar(400),
    ADD COLUMN logo_ticket varchar(400);

-- La captura ocurre en la misma transacción que la emisión, también en canjes
-- y notas de crédito. No acepta referencias suministradas por el cliente.
CREATE FUNCTION fijar_logos_de_documento() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    SELECT logo_principal, logo_ticket INTO NEW.logo_principal, NEW.logo_ticket
        FROM identidad_visual WHERE empresa_id = NEW.empresa_id;
    RETURN NEW;
END;
$$;

CREATE TRIGGER documento_venta_fija_logos
    BEFORE INSERT ON documento_venta
    FOR EACH ROW EXECUTE FUNCTION fijar_logos_de_documento();

-- Solo estado y la marca de actualización pueden cambiar. Comparar el registro
-- entero también protege columnas que se añadan en futuras migraciones.
CREATE OR REPLACE FUNCTION impedir_modificacion_documento_venta() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Un documento de venta no se borra; se anula'
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF (to_jsonb(NEW) - 'estado' - 'actualizado_en') IS DISTINCT FROM
       (to_jsonb(OLD) - 'estado' - 'actualizado_en') THEN
        RAISE EXCEPTION 'Un documento de venta emitido no se modifica; solo cambia su estado'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;

COMMENT ON COLUMN documento_venta.logo_principal IS
    'Clave inmutable de la versión del logo principal al emitir; NULL significa sin logo.';
COMMENT ON COLUMN documento_venta.logo_ticket IS
    'Clave inmutable de la versión del logo de ticket al emitir; NULL significa sin logo.';
