package cl.tutorias.sesiones.usuarios;

import cl.tutorias.sesiones.dominio.ValidadorTutorRamo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Reemplazo del servicio de Usuarios para desarrollo local: todo usuario tiene todos los ramos
 * activos. Se usa solo si {@code USUARIOS_GRPC_URL} no está definida; si lo está, {@link UsuariosGrpc}.
 */
@Component
@ConditionalOnExpression("'${USUARIOS_GRPC_URL:}'.isBlank()")
public class UsuariosMock implements ValidadorTutorRamo {

    @Override
    public boolean tieneRamoActivo(String tutorId, String ramoCodigo) {
        return true;
    }
}
