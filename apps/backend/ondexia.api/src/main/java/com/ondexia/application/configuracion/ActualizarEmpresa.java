package com.ondexia.application.configuracion;

import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.Ubigeo;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.identidad.EmpresaRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambia los datos fiscales de la empresa activa.
 *
 * <h2>El RUC no se toca</h2>
 *
 * <p>No aparece entre los parámetros. Cambiar el RUC de una empresa no es
 * corregir un dato: es decir que los comprobantes ya emitidos pertenecen a otro
 * contribuyente. Si de verdad se tecleó mal al darse de alta y todavía no se ha
 * emitido nada, lo correcto es crear la empresa correcta, no mutar esta.
 *
 * <p>El plan (doc 07, Entrega 1) preveía bloquearlo «cuando ya hay comprobantes
 * emitidos». Al escribirlo se vio que la condición sobra: <strong>no hay ningún
 * momento en que cambiarlo sea correcto</strong>, y una regla condicional
 * invitaría a discutir el caso límite en vez de crear la empresa buena.
 */
@Service
public class ActualizarEmpresa {

    private final EmpresaRepositorio empresas;
    private final RegistroDeAuditoria auditoria;
    private final ProveedorDeContexto contexto;

    public ActualizarEmpresa(EmpresaRepositorio empresas, RegistroDeAuditoria auditoria,
            ProveedorDeContexto contexto) {
        this.empresas = empresas;
        this.auditoria = auditoria;
        this.contexto = contexto;
    }

    /**
     * @param ubigeo puede ser {@code null}: es opcional mientras no haya
     *               integración con SUNAT, que es quien lo exige
     */
    @Transactional
    public Empresa ejecutar(String razonSocial, String nombreComercial, String domicilioFiscal,
            String ubigeo) {
        var empresaId = contexto.obligatorio().empresaActivaObligatoria();

        var empresa = empresas.buscarPorId(empresaId)
                .orElseThrow(() -> new RecursoNoEncontrado(
                        "empresa_no_encontrada", "La empresa activa ya no existe."));

        // Se copia el estado ANTES de mutar. La auditoría necesita las dos
        // caras, y el agregado es mutable: leerlo después daría dos veces lo
        // mismo y el registro no diría qué cambió.
        var antes = InstantaneaEmpresa.de(empresa);

        empresa.actualizarDatosFiscales(
                razonSocial,
                nombreComercial,
                domicilioFiscal,
                ubigeo == null || ubigeo.isBlank() ? null : new Ubigeo(ubigeo));

        var guardada = empresas.guardar(empresa);

        auditoria.registrarActualizacion(
                "empresa", empresaId, antes, InstantaneaEmpresa.de(guardada));

        return guardada;
    }

    /**
     * Lo que se guarda en la bitácora.
     *
     * <p>Solo datos fiscales. No entran {@code usuarioSol} ni
     * {@code secretArnCertificado}: la bitácora no debe recibir credenciales
     * SOL ni referencias a material criptográfico (DTE §8.4), y una vez dentro
     * ya no salen — la tabla es de solo inserción por disparador.
     */
    private record InstantaneaEmpresa(
            String ruc, String razonSocial, String nombreComercial,
            String domicilioFiscal, String ubigeo) {

        static InstantaneaEmpresa de(Empresa empresa) {
            return new InstantaneaEmpresa(
                    empresa.ruc().valor(),
                    empresa.razonSocial(),
                    empresa.nombreComercial(),
                    empresa.domicilioFiscal(),
                    empresa.ubigeo() == null ? null : empresa.ubigeo().valor());
        }
    }
}
