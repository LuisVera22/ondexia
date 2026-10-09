package com.ondexia.pruebas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ondexia.pruebas.PruebaIntegracion;
import com.ondexia.infrastructure.seguridad.ContextoInterceptor;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * El recorrido completo de una peticion autenticada: token → usuario → empresa
 * activa → permisos.
 *
 * <p>Estas pruebas cubren la decision central del DTE §8.1 —que los permisos se
 * resuelven en la base y dependen de la empresa activa— y el control que la
 * sostiene: que la cabecera que envia el cliente se verifica y no se cree.
 */
class ContextoIT extends PruebaIntegracion {

    @Test
    @DisplayName("Sin token no se pasa")
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/contexto"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un token sin usuario detrás significa «falta registrarse», no «no autenticado»")
    void tokenDeSujetoDesconocidoPideRegistro() throws Exception {
        /*
         * Esta prueba exigía 401 y ahora exige 404 con código propio. El cambio
         * lo trajo el flujo de registro, y corrige un bucle real.
         *
         * La lectura anterior era «alguien dado de baja cuyo usuario de Cognito
         * sigue existiendo». Existe otro caso mucho más frecuente y que antes no
         * existía: el de quien acaba de crear su cuenta en Cognito y todavía no
         * ha completado el alta. Su identidad está probada; lo que falta es el
         * negocio.
         *
         * Con 401, el SPA cerraba la sesión y volvía al acceso — donde Cognito,
         * cuya sesión sí es válida, lo dejaba entrar otra vez. Un bucle del que
         * el usuario no puede salir.
         *
         * La baja sigue cubierta, y por otra vía: `ResolverContexto` comprueba
         * `usuario.estaActivo()` en CADA petición y responde 403. Esa es la que
         * corta el acceso a quien fue desactivado, no esta.
         */
        mockMvc.perform(get("/api/v1/contexto")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPara("nadie")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("usuario_no_registrado"));
    }

    @Test
    @DisplayName("Con varias empresas y sin elegir ninguna, no hay permisos")
    void sinEmpresaActivaNoHayPermisos() throws Exception {
        // El usuario demo tiene dos empresas. Sin cabecera no se puede saber
        // sobre cual opera, asi que el conjunto de permisos va vacio.
        //
        // No es que no pueda nada: es que todavia no ha dicho sobre que empresa.
        // La diferencia importa porque el frontend usa este conjunto para pintar
        // el menu, y un menu completo sin empresa elegida ofreceria acciones que
        // fallarian al ejecutarse.
        mockMvc.perform(get("/api/v1/contexto")
                        .header(HttpHeaders.AUTHORIZATION, autorizacionDemo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value(USUARIO_DEMO))
                .andExpect(jsonPath("$.cuenta.esAdministrador").value(true))
                .andExpect(jsonPath("$.empresaActiva").doesNotExist())
                // Al menos las dos de la V900: RegistroDeEmpresaIT deja mas en la
                // misma cuenta y no se pueden borrar (bitacora de solo insercion).
                .andExpect(jsonPath("$.empresas.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.permisos.length()").value(0));
    }

    @Test
    @DisplayName("Los permisos son los del rol EN ESA empresa, no los del usuario")
    void losPermisosDependenDeLaEmpresaActiva() throws Exception {
        // Misma persona, mismo token. En una empresa es Administrador y en la
        // otra Vendedor. Este par de comprobaciones es la razon de ser de todo el
        // mecanismo: si los permisos viajaran en el token, esto seria imposible.

        mockMvc.perform(get("/api/v1/contexto")
                        .header(HttpHeaders.AUTHORIZATION, autorizacionDemo())
                        .header(ContextoInterceptor.CABECERA_EMPRESA, EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empresaActiva.ruc").value("20100000009"))
                .andExpect(jsonPath("$.empresaActiva.rol").value("Administrador"))
                // Alcanza todas las sucursales.
                .andExpect(jsonPath("$.empresaActiva.sucursalId").doesNotExist())
                .andExpect(jsonPath("$.permisos",
                        Matchers.hasItem("ventas.comprobante:anular")));

        mockMvc.perform(get("/api/v1/contexto")
                        .header(HttpHeaders.AUTHORIZATION, autorizacionDemo())
                        .header(ContextoInterceptor.CABECERA_EMPRESA, EMPRESA_COMO_VENDEDOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empresaActiva.rol").value("Vendedor"))
                // Acotado a una sucursal: no puede cambiarse de local, porque eso
                // decide la serie del comprobante y que almacen descarga.
                .andExpect(jsonPath("$.empresaActiva.sucursalNombre").value("Principal"))
                // Emite, pero no anula. Es la separacion mas importante del
                // catalogo de permisos.
                .andExpect(jsonPath("$.permisos",
                        Matchers.hasItem("ventas.comprobante:emitir")))
                .andExpect(jsonPath("$.permisos",
                        Matchers.not(Matchers.hasItem("ventas.comprobante:anular"))));
    }

    @Test
    @DisplayName("Los establecimientos del contexto son los que el usuario alcanza")
    void losEstablecimientosSonLosQueElUsuarioAlcanza() throws Exception {
        // Es lo que alimenta el selector de la barra superior, y hasta que estuvo
        // aqui no habia forma de construirlo: la respuesta traia la ASIGNACION
        // del usuario —«todas» o «solo esta»— y de «todas» no se deduce cuales.
        // El resultado era el contrario del correcto: quien alcanzaba todos los
        // establecimientos se quedaba sin selector.

        // Administrador sin sucursal asignada: alcanza las de la empresa, no solo
        // una. No se afirma un numero exacto porque otras pruebas de la suite dan
        // de alta establecimientos en esta misma empresa —el contenedor y los
        // datos son unicos para toda la ejecucion—, y un recuento exacto fallaria
        // segun el orden en que corran, que es justo lo que una prueba no debe
        // hacer.
        mockMvc.perform(get("/api/v1/contexto")
                        .header(HttpHeaders.AUTHORIZATION, autorizacionDemo())
                        .header(ContextoInterceptor.CABECERA_EMPRESA, EMPRESA_ADMINISTRADA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.establecimientos[*].codigo",
                        Matchers.hasItems("0000", "0001")))
                // La casa matriz primero. El frontend toma el primero como
                // establecimiento activo mientras nadie elija otro, y eso decide
                // la serie del comprobante: ordenados por nombre se entraba
                // trabajando en «Miraflores» —el anexo 0001— en vez de en la
                // matriz. Por codigo no depende de como se llame un local.
                .andExpect(jsonPath("$.establecimientos[0].codigo").value("0000"));

        // Vendedor acotado a una: solo esa, aunque la empresa tuviera mas. El
        // recorte lo hace el servidor; si se dejara al frontend, bastaria con
        // abrir las herramientas del navegador para emitir desde otro local.
        mockMvc.perform(get("/api/v1/contexto")
                        .header(HttpHeaders.AUTHORIZATION, autorizacionDemo())
                        .header(ContextoInterceptor.CABECERA_EMPRESA, EMPRESA_COMO_VENDEDOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.establecimientos.length()").value(1))
                .andExpect(jsonPath("$.establecimientos[0].nombre").value("Principal"));
    }

    @Test
    @DisplayName("Sin empresa activa no hay establecimientos que ofrecer")
    void sinEmpresaActivaNoHayEstablecimientos() throws Exception {
        mockMvc.perform(get("/api/v1/contexto")
                        .header(HttpHeaders.AUTHORIZATION, autorizacionDemo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.establecimientos.length()").value(0));
    }

    @Test
    @DisplayName("Pedir una empresa que no es tuya se rechaza")
    void empresaAjenaDevuelve403() throws Exception {
        // Es el control que impide lo peor que puede pasar en este sistema: la
        // empresa determina con que certificado digital se firma, asi que un
        // empresa_id falsificado no seria ver datos ajenos, seria emitir un
        // comprobante con el certificado de otro RUC.
        mockMvc.perform(get("/api/v1/contexto")
                        .header(HttpHeaders.AUTHORIZATION, autorizacionDemo())
                        .header(ContextoInterceptor.CABECERA_EMPRESA,
                                "11111111-1111-4111-8111-111111111111"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("acceso_denegado"))
                // El mensaje no debe distinguir «no existe» de «existe y no es
                // tuya»: distinguirlos convertiria el endpoint en un buscador de
                // que empresas estan dadas de alta en Ondexia.
                .andExpect(jsonPath("$.detail").value("No tienes acceso a la empresa solicitada"));
    }

    @Test
    @DisplayName("Una cabecera de empresa mal formada es un 400, no un 500")
    void cabeceraMalFormadaDevuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/contexto")
                        .header(HttpHeaders.AUTHORIZATION, autorizacionDemo())
                        .header(ContextoInterceptor.CABECERA_EMPRESA, "no-es-un-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("solicitud_invalida"));
    }
}
