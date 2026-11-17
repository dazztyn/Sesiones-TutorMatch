package cl.tutorias.sesiones.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cl.tutorias.sesiones.ContenedoresDePrueba;
import cl.tutorias.sesiones.PruebaDeIntegracion;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Adaptador gRPC: traducción de mensajes y de errores a códigos de estado. Se llama directo a los
 * métodos del servicio, sin red, con un {@link StreamObserver} que guarda la respuesta.
 */
class SesionesGrpcServiceTest extends PruebaDeIntegracion {

    private static final String RAMO = "MAT1610";

    // Miércoles 6 de marzo de 2030, 08:10 y 09:55 en Chile.
    private static final Instant MIERCOLES_0810 = bloque(3, 6, 8, 10);
    private static final Instant MIERCOLES_0955 = bloque(3, 6, 9, 55);

    @Autowired
    private SesionesGrpcService grpc;

    /** Guarda la respuesta o el error de una llamada. */
    private static final class Respuesta<T> implements StreamObserver<T> {
        T valor;
        Status error;

        @Override
        public void onNext(T valor) {
            this.valor = valor;
        }

        @Override
        public void onError(Throwable t) {
            this.error = Status.fromThrowable(t);
        }

        @Override
        public void onCompleted() {
        }
    }

    private static <Q, R> R ok(BiConsumer<Q, StreamObserver<R>> rpc, Q request) {
        Respuesta<R> respuesta = new Respuesta<>();
        rpc.accept(request, respuesta);
        assertNull(respuesta.error, () -> "error inesperado: " + respuesta.error);
        return respuesta.valor;
    }

    private static <Q, R> Status.Code error(BiConsumer<Q, StreamObserver<R>> rpc, Q request) {
        Respuesta<R> respuesta = new Respuesta<>();
        rpc.accept(request, respuesta);
        assertNull(respuesta.valor);
        return respuesta.error.getCode();
    }

    private static Timestamp ts(Instant i) {
        return Timestamp.newBuilder().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build();
    }

    private BloqueDisponibilidad marcar(String tutor, String ramo, Instant inicio) {
        return ok(grpc::marcarCasilla, MarcarCasillaRequest.newBuilder()
                .setTutorId(tutor).setRamoCodigo(ramo).setInicio(ts(inicio)).build());
    }

    private CrearSesionRequest reserva(String tutor, String estudiante, Instant inicio) {
        return CrearSesionRequest.newBuilder().setTutorId(tutor).setEstudianteId(estudiante).setRamoCodigo(RAMO)
                .setInicio(ts(inicio)).setFin(ts(finDe(inicio))).build();
    }

    private static CambiarEstadoRequest cambio(String id, String usuario) {
        return CambiarEstadoRequest.newBuilder().setId(id).setUsuarioId(usuario).build();
    }

    @Test
    void cicloCompletoDeUnaReserva() {
        String tutor = usuario();
        String estudiante = usuario();
        BloqueDisponibilidad casilla = marcar(tutor, RAMO, MIERCOLES_0810);
        assertTrue(casilla.getLibre());
        assertEquals(ts(finDe(MIERCOLES_0810)), casilla.getFin());

        Sesion sesion = ok(grpc::crearSesion, reserva(tutor, estudiante, MIERCOLES_0810));
        assertEquals(EstadoSesion.PENDIENTE, sesion.getEstado());
        assertEquals(ts(MIERCOLES_0810), sesion.getInicio());
        assertEquals(sesion, ok(grpc::obtenerSesion, ObtenerSesionRequest.newBuilder().setId(sesion.getId()).build()));

        assertEquals(EstadoSesion.CONFIRMADA, ok(grpc::confirmarSesion, cambio(sesion.getId(), tutor)).getEstado());
        assertEquals(EstadoSesion.FINALIZADA, ok(grpc::finalizarSesion, cambio(sesion.getId(), tutor)).getEstado());

        VerificarParticipacionResponse participacion = ok(grpc::verificarParticipacion,
                VerificarParticipacionRequest.newBuilder().setSesionId(sesion.getId()).setEstudianteId(estudiante).build());
        assertTrue(participacion.getFinalizada());
        assertTrue(participacion.getParticipo());
        assertEquals(tutor, participacion.getTutorId());
        assertEquals(RAMO, participacion.getRamoCodigo());
        assertFalse(ok(grpc::verificarParticipacion, VerificarParticipacionRequest.newBuilder()
                .setSesionId(sesion.getId()).setEstudianteId(usuario()).build()).getParticipo());
    }

