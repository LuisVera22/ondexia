/**
 * API Core de Ondexia: reglas de negocio, correlativos, stock y persistencia.
 *
 * <h2>El boundary que no se cruza</h2>
 *
 * Este modulo <strong>nunca abre una conexion hacia SUNAT</strong> (DTE §3.3).
 * Publica un evento en una cola y responde. Toda comunicacion con el exterior
 * fiscal pasa por {@code ondexia-facturacion}, que es el unico componente que
 * toca certificados digitales.
 *
 * <p>La razon de fondo es que SUNAT se cae, y con frecuencia. Si la emision
 * fuera sincrona dentro de la transaccion de venta, cada caida de SUNAT seria
 * una caida de Ondexia. Con la cola de por medio, la venta se registra y el
 * comprobante se emite cuando SUNAT responda.
 *
 * <p>Si algun dia aparece aqui una dependencia de firma XML o un cliente SOAP
 * de SUNAT, la arquitectura se rompio.
 *
 * <h2>Organizacion</h2>
 *
 * <pre>
 *   comun/          lo transversal — seguridad, errores, persistencia, config
 *   identidad/      quien eres, sobre que empresa operas, que puedes
 *   desarrollo/     solo perfil local; ver SeguridadDesarrolloConfig
 *   &lt;dominio&gt;/      un paquete por dominio de negocio, no por capa tecnica
 * </pre>
 *
 * Dentro de cada dominio: {@code aplicacion} (casos de uso),
 * {@code infraestructura} (adaptadores) y {@code web} (controladores y DTO). La
 * capa de dominio vive en el modulo {@code ondexia-domain}, porque
 * {@code ondexia-facturacion} tambien la necesita.
 *
 * <p>El corte principal es por dominio y no por capa. Con un paquete
 * {@code service} de sesenta clases no hay ninguna frontera que impida que
 * Ventas llame directo al repositorio de Almacen, y a los 23 submodulos del
 * alcance eso es un monolito enredado.
 *
 * <h2>Las tres piezas que conviene leer antes de anadir nada</h2>
 *
 * <ol>
 *   <li>{@link com.ondexia.api.comun.seguridad.ContextoPeticion} — quien opera y
 *       sobre que empresa. Se resuelve en la base en cada peticion; el token
 *       porta identidad y nada mas.</li>
 *   <li>{@link com.ondexia.api.comun.persistencia.GestorTransaccionesConAislamiento}
 *       — como se fija el inquilino de PostgreSQL sin que la reutilizacion de
 *       conexiones en Lambda provoque una fuga entre clientes.</li>
 *   <li>{@link com.ondexia.api.comun.seguridad.EvaluadorPermisos} — autorizacion
 *       por {@code (modulo, accion)}, denegando por defecto.</li>
 * </ol>
 */
package com.ondexia.api;
