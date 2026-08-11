/**
 * Nucleo del dominio de Ondexia: entidades, enumerados, catalogos SUNAT y los
 * puertos de persistencia.
 *
 * <h2>Como se organiza el backend</h2>
 *
 * Monolito modular con hexagonal pragmatica. El corte principal es
 * <strong>por dominio</strong>, no por capa tecnica: existe {@code almacen} y
 * existe {@code ventas}, y dentro de cada uno estan sus capas. Al reves —un
 * paquete {@code service} con sesenta clases— no hay ninguna frontera que
 * impida que Ventas llame directo al repositorio de Almacen, y a los 23
 * submodulos del alcance eso es un monolito enredado.
 *
 * <pre>
 *   ondexia-domain                       ondexia-api
 *   com.ondexia.domain.&lt;dominio&gt;         com.ondexia.api.&lt;dominio&gt;
 *     entidades, enums, puertos            aplicacion/     casos de uso
 *                                          infraestructura/ adaptadores
 *                                          web/            controladores y DTO
 * </pre>
 *
 * La capa de dominio vive aqui, en un modulo Maven aparte, porque
 * {@code ondexia-facturacion} tambien la necesita: el Emisor lee el documento
 * de venta que el nucleo comercial escribio. Las otras tres capas viven en
 * {@code ondexia-api} y no se comparten.
 *
 * <h2>Por que las entidades llevan anotaciones de JPA</h2>
 *
 * Es hexagonal <em>pragmatica</em>, y la palabra importa. La version estricta
 * exige que el dominio sea POJO puro, con entidades de persistencia aparte y un
 * mapeador por agregado. Eso duplica unas setenta clases de modelo y obliga a
 * mantener los mapeadores a mano, a cambio de una independencia de la que este
 * proyecto no va a hacer uso: no vamos a cambiar de PostgreSQL, y RLS —que es
 * una decision de aislamiento multiempresa (DTE §5.1)— ya nos ata al motor a
 * proposito.
 *
 * Lo que si se conserva de hexagonal es lo que rinde: <strong>el dominio no
 * conoce HTTP ni Spring Web ni seguridad</strong>, y las dependencias apuntan
 * hacia adentro. Este modulo no depende de ningun otro modulo de Ondexia. Si
 * empieza a importar de {@code com.ondexia.api}, la separacion se perdio.
 *
 * <h2>Nomenclatura</h2>
 *
 * Sustantivo del dominio en espanol, sufijo tecnico en ingles:
 * {@code UsuarioRepository}, {@code ProductoService},
 * {@code EmitirBoletaUseCase}. Los metodos de negocio van en espanol
 * ({@code calcularCostoPromedio}); los derivados de Spring Data van en ingles
 * porque el framework <em>parsea</em> el nombre para construir la consulta —
 * {@code findByEmpresaIdAndCodigo} no se puede llamar de otra forma sin
 * escribir la consulta a mano.
 *
 * Tablas y columnas en {@code snake_case} espanol, como fija el DTE §5.
 */
package com.ondexia.domain;
