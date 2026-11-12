package cl.tutorias.sesiones.grpc;

import cl.tutorias.sesiones.dominio.SesionException;
import cl.tutorias.sesiones.dominio.SesionException.Motivo;
import cl.tutorias.sesiones.dominio.SesionesService;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Adaptador gRPC: traduce los mensajes de sesiones.proto a llamadas a {@link SesionesService}
 * y los errores de negocio a códigos de estado.
 *
 * <p>Vive en el mismo paquete que las clases generadas desde el .proto, por eso aquí
 * {@code Sesion}, {@code EstadoSesion} y {@code BloqueDisponibilidad} son los mensajes gRPC
 * y las clases del dominio se nombran con su paquete completo.
 */
@Service
public class SesionesGrpcService extends SesionesServiceGrpc.SesionesServiceImplBase {

    private static final Map<Motivo, Status> ESTADOS = Map.of(
            Motivo.DATOS_INVALIDOS, Status.INVALID_ARGUMENT,
            Motivo.NO_ENCONTRADA, Status.NOT_FOUND,
            Motivo.CHOQUE_DE_HORARIO, Status.ALREADY_EXISTS,
            Motivo.TRANSICION_INVALIDA, Status.FAILED_PRECONDITION,
            Motivo.SIN_PERMISO, Status.PERMISSION_DENIED);

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final SesionesService servicio;

    public SesionesGrpcService(SesionesService servicio) {
        this.servicio = servicio;
    }

    @Override
    public void crearSesion(CrearSesionRequest req, StreamObserver<Sesion> obs) {
        responder(obs, () -> aProto(servicio.crear(req.getTutorId(), req.getEstudianteId(),
                req.getRamoCodigo(), aInstant(req.getInicio()), aInstant(req.getFin()))));
    }

    @Override
    public void obtenerSesion(ObtenerSesionRequest req, StreamObserver<Sesion> obs) {
        responder(obs, () -> aProto(servicio.obtener(req.getId())));
    }

    @Override
    public void listarSesiones(ListarSesionesRequest req, StreamObserver<ListarSesionesResponse> obs) {
        responder(obs, () -> ListarSesionesResponse.newBuilder()
                .addAllSesiones(servicio.listar(req.getTutorId(), req.getEstudianteId()).stream()
                        .map(SesionesGrpcService::aProto).toList())
                .build());
    }

    @Override
    public void confirmarSesion(CambiarEstadoRequest req, StreamObserver<Sesion> obs) {
        cambiarEstado(req, obs, EstadoSesion.CONFIRMADA);
    }

    @Override
    public void cancelarSesion(CambiarEstadoRequest req, StreamObserver<Sesion> obs) {
        cambiarEstado(req, obs, EstadoSesion.CANCELADA);
    }

    @Override
    public void finalizarSesion(CambiarEstadoRequest req, StreamObserver<Sesion> obs) {
        cambiarEstado(req, obs, EstadoSesion.FINALIZADA);
    }

    @Override
    public void obtenerDisponibilidad(ObtenerDisponibilidadRequest req,
            StreamObserver<ObtenerDisponibilidadResponse> obs) {
        responder(obs, () -> ObtenerDisponibilidadResponse.newBuilder()
                .addAllBloques(servicio.disponibilidad(req.getTutorId()).stream()
                        .map(SesionesGrpcService::aProto).toList())
                .build());
    }

    @Override
    public void verificarParticipacion(VerificarParticipacionRequest req,
            StreamObserver<VerificarParticipacionResponse> obs) {
        responder(obs, () -> {
            var sesion = servicio.obtener(req.getSesionId());
            return VerificarParticipacionResponse.newBuilder()
                    .setFinalizada(sesion.getEstado() == cl.tutorias.sesiones.dominio.EstadoSesion.FINALIZADA)
                    .setParticipo(sesion.getEstudianteId().equals(req.getEstudianteId()))
                    .build();
        });
    }

    private void cambiarEstado(CambiarEstadoRequest req, StreamObserver<Sesion> obs, EstadoSesion destino) {
        // Los dos enums usan los mismos nombres, así que se convierten por nombre.
        responder(obs, () -> aProto(servicio.cambiarEstado(req.getId(), req.getUsuarioId(),
                cl.tutorias.sesiones.dominio.EstadoSesion.valueOf(destino.name()))));
    }

    /** Envía la respuesta, o el código de estado gRPC que corresponde al error de negocio. */
    private static <T> void responder(StreamObserver<T> obs, Supplier<T> accion) {
        T respuesta;
        try {
            respuesta = accion.get();
        } catch (SesionException e) {
            obs.onError(ESTADOS.get(e.getMotivo()).withDescription(e.getMessage()).asRuntimeException());
            return;
        }
        obs.onNext(respuesta);
        obs.onCompleted();
    }

    private static Sesion aProto(cl.tutorias.sesiones.dominio.Sesion s) {
        return Sesion.newBuilder()
                .setId(s.getId().toString())
                .setTutorId(s.getTutorId())
                .setEstudianteId(s.getEstudianteId())
                .setRamoCodigo(s.getRamoCodigo())
                .setInicio(aTimestamp(s.getInicio()))
                .setFin(aTimestamp(s.getFin()))
                .setEstado(EstadoSesion.valueOf(s.getEstado().name()))
                .setCreadaEn(aTimestamp(s.getCreadaEn()))
                .build();
    }

    private static BloqueDisponibilidad aProto(cl.tutorias.sesiones.dominio.BloqueDisponibilidad b) {
        return BloqueDisponibilidad.newBuilder()
                .setTutorId(b.getTutorId())
                .setDia(b.getDia())
                .setHoraIni(b.getHoraIni().format(HORA))
                .setHoraFin(b.getHoraFin().format(HORA))
                .build();
    }

    private static Timestamp aTimestamp(Instant i) {
        return Timestamp.newBuilder().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build();
    }

    private static Instant aInstant(Timestamp t) {
        return Instant.ofEpochSecond(t.getSeconds(), t.getNanos());
    }
}
