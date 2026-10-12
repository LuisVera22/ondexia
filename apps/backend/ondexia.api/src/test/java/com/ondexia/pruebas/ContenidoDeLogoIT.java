package com.ondexia.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondexia.application.configuracion.Identidad;
import com.ondexia.domain.comun.error.ReglaDeNegocioViolada;
import com.ondexia.infrastructure.salida.marca.AlmacenDeMarcaEnMemoria;
import com.ondexia.infrastructure.seguridad.ContextoDePrueba;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class ContenidoDeLogoIT extends PruebaIntegracion {
    @Autowired private Identidad identidad;
    @Autowired private AlmacenDeMarcaEnMemoria almacen;

    @AfterEach void limpiar() { ContextoDePrueba.limpiar(); }

    private void contexto() {
        ContextoDePrueba.comoUsuarioDe(UUID.fromString(USUARIO_DEMO),
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                UUID.fromString(EMPRESA_ADMINISTRADA));
    }

    public static byte[] imagen(String formato) throws Exception {
        return ImagenDePrueba.de(formato);
    }

    private void rechazarSinCambiarLogo(String tipo, byte[] contenido) {
        contexto();
        var anterior = identidad.consultar();
        var subida = identidad.autorizarSubida("logo_principal", tipo, contenido.length);
        almacen.simularSubida(subida.clave(), tipo, contenido);
        assertThatThrownBy(() -> identidad.confirmarSubida("logo_principal", subida.clave()))
                .isInstanceOf(ReglaDeNegocioViolada.class)
                .extracting(fallo -> ((ReglaDeNegocioViolada) fallo).getCodigo()).isEqualTo("imagen_no_valida");
        assertThat(identidad.consultar()).isEqualTo(anterior);
    }

    @Test void textoAnunciadoComoPngNoSePublica() {
        rechazarSinCambiarLogo("image/png", "<svg onload='alert(1)'></svg>".getBytes(StandardCharsets.UTF_8));
    }

    @Test void jpegAnunciadoComoPngNoSePublica() throws Exception {
        rechazarSinCambiarLogo("image/png", imagen("jpeg"));
    }

    @Test void cabeceraPngSinImagenNoSePublica() throws Exception {
        rechazarSinCambiarLogo("image/png", Arrays.copyOf(imagen("png"), 40));
    }

    @Test void pngValidoSePublica() throws Exception {
        contexto();
        var datos = imagen("png");
        var subida = identidad.autorizarSubida("logo_principal", "image/png", datos.length);
        almacen.simularSubida(subida.clave(), "image/png", datos);
        identidad.confirmarSubida("logo_principal", subida.clave());
        assertThat(identidad.consultar().getFirst().url()).isEqualTo(almacen.urlPublica(subida.clave()));
    }

    @Test void jpegValidoSePublica() throws Exception {
        contexto();
        var datos = imagen("jpeg");
        var subida = identidad.autorizarSubida("logo_ticket", "image/jpeg", datos.length);
        almacen.simularSubida(subida.clave(), "image/jpeg", datos);
        identidad.confirmarSubida("logo_ticket", subida.clave());
        assertThat(identidad.consultar().get(1).url()).isEqualTo(almacen.urlPublica(subida.clave()));
    }

    @Test void contenidoFalsoDevuelveRechazoHttpConCodigoFijo() throws Exception {
        contexto();
        var datos = "contenido-ajeno".getBytes(StandardCharsets.UTF_8);
        var subida = identidad.autorizarSubida("logo_principal", "image/png", datos.length);
        almacen.simularSubida(subida.clave(), "image/png", datos);
        ContextoDePrueba.limpiar();
        mockMvc.perform(put("/api/v1/configuracion/identidad/logo_principal")
                .header("Authorization", autorizacionDemo()).header("X-Empresa-Id", EMPRESA_ADMINISTRADA)
                .contentType(MediaType.APPLICATION_JSON).content("{\"clave\":\"%s\"}".formatted(subida.clave())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("imagen_no_valida"))
                .andExpect(jsonPath("$.detail").value("El archivo no es una imagen PNG o JPG válida del formato indicado."));
    }
}
