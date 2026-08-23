package com.ondexia.consultas;

import com.ondexia.domain.comun.Ruc;
import com.ondexia.domain.consultas.DatosDeRuc;
import java.util.Optional;

/**
 * Consultar un RUC desde la línea de órdenes, para probar de verdad.
 *
 * <h2>Por qué existe</h2>
 *
 * <p>Porque el mapeo de campos de cada proveedor viene de su documentación, y la
 * documentación de un servicio pequeño no siempre coincide con lo que devuelve.
 * Descubrir esa diferencia desplegando una Lambda es lento y confunde dos cosas
 * a la vez: si falla el mapeo o si falla el despliegue.
 *
 * <p>En IntelliJ: crear una configuración de tipo Application con esta clase,
 * poner el RUC en «Program arguments» y las claves en «Environment variables»:
 *
 * <pre>
 *   CONSULTAS_DECOLECTA_TOKEN=...
 *   CONSULTAS_FIRMA_SECRETO=cualquier-cosa-en-local
 * </pre>
 *
 * <p>No forma parte de lo que se ejecuta en producción — Lambda solo invoca
 * {@link ManejadorDeConsultas}—, pero se queda en el artefacto: pesa unas líneas
 * y es lo primero que hace falta el día que un proveedor cambie algo.
 */
public final class Consola {

    private Consola() {
    }

    public static void main(String[] argumentos) {
        if (argumentos.length != 1) {
            System.err.println("Uso: Consola <ruc de 11 dígitos>");
            System.exit(2);
            return;
        }

        ServicioDeConsultas servicio = ServicioDeConsultas.desdeElEntorno(System::getenv);
        Optional<ServicioDeConsultas.Resultado> resultado =
                servicio.consultar(new Ruc(argumentos[0]));

        if (resultado.isEmpty()) {
            System.out.println("SUNAT no tiene registrado ese RUC.");
            return;
        }

        DatosDeRuc datos = resultado.get().datos();
        System.out.println("Razón social:  " + datos.razonSocial());
        System.out.println("Estado:        " + datos.estado());
        System.out.println("Condición:     " + datos.condicion());
        System.out.println("Domicilio:     " + datos.domicilioFiscal());
        System.out.println("Ubigeo:        " + datos.ubigeo()
                + (datos.faltaUbigeo() ? "  <-- lo trae el padrón, no este proveedor" : ""));
        System.out.println("Distrito:      " + datos.distrito());
        System.out.println("Provincia:     " + datos.provincia());
        System.out.println("Departamento:  " + datos.departamento());
        System.out.println("Tipo:          " + datos.tipoSocietario());
        System.out.println("Agente ret.:   " + datos.esAgenteRetencion());
        System.out.println("Buen contrib.: " + datos.esBuenContribuyente());
        System.out.println();
        System.out.println("Apta para registro: " + datos.aptaParaRegistro());
        if (!datos.aptaParaRegistro()) {
            System.out.println("Motivo: " + datos.motivoDeRechazo());
        }
        System.out.println();
        System.out.println("Atestación: " + resultado.get().atestacion());
    }
}
