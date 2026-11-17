package cl.tutorias.sesiones.usuarios;

import cl.tutorias.sesiones.dominio.SesionException;
import cl.tutorias.sesiones.dominio.SesionException.Motivo;
import cl.tutorias.sesiones.dominio.ValidadorTutorRamo;
import cl.tutorias.usuarios.grpc.UsuariosServiceGrpc;
import cl.tutorias.usuarios.grpc.ValidarTutorRamoRequest;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

/**
 * Adaptador hacia el servicio de Usuarios: llama a {@code Usuarios.ValidarTutorRamo} por gRPC.
 * Se usa cuando está definida {@code USUARIOS_GRPC_URL} (ej. {@code usuarios:9090}); si no, {@link UsuariosMock}.
 */
@Component
@ConditionalOnExpression("!'${USUARIOS_GRPC_URL:}'.isBlank()")
public class UsuariosGrpc implements ValidadorTutorRamo {

    /** Tiempo máximo por llamada, para que un Usuarios caído no deje colgado el RPC de Sesiones. */
    private static final long PLAZO_SEGUNDOS = 3;

    private final UsuariosServiceGrpc.UsuariosServiceBlockingStub usuarios;

    public UsuariosGrpc(GrpcChannelFactory canales, @Value("${USUARIOS_GRPC_URL}") String direccion) {
        this.usuarios = UsuariosServiceGrpc.newBlockingStub(canales.createChannel(direccion));
    }

    @Override
    public boolean tieneRamoActivo(String tutorId, String ramoCodigo) {
        try {
            return usuarios.withDeadlineAfter(PLAZO_SEGUNDOS, TimeUnit.SECONDS)
                    .validarTutorRamo(ValidarTutorRamoRequest.newBuilder()
                            .setTutorId(tutorId).setRamoCodigo(ramoCodigo).build())
                    .getValido();
        } catch (StatusRuntimeException e) {
            switch (e.getStatus().getCode()) {
                // El contrato responde valido = false, pero un usuario inexistente significa lo mismo.
                case NOT_FOUND -> {
                    return false;
                }
                case UNAVAILABLE, DEADLINE_EXCEEDED ->
                    throw new SesionException(Motivo.USUARIOS_NO_DISPONIBLE, "Usuarios no responde");
                default -> throw e;
            }
        }
    }
}