    @Test
    void listarConFiltroDeEstadoYRango() {
        String tutor = usuario();
        String estudiante = usuario();
        marcar(tutor, RAMO, MIERCOLES_0810);
        Sesion sesion = ok(grpc::crearSesion, reserva(tutor, estudiante, MIERCOLES_0810));

        ListarSesionesRequest.Builder filtro = ListarSesionesRequest.newBuilder().setTutorId(tutor);
        assertEquals(1, ok(grpc::listarSesiones, filtro.build()).getSesionesCount());
        assertEquals(sesion, ok(grpc::listarSesiones,
                filtro.setEstado(EstadoSesion.PENDIENTE).build()).getSesiones(0));
        assertEquals(0, ok(grpc::listarSesiones,
                filtro.setEstado(EstadoSesion.CONFIRMADA).build()).getSesionesCount());
        assertEquals(0, ok(grpc::listarSesiones, ListarSesionesRequest.newBuilder().setTutorId(tutor)
                .setDesde(ts(MIERCOLES_0955)).setHasta(ts(bloque(3, 7, 0, 0))).build()).getSesionesCount());
    }

    @Test
    void disponibilidadBusquedaYDesmarcar() {
        String tutor = usuario();
        String ramo = "GRPC" + usuario().substring(0, 6);
        marcar(tutor, ramo, MIERCOLES_0810);
        BloqueDisponibilidad segunda = marcar(tutor, ramo, MIERCOLES_0955);
        ok(grpc::crearSesion, CrearSesionRequest.newBuilder().setTutorId(tutor).setEstudianteId(usuario())
                .setRamoCodigo(ramo).setInicio(ts(MIERCOLES_0810)).setFin(ts(finDe(MIERCOLES_0810))).build());

        ObtenerDisponibilidadResponse delTutor = ok(grpc::obtenerDisponibilidad, ObtenerDisponibilidadRequest
                .newBuilder().setTutorId(tutor).setDesde(ts(bloque(3, 6, 0, 0))).setHasta(ts(bloque(3, 7, 0, 0))).build());
        assertEquals(2, delTutor.getBloquesCount());
        assertFalse(delTutor.getBloques(0).getLibre());
        assertTrue(delTutor.getBloques(1).getLibre());

        ObtenerDisponibilidadResponse delRamo = ok(grpc::buscarDisponibilidad, BuscarDisponibilidadRequest
                .newBuilder().setRamoCodigo(ramo).setDesde(ts(bloque(3, 6, 0, 0))).setHasta(ts(bloque(3, 7, 0, 0))).build());
        assertEquals(1, delRamo.getBloquesCount());
        assertEquals(segunda.getId(), delRamo.getBloques(0).getId());

        // Sin rango, la consulta es inválida.
        assertEquals(Status.Code.INVALID_ARGUMENT, error(grpc::obtenerDisponibilidad,
                ObtenerDisponibilidadRequest.newBuilder().setTutorId(tutor).build()));

        ok(grpc::desmarcarCasilla, DesmarcarCasillaRequest.newBuilder().setId(segunda.getId()).setTutorId(tutor).build());
        assertEquals(Status.Code.NOT_FOUND, error(grpc::desmarcarCasilla,
                DesmarcarCasillaRequest.newBuilder().setId(segunda.getId()).setTutorId(tutor).build()));
    }

    @Test
    void cadaErrorDeNegocioTieneSuCodigoGrpc() {
        String tutor = usuario();
        String estudiante = usuario();
        marcar(tutor, RAMO, MIERCOLES_0810);
        Sesion sesion = ok(grpc::crearSesion, reserva(tutor, estudiante, MIERCOLES_0810));

        assertEquals(Status.Code.NOT_FOUND,
                error(grpc::obtenerSesion, ObtenerSesionRequest.newBuilder().setId("no-existe").build()));
        assertEquals(Status.Code.INVALID_ARGUMENT, error(grpc::crearSesion, reserva(tutor, tutor, MIERCOLES_0810)));
        assertEquals(Status.Code.FAILED_PRECONDITION,
                error(grpc::crearSesion, reserva(tutor, usuario(), MIERCOLES_0810)));
        assertEquals(Status.Code.PERMISSION_DENIED, error(grpc::confirmarSesion, cambio(sesion.getId(), estudiante)));
        assertEquals(Status.Code.FAILED_PRECONDITION, error(grpc::finalizarSesion, cambio(sesion.getId(), tutor)));
        assertEquals(Status.Code.ALREADY_EXISTS, error(grpc::marcarCasilla, MarcarCasillaRequest.newBuilder()
                .setTutorId(tutor).setRamoCodigo("FIS1513").setInicio(ts(MIERCOLES_0810)).build()));
        assertEquals(Status.Code.UNAVAILABLE, error(grpc::marcarCasilla, MarcarCasillaRequest.newBuilder()
                .setTutorId(tutor).setRamoCodigo(ContenedoresDePrueba.RAMO_USUARIOS_CAIDO)
                .setInicio(ts(MIERCOLES_0955)).build()));
        assertEquals(EstadoSesion.CANCELADA, ok(grpc::cancelarSesion, cambio(sesion.getId(), estudiante)).getEstado());
    }
}
