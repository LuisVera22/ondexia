-- =============================================================================
-- V23 · El vuelto del efectivo, y la venta sin existencias bloqueada de origen
-- =============================================================================
--
-- Dos observaciones del propietario sobre el punto de venta del 2026-09-08.
--
-- 1. VUELTO. Cobrar S/ 98 con un billete de 100 es el caso mas comun del
--    mostrador y hasta ahora era un error de validacion: DocumentoVenta exige
--    que los pagos sumen EXACTAMENTE el total, asi que la caja mostraba «los
--    pagos superan el total en S/ 2.00» y no dejaba registrar.
--
--    Lo que se guarda es `entregado`, y solo eso. El `monto` del pago sigue
--    siendo lo que se aplica al documento y no se toca, porque de el dependen
--    dos cosas que no pueden moverse: el total del comprobante que va a SUNAT
--    —donde el billete del cliente no pinta nada— y el arqueo de la sesion, que
--    compara lo cobrado con lo contado. Si el vuelto entrara en el monto, la
--    caja cuadraria de menos cada vez que alguien paga con billete grande.
--
--    El vuelto NO se almacena: es `entregado - monto`, y un valor derivado que
--    se guarda es un valor que algun dia contradice a sus operandos.
--
--    Solo en efectivo. Una tarjeta cobra el importe exacto y una transferencia
--    tambien; el CHECK lo impide en vez de confiar en que la aplicacion mire.
--
-- 2. SIN EXISTENCIAS. `permite_venta_sin_stock` existe desde la V19 y nacio en
--    `true`, que es lo que dejaba vender un producto con cero unidades sin
--    decir nada. Pasa a `false` para las empresas que se registren de ahora en
--    adelante.
--
--    Las empresas YA REGISTRADAS se quedan como estan. Cambiarles la regla en
--    una migracion seria decidir por un cliente que quiza vende contra
--    mercaderia en transito, y descubrirlo en mitad de una venta.
--
--    Donde vive la regla importa para lo que viene: la comprobacion esta en la
--    DESCARGA de existencias (DocumentosDeVenta.descargarExistencias), no en el
--    documento. Una cotizacion o una proforma no descargan nada, asi que no
--    pasan por ahi y el ajuste no les afecta — que es justo lo que se quiere
--    cuando existan. Atarlo al tipo de comprobante habria obligado a mantener
--    una lista de excepciones.
--
-- Privilegios: no se crea ninguna tabla. La columna nueva la lee y escribe
-- ondexia_app con los grants de `pago` (V8).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Lo que el cliente puso sobre el mostrador
-- -----------------------------------------------------------------------------

ALTER TABLE pago ADD COLUMN entregado numeric(18,6);

COMMENT ON COLUMN pago.entregado IS
    'Lo que el cliente entrego en efectivo, cuando fue mas que el monto. NULL '
    'si pago justo o si no fue en efectivo. El vuelto es entregado - monto y no '
    'se guarda: es derivado.';

-- Nunca por debajo del monto: eso no seria un vuelto, seria un cobro parcial, y
-- un cobro parcial se expresa bajando el monto.
ALTER TABLE pago ADD CONSTRAINT pago_entregado_cubre_el_monto
    CHECK (entregado IS NULL OR entregado >= monto);

-- Solo el efectivo da vuelto.
ALTER TABLE pago ADD CONSTRAINT pago_entregado_solo_efectivo
    CHECK (entregado IS NULL OR forma = 'EFECTIVO');


-- -----------------------------------------------------------------------------
-- 2. La venta sin existencias, bloqueada de origen
-- -----------------------------------------------------------------------------

ALTER TABLE empresa ALTER COLUMN permite_venta_sin_stock SET DEFAULT false;

COMMENT ON COLUMN empresa.permite_venta_sin_stock IS
    'Si se puede registrar una venta que deje una existencia en negativo. Por '
    'omision NO desde la V23. Solo afecta a las lineas que descargan almacen: '
    'un servicio no descarga, y una cotizacion tampoco descargara.';
