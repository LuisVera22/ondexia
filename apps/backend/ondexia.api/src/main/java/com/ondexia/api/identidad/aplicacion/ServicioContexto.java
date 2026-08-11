package com.ondexia.api.identidad.aplicacion;

import com.ondexia.api.comun.error.RecursoNoEncontradoException;
import com.ondexia.api.comun.seguridad.ContextoActual;
import com.ondexia.api.comun.seguridad.ContextoPeticion;
import com.ondexia.api.comun.seguridad.EvaluadorPermisos;
import com.ondexia.domain.identidad.AsignacionEmpresa;
import com.ondexia.domain.identidad.Usuario;
import com.ondexia.domain.identidad.UsuarioEmpresaRepository;
import com.ondexia.domain.identidad.UsuarioRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reune todo lo que el frontend necesita saber nada mas iniciar sesion.
 *
 * <p>Es una sola llamada a proposito. La alternativa —un endpoint para el
 * usuario, otro para sus empresas, otro para sus permisos— obliga al SPA a
 * encadenar tres peticiones antes de poder pintar el menu, y con arranque en
 * frio de Lambda eso son tres esperas en serie sobre la primera pantalla que ve
 * el usuario.
 */
@Service
public class ServicioContexto {

    private final UsuarioRepository usuarios;
    private final UsuarioEmpresaRepository asignaciones;
    private final EvaluadorPermisos permisos;

    public ServicioContexto(UsuarioRepository usuarios, UsuarioEmpresaRepository asignaciones,
            EvaluadorPermisos permisos) {
        this.usuarios = usuarios;
        this.asignaciones = asignaciones;
        this.permisos = permisos;
    }

    @Transactional(readOnly = true)
    public ContextoResuelto resolver() {
        ContextoPeticion contexto = ContextoActual.obtenerObligatorio();

        Usuario usuario = usuarios.findById(contexto.usuarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("usuario", contexto.usuarioId()));

        List<AsignacionEmpresa> empresas =
                asignaciones.findAsignacionesByUsuarioId(contexto.usuarioId());

        return new ContextoResuelto(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                contexto.cuentaId(),
                contexto.esAdministradorCuenta(),
                contexto.empresaId(),
                contexto.sucursalId(),
                empresas,
                // Los permisos del rol en la empresa activa. Sin empresa activa
                // el conjunto va vacio, y el frontend no debe mostrar ningun
                // modulo: no es que el usuario no pueda nada, es que todavia no
                // ha dicho sobre que empresa opera.
                permisos.codigosDelContexto());
    }
}
