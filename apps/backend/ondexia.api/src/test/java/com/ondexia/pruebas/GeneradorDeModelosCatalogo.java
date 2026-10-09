package com.ondexia.pruebas;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** Los modelos usados por el cliente Angular salen del contrato exportado, sin una segunda copia. */
final class GeneradorDeModelosCatalogo {
    private GeneradorDeModelosCatalogo() { }

    @SuppressWarnings("unchecked")
    static void generar(Path contrato, Path destino) throws Exception {
        Map<String, Object> documento = new Yaml().load(Files.readString(contrato));
        var componentes = (Map<String, Object>) documento.get("components");
        var esquemas = (Map<String, Map<String, Object>>) componentes.get("schemas");
        var producto = (Map<String, Map<String, Object>>) esquemas.get("RespuestaProducto").get("properties");
        var tipos = (List<String>) producto.get("tipo").get("enum");
        StringBuilder contenido = new StringBuilder("// Generado desde ondexia.contracts/openapi.yaml por ExportarContratoIT. No editar a mano.\n\n");
        contenido.append("export type TipoProducto = ")
                .append(String.join(" | ", tipos.stream().map(tipo -> "'" + tipo + "'").toList())).append(";\n");
        for (var nombres : List.of(List.of("RespuestaProducto", "ProductoApi"),
                List.of("RespuestaDisponible", "ProductoDisponibleApi"),
                List.of("RespuestaDisponibilidad", "DisponibilidadApi"),
                List.of("PeticionEdicionProducto", "DatosProducto"))) {
            contenido.append("\nexport interface ").append(nombres.get(1)).append(" {\n");
            var propiedades = (Map<String, Map<String, Object>>) esquemas.get(nombres.get(0)).get("properties");
            for (var propiedad : propiedades.entrySet()) {
                String tipo = switch ((String) propiedad.getValue().get("type")) {
                    case "string" -> "string";
                    case "number", "integer" -> "number";
                    case "boolean" -> "boolean";
                    default -> throw new IllegalStateException("Tipo no contemplado: " + propiedad.getKey());
                };
                if (propiedad.getKey().equals("tipo")) tipo = "TipoProducto";
                if (Boolean.TRUE.equals(propiedad.getValue().get("nullable"))) tipo += " | null";
                contenido.append("  readonly ").append(propiedad.getKey()).append(": ").append(tipo).append(";\n");
            }
            contenido.append("}\n");
        }
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, contenido, StandardCharsets.UTF_8);
    }
}
