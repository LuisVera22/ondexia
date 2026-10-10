package com.ondexia.infrastructure.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Descripcion del contrato HTTP.
 *
 * <p>La direccion es codigo → contrato: springdoc lo deriva de los
 * controladores y el resultado se versiona en {@code ondexia.contracts}, de
 * donde se genera el cliente Angular. Con un solo desarrollador esto resuelve
 * el fallo caro que describe el doc 03 §5 — un campo renombrado en el backend y
 * no propagado al frontend aparece al compilar, no en produccion semanas
 * despues.
 */
@Configuration
public class OpenApiConfig {

    /** Texto decimal para clientes nuevos; números admitidos para peticiones anteriores. */
    @Bean
    public OpenApiCustomizer decimalesDeLaVenta() {
        return contrato -> {
            for (var entrada : Map.of("LineaPedida", List.of("cantidad", "descuento"),
                    "PagoPedido", List.of("monto", "entregado")).entrySet()) {
                var esquema = contrato.getComponents().getSchemas().get(entrada.getKey());
                for (String campo : entrada.getValue()) {
                    int escala = campo.equals("cantidad") ? 6 : 2;
                    boolean positivo = !campo.equals("descuento");
                    var textual = new StringSchema().pattern("^" + (positivo ? "(?!0+(?:\\.0+)?$)" : "")
                            + "[0-9]{1,12}(\\.[0-9]{1," + escala + "})?$");
                    var numerico = new NumberSchema();
                    numerico.setMinimum(BigDecimal.ZERO);
                    numerico.setExclusiveMinimum(positivo);
                    var decimal = new ComposedSchema().oneOf(List.of(textual, numerico))
                            .description("Decimal exacto como texto; se admiten números por compatibilidad. "
                                    + "Hasta 12 cifras enteras y " + escala + " decimales.");
                    decimal.setNullable(campo.equals("descuento") || campo.equals("entregado"));
                    esquema.addProperty(campo, decimal);
                }
            }
        };
    }

    @Bean
    public OpenAPI documentacionApi() {
        SecurityScheme esquemaJwt = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Token de acceso emitido por el grupo de usuarios de Cognito.");

        // Se declara como parametro reutilizable para que aparezca en el
        // contrato y el cliente generado la exponga. Sin esto, el generador
        // produce metodos sin forma de indicar la empresa activa y hay que
        // parchear el cliente a mano en cada regeneracion.
        HeaderParameter cabeceraEmpresa = (HeaderParameter) new HeaderParameter()
                .name("X-Empresa-Id")
                .description("""
                        Empresa sobre la que opera la peticion. Es una peticion, no una \
                        afirmacion: el servidor comprueba que el usuario tenga esa asignacion. \
                        Se puede omitir cuando el usuario solo tiene una empresa.""")
                .required(false);

        return new OpenAPI()
                .info(new Info()
                        .title("Ondexia — API Core")
                        .version("0.1.0")
                        .description("""
                                Nucleo comercial: almacen, compras, ventas y configuracion.

                                Esta API no habla con SUNAT. La emision de comprobantes se \
                                publica en una cola y la atiende ondexia-facturacion \
                                (DTE §3.3)."""))
                .components(new Components()
                        .addSecuritySchemes("jwt", esquemaJwt)
                        .addParameters("EmpresaActiva", cabeceraEmpresa))
                .addSecurityItem(new SecurityRequirement().addList("jwt"));
    }
}
