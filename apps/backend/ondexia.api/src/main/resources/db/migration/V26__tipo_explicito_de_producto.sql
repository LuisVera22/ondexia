-- V26 · Bien y servicio como tipos explícitos.
-- La V900 local declara tipo: no se deja DEFAULT para nuevas altas.
ALTER TABLE producto ADD COLUMN tipo varchar(8);
-- Flyway es propietario; FORCE RLS impediría rellenar todas las empresas.
-- El bloqueo de ALTER TABLE y la transacción de Flyway impiden exposición intermedia.
ALTER TABLE producto NO FORCE ROW LEVEL SECURITY;
UPDATE producto SET tipo = CASE WHEN controla_stock THEN 'BIEN' ELSE 'SERVICIO' END;
ALTER TABLE producto FORCE ROW LEVEL SECURITY;
ALTER TABLE producto ALTER COLUMN tipo SET NOT NULL;
ALTER TABLE producto ADD CONSTRAINT producto_tipo_valido CHECK (tipo IN ('BIEN', 'SERVICIO'));
-- Se conserva el booleano para las instantáneas y consumidores del mostrador;
-- es una proyección del tipo, nunca una decisión independiente.
ALTER TABLE producto ADD CONSTRAINT producto_stock_coherente CHECK (controla_stock = (tipo = 'BIEN'));

-- La conversión y cualquier escritura de stock comparten el bloqueo del producto.
-- Ver ConcurrenciaDeTiposIT: ambos órdenes de confirmación y saldo cero.
CREATE FUNCTION impedir_servicio_con_stock() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.tipo = 'BIEN' AND NEW.tipo = 'SERVICIO' AND (
        EXISTS (SELECT 1 FROM stock WHERE producto_id = OLD.id) OR
        EXISTS (SELECT 1 FROM movimiento_stock WHERE producto_id = OLD.id)) THEN
        RAISE EXCEPTION 'bien_con_historia_de_stock' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER producto_sin_stock_al_convertir BEFORE UPDATE OF tipo ON producto
    FOR EACH ROW EXECUTE FUNCTION impedir_servicio_con_stock();

CREATE FUNCTION exigir_bien_para_stock() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE tipo_actual varchar(8);
BEGIN
    SELECT tipo INTO tipo_actual FROM producto WHERE id = NEW.producto_id FOR UPDATE;
    IF tipo_actual IS DISTINCT FROM 'BIEN' THEN
        RAISE EXCEPTION 'producto_sin_existencias' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER movimiento_solo_de_bien BEFORE INSERT ON movimiento_stock
    FOR EACH ROW EXECUTE FUNCTION exigir_bien_para_stock();
CREATE TRIGGER existencias_solo_de_bien BEFORE INSERT OR UPDATE ON stock
    FOR EACH ROW EXECUTE FUNCTION exigir_bien_para_stock();
