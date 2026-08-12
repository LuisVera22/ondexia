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
    @DisplayName("Un token cuyo sujeto no existe en nuestra base tampoco pasa")
    void tokenDeSujetoDesconocidoDevuelve401() throws Exception {
        // La firma es valida: el token lo emitimos nosotros. Lo que falla es que
        // no hay usuario detras. Es el caso de alguien dado de baja en nuestra
        // base cuyo usuario de Cognito sigue existiendo — y tiene que cerrarse.
        mockMvc.perform(get("/api/v1/contexto")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPara("nadie")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("no_autenticado"));
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
                .andExpect(jsonPath("$.empresas.length()").value(2))
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
