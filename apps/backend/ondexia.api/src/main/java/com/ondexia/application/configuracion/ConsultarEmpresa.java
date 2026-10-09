package com.ondexia.application.configuracion;

import com.ondexia.domain.comun.ProveedorDeContexto;
import com.ondexia.domain.comun.error.RecursoNoEncontrado;
import com.ondexia.domain.identidad.Empresa;
import com.ondexia.domain.identidad.EmpresaRepositorio;
import org.springframework.stereotype.Service;

/**
 * Devuelve los datos fiscales de la empresa activa.
 *
 * <p>No recibe identificador, y es deliberado. La empresa sale del contexto —es
 * decir, del token más la cabecera {@code X-Empresa-Id} que el interceptor ya
 * validó contra las asignaciones del usuario—, nunca de un parámetro.
 *
 * <p>La diferencia importa: con un {@code GET /empresas/{id}} habría que
 * comprobar en cada llamada que ese id es uno de los del usuario, y el día que
 * alguien olvide la comprobación queda una fuga de datos entre clientes. Aquí
 * <strong>no hay nada que olvidar</strong>, porque no existe forma de pedir otra
 * empresa.
 */
@Service
public class ConsultarEmpresa {

    private final EmpresaRepositorio empresas;
    private final ProveedorDeContexto contexto;

    public ConsultarEmpresa(EmpresaRepositorio empresas, ProveedorDeContexto contexto) {
        this.empresas = empresas;
        this.contexto = contexto;
    }

    public Empresa ejecutar() {
        var empresaId = contexto.obligatorio().empresaActivaObligatoria();

        return empresas.buscarPorId(empresaId)
                .orElseThrow(() -> RecursoNoEncontrado.con(
                        "empresa_no_encontrada",
                        "La empresa activa ya no existe. Vuelve a iniciar sesión."));
    }
}
