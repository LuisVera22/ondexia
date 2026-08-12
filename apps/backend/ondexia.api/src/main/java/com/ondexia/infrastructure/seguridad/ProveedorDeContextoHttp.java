package com.ondexia.infrastructure.seguridad;

import com.ondexia.domain.comun.ContextoOperacion;
import com.ondexia.domain.comun.ProveedorDeContexto;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Adaptador del puerto {@link ProveedorDeContexto} para peticiones HTTP.
 *
 * <p>Es la clase que permite que el dominio y los casos de uso no llamen a un
 * {@code ThreadLocal} estático: reciben esta interfaz inyectada, la dependencia
 * se ve en el constructor, y una prueba puede pasar otra implementación sin
 * levantar Spring ni fingir una petición.
 */
@Component
public class ProveedorDeContextoHttp implements ProveedorDeContexto {

    @Override
    public Optional<ContextoOperacion> actual() {
        return ContextoActual.obtener();
    }
}
