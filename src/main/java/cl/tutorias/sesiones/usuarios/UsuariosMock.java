package cl.tutorias.sesiones.usuarios;

import cl.tutorias.sesiones.dominio.ValidadorTutorRamo;
import org.springframework.stereotype.Component;

/**
 * Reemplazo del servicio de Usuarios mientras no exista: todo usuario tiene todos los ramos activos.
 *
 * <p>shortcut: no valida nada; cuando {@code usuarios.proto} esté en {@code contracts/proto},
 * agregar un adaptador gRPC a {@code Usuarios.ValidarTutorRamo} (dirección en {@code USUARIOS_GRPC_URL})
 * que responda {@code UNAVAILABLE} si Usuarios no contesta, y usar este mock solo sin esa variable.
 */
@Component
public class UsuariosMock implements ValidadorTutorRamo {

    @Override
    public boolean tieneRamoActivo(String tutorId, String ramoCodigo) {
        return true;
    }
}
