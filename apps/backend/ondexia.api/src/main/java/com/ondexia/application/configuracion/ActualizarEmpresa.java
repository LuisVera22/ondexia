package com.ondexia.application.configuracion;

import com.ondexia.domain.auditoria.RegistroDeAuditoria;
import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.consultas.DatosDeRuc;
import com.ondexia.domain.consultas.VerificacionDeRuc;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.identidad.RegimenTributario;
import com.ondexia.domain.identidad.EmpresaRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambia los datos fiscales de una empresa del usuario.
 *
 * <h2>Lo que viene de SUNAT tampoco se toca</h2>
 *
 * <p>Razon social, domicilio fiscal y ubigeo <strong>ya no estan entre los
 * parametros</strong>. Llegaban por el formulario y eso era un error latente:
 * una razon social que no coincide con el padron hace que SUNAT rechace
 * <em>todos</em> los comprobantes de esa empresa, y el fallo aparece en la
 * primera emision real — muy lejos de la pantalla donde alguien la escribio.
 *
 * <p>Pero «no editable» no puede significar «congelado». Una razon social cambia
 * legitimamente en SUNAT, y las empresas dadas de alta por el onboarding la
 * tienen tecleada a mano. Sin forma de traerla del padron, un dato corregible se
 * volveria imposible de corregir, que es peor que dejarlo editable. Ese camino
 * es {@link #refrescarDesdeSunat(String)}.
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
    private final EmpresasDelUsuario empresasDelUsuario;
    private final RegistroDeAuditoria auditoria;
    private final ProveedorDeContexto contexto;
    private final VerificacionDeRuc verificacion;

    public ActualizarEmpresa(EmpresaRepositorio empresas, EmpresasDelUsuario empresasDelUsuario,
            RegistroDeAuditoria auditoria, ProveedorDeContexto contexto,
            VerificacionDeRuc verificacion) {
        this.empresas = empresas;
        this.empresasDelUsuario = empresasDelUsuario;
        this.auditoria = auditoria;
        this.contexto = contexto;
        this.verificacion = verificacion;
    }

    /**
     * Siempre sobre la empresa activa, aunque el listado deje abrir cualquiera.
     *
     * <h2>Por qué no hay una variante que reciba el id</h2>
     *
     * <p>Sería fácil de escribir —{@link EmpresasDelUsuario} ya comprueba el
     * acceso— y quedaría mal. La bitácora archiva cada anotación bajo la
     * <em>empresa activa</em>, no bajo la entidad tocada
     * ({@code RegistroDeAuditoriaJpa}), y la política de aislamiento de
     * {@code auditoria} solo admite ese valor. Editar la empresa B mientras la
     * activa es A dejaría el rastro del cambio en la bitácora de A: no falla
     * nada, y el historial de B queda incompleto para siempre.
     *
     * <p>Arreglarlo de verdad es cambiar el modelo de auditoría y su política
     * RLS. Mientras tanto la ficha de una empresa que no es la activa se sirve
     * en solo lectura, y editarla exige pasar a trabajar en ella — que es
     * exactamente la condición que esta clase necesita.
     *
     * <p>Los dos unicos campos que son nuestros: el nombre comercial —SUNAT
     * tiene uno registrado, pero las empresas usan el que quieren en sus
     * facturas y no hay ninguna consecuencia en que difieran— y la cuenta de
     * detracciones.
     *
     * @param cuentaDetracciones vacio o nulo la borra
     */
    @Transactional
    public Empresa ejecutar(String nombreComercial, String cuentaDetracciones,
            RegimenTributario regimen) {
        return ejecutar(nombreComercial, cuentaDetracciones, regimen, null);
    }

    /** @param permiteVentaSinStock {@code null} = no tocarlo, como el régimen. */
    @Transactional
    public Empresa ejecutar(String nombreComercial, String cuentaDetracciones,
            RegimenTributario regimen, Boolean permiteVentaSinStock) {
        var empresaId = contexto.obligatorio().empresaActivaObligatoria();
        var empresa = empresasDelUsuario.exigirAcceso(empresaId);

        // Se copia el estado ANTES de mutar. La auditoria necesita las dos
        // caras, y el agregado es mutable: leerlo despues daria dos veces lo
        // mismo y el registro no diria que cambio.
        var antes = InstantaneaEmpresa.de(empresa);

        empresa.renombrarComercialmente(nombreComercial);
        empresa.anotarCuentaDetracciones(cuentaDetracciones);
        // `null` = no tocarlo: el PUT viene de una pantalla que puede no saber
        // del regimen (la ficha antigua) y no debe resetearlo a OTRO por omision.
        if (regimen != null) {
            empresa.cambiarRegimen(regimen);
        }
        if (permiteVentaSinStock != null) {
            empresa.fijarVentaSinStock(permiteVentaSinStock);
        }

        return guardarYRegistrar(empresaId, empresa, antes);
    }

    /**
     * Trae del padron lo que la empresa no puede editar.
     *
     * <h2>Dos usos, y el segundo es el que desbloquea el cambio</h2>
     *
     * <p>Uno: refrescar una empresa ya verificada cuando su razon social o su
     * domicilio cambian en SUNAT. Dos: verificar por primera vez una empresa
     * creada por el onboarding, cuyos datos los tecleo una persona. Sin este
     * segundo uso, quitar esos campos del {@code PUT} las dejaria congeladas con
     * un dato posiblemente mal escrito y ninguna salida.
     *
     * <p>No exige que el RUC siga apto. Una empresa que ya opera y pasa a
     * NO HABIDO tiene que poder registrar ese hecho: es justo lo que hay que ver
     * en pantalla, y bloquear la actualizacion dejaria el dato viejo — la unica
     * version que de verdad engania.
     *
     * @param atestacion lo que devolvio {@code GET /consultas/ruc/{ruc}}
     */
    @Transactional
    public Empresa refrescarDesdeSunat(String atestacion) {
        var empresaId = contexto.obligatorio().empresaActivaObligatoria();
        var empresa = empresasDelUsuario.exigirAcceso(empresaId);

        DatosDeRuc datos = verificacion.comprobar(atestacion, contexto.obligatorio().sub());

        var antes = InstantaneaEmpresa.de(empresa);

        // Lanza si la atestacion es de otro RUC. Sin esa comprobacion, consultar
        // un RUC y aplicarlo a la empresa equivocada reescribiria su razon social
        // con la de otro contribuyente — y la firma seria valida, asi que no
        // habria nada mas que lo detuviera.
        empresa.refrescarDesdeSunat(datos);

        return guardarYRegistrar(empresaId, empresa, antes);
    }

    private Empresa guardarYRegistrar(java.util.UUID empresaId, Empresa empresa,
            InstantaneaEmpresa antes) {
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
            String domicilioFiscal, String ubigeo, String cuentaDetracciones,
            String estado, String condicion) {

        static InstantaneaEmpresa de(Empresa empresa) {
            var verificacion = empresa.verificacion();
            return new InstantaneaEmpresa(
                    empresa.ruc().valor(),
                    empresa.razonSocial(),
                    empresa.nombreComercial(),
                    empresa.domicilioFiscal(),
                    empresa.ubigeo() == null ? null : empresa.ubigeo().valor(),
                    empresa.cuentaDetracciones(),
                    verificacion == null ? null : verificacion.estado().name(),
                    verificacion == null ? null : verificacion.condicion().name());
        }
    }
}
