-- El legado conserva NULL. No conocemos sus datos originales y no los inventamos.
ALTER TABLE documento_venta ADD COLUMN datos_historicos jsonb;

CREATE FUNCTION fijar_datos_historicos_documento() RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    emisor jsonb;
    local_emision jsonb;
    adquirente jsonb;
BEGIN
    SELECT jsonb_build_object('ruc', ruc, 'razonSocial', razon_social,
        'nombreComercial', nombre_comercial, 'domicilioFiscal', domicilio_fiscal,
        'ubigeo', ubigeo) INTO emisor FROM empresa WHERE id = NEW.empresa_id;
    SELECT jsonb_build_object('id', id, 'nombre', nombre, 'direccion', direccion,
        'ubigeo', ubigeo, 'codigo', codigo) INTO local_emision FROM sucursal
        WHERE id = NEW.sucursal_id AND empresa_id = NEW.empresa_id;
    IF emisor IS NULL OR local_emision IS NULL THEN
        RAISE EXCEPTION 'No se puede fijar el emisor o local de la empresa'
            USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.cliente_id IS NOT NULL THEN
        SELECT jsonb_build_object('id', id, 'tipoDocumento', tipo_documento,
            'numeroDocumento', numero_documento, 'nombre', nombre,
            'direccion', direccion) INTO adquirente FROM cliente
            WHERE id = NEW.cliente_id AND empresa_id = NEW.empresa_id;
        IF adquirente IS NULL THEN
            RAISE EXCEPTION 'No se puede fijar el cliente de la empresa'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    NEW.datos_historicos := jsonb_build_object('emisor', emisor,
        'local', local_emision, 'cliente', adquirente);
    RETURN NEW;
END;
$$;

CREATE TRIGGER documento_venta_fija_datos_historicos BEFORE INSERT ON documento_venta
    FOR EACH ROW EXECUTE FUNCTION fijar_datos_historicos_documento();

-- La V27 compara el registro completo: protege también la nueva columna.
COMMENT ON COLUMN documento_venta.datos_historicos IS
    'Datos públicos del cliente, emisor y local al emitir; NULL indica legado sin instantánea.';
