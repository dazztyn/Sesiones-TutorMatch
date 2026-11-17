package cl.tutorias.sesiones.dominio;

import cl.tutorias.sesiones.dominio.SesionException.Motivo;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reglas de negocio de las sesiones y de la disponibilidad de los tutores. No sabe nada de gRPC. */
@Service
@Transactional
public class SesionesService {

    /** SQLSTATE de Postgres cuando se viola una restricción EXCLUDE. */
    private static final String VIOLACION_DE_EXCLUSION = "23P01";

    /** Estados que ocupan un horario: bloquean la casilla y cuentan para los cruces. */
    private static final Set<EstadoSesion> ACTIVAS = EnumSet.of(EstadoSesion.PENDIENTE, EstadoSesion.CONFIRMADA);

    private final SesionRepository sesiones;
    private final CasillaRepository casillas;
    private final ValidadorTutorRamo usuarios;
    private final ApplicationEventPublisher eventos;

    public SesionesService(SesionRepository sesiones, CasillaRepository casillas, ValidadorTutorRamo usuarios,
            ApplicationEventPublisher eventos) {
        this.sesiones = sesiones;
        this.casillas = casillas;
        this.usuarios = usuarios;
        this.eventos = eventos;
    }

    // ---------------------------------------------------------------- Sesiones

    /** Reserva: el alumno toma una casilla libre de ese tutor, ese ramo y ese horario exacto. */
    public Sesion crear(String tutorId, String estudianteId, String ramoCodigo, Instant inicio, Instant fin) {
        if (tutorId.isBlank() || estudianteId.isBlank() || ramoCodigo.isBlank()) {
            throw new SesionException(Motivo.DATOS_INVALIDOS, "Faltan el tutor, el estudiante o el ramo");
        }
        if (tutorId.equals(estudianteId)) {
            throw new SesionException(Motivo.DATOS_INVALIDOS, "Nadie puede reservarse a sí mismo");
        }
        if (!inicio.isAfter(Instant.now())) {
            throw new SesionException(Motivo.DATOS_INVALIDOS, "El horario ya pasó");
        }
        Casilla casilla = casillas.findByTutorIdAndRamoCodigoAndInicio(tutorId, ramoCodigo, inicio)
                .filter(c -> c.getFin().equals(fin))
                .orElseThrow(() -> new SesionException(Motivo.REGLA_INCUMPLIDA,
                        "El tutor no tiene una casilla de ese ramo en ese horario"));
        if (!sesiones.deTutoresEnRango(List.of(tutorId), ACTIVAS, inicio, fin).isEmpty()) {
            throw new SesionException(Motivo.REGLA_INCUMPLIDA, "La casilla ya está reservada");
        }
        exigirRamoActivo(tutorId, ramoCodigo);
        // Cubre el cruce entre papeles, que las restricciones EXCLUDE de la tabla no ven.
        // shortcut: dos reservas simultáneas pueden colarse entre esta consulta y el INSERT; usar
        // un bloqueo por usuario si llega a pasar.
        if (sesiones.contarCruces(List.of(tutorId, estudianteId), ACTIVAS, casilla.getInicio(), casilla.getFin()) > 0) {
            throw choque();
        }
        // El flush fuerza el INSERT ahora, para que una restricción violada se detecte aquí.
        Sesion sesion = guardando(
                () -> sesiones.saveAndFlush(new Sesion(tutorId, estudianteId, ramoCodigo, inicio, fin)),
                SesionesService::choque);
        eventos.publishEvent(new SesionCambiada(sesion));
        return sesion;
    }

    @Transactional(readOnly = true)
    public Sesion obtener(String id) {
        return sesiones.findById(uuid(id, "sesión")).orElseThrow(() -> noEncontrada("sesión", id));
    }

    /** Un texto vacío no filtra; estado null son todos; desde y hasta null no acotan. */
    @Transactional(readOnly = true)
    public List<Sesion> listar(String tutorId, String estudianteId, EstadoSesion estado, Instant desde,
            Instant hasta) {
        return sesiones.buscar(tutorId, estudianteId,
                estado == null ? EnumSet.allOf(EstadoSesion.class) : EnumSet.of(estado),
                desde == null ? Instant.EPOCH : desde,
                hasta == null ? Instant.parse("9999-12-31T00:00:00Z") : hasta);
    }

    public Sesion cambiarEstado(String id, String usuarioId, EstadoSesion destino) {
        Sesion sesion = obtener(id);
        if (!sesion.participa(usuarioId)) {
            throw new SesionException(Motivo.SIN_PERMISO, "El usuario no participa en la sesión");
        }
        // Si el alumno pudiera finalizar, podría evaluar una sesión que todavía no ocurre.
        if (destino != EstadoSesion.CANCELADA && !sesion.getTutorId().equals(usuarioId)) {
            throw new SesionException(Motivo.SIN_PERMISO, "Solo el tutor puede confirmar o finalizar la sesión");
        }
        if (!sesion.getEstado().puedePasarA(destino)) {
            throw new SesionException(Motivo.TRANSICION_INVALIDA,
                    "Una sesión " + sesion.getEstado() + " no puede pasar a " + destino);
        }
        pasarA(sesion, destino);
        return sesion;
    }

    /**
     * Finaliza las sesiones confirmadas que ya terminaron y cancela las pendientes cuyo inicio
     * pasó sin confirmación. Devuelve cuántas cambiaron.
     */
    public int vencer(Instant ahora) {
        List<Sesion> terminadas = sesiones.findByEstadoAndFinLessThanEqual(EstadoSesion.CONFIRMADA, ahora);
        List<Sesion> sinConfirmar = sesiones.findByEstadoAndInicioLessThanEqual(EstadoSesion.PENDIENTE, ahora);
        terminadas.forEach(s -> pasarA(s, EstadoSesion.FINALIZADA));
        sinConfirmar.forEach(s -> pasarA(s, EstadoSesion.CANCELADA));
        return terminadas.size() + sinConfirmar.size();
    }

    // ------------------------------------------------------------ Disponibilidad

    public Casilla marcarCasilla(String tutorId, String ramoCodigo, Instant inicio) {
        if (tutorId.isBlank() || ramoCodigo.isBlank()) {
            throw new SesionException(Motivo.DATOS_INVALIDOS, "Faltan el tutor o el ramo");
        }
        if (!Bloques.esInicioDeBloque(inicio)) {
            throw new SesionException(Motivo.DATOS_INVALIDOS,
                    "El horario no es el inicio de un bloque de clase de lunes a viernes");
        }
        if (!inicio.isAfter(Instant.now())) {
            throw new SesionException(Motivo.DATOS_INVALIDOS, "El horario ya pasó");
        }
        exigirRamoActivo(tutorId, ramoCodigo);
        return guardando(() -> casillas.saveAndFlush(new Casilla(tutorId, ramoCodigo, inicio)),
                () -> new SesionException(Motivo.CHOQUE_DE_HORARIO,
                        "El tutor ya tiene una casilla que se solapa con ese horario"));
    }

    public void desmarcarCasilla(String id, String tutorId) {
        Casilla casilla = casillas.findById(uuid(id, "casilla")).orElseThrow(() -> noEncontrada("casilla", id));
        if (!casilla.getTutorId().equals(tutorId)) {
            throw new SesionException(Motivo.SIN_PERMISO, "La casilla no es del tutor");
        }
        if (!sesiones.deTutoresEnRango(List.of(tutorId), ACTIVAS, casilla.getInicio(), casilla.getFin()).isEmpty()) {
            throw new SesionException(Motivo.REGLA_INCUMPLIDA,
                    "La casilla tiene una sesión pendiente o confirmada; primero hay que cancelarla");
        }
        casillas.delete(casilla);
    }

    /** Casillas de un tutor que empiezan en [desde, hasta), libres u ocupadas. */
    @Transactional(readOnly = true)
    public List<CasillaConEstado> disponibilidad(String tutorId, Instant desde, Instant hasta) {
        validarRango(desde, hasta);
        return conEstado(casillas.findByTutorIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicio(
                tutorId, desde, hasta), desde, hasta);
    }

    /**
     * Casillas libres y futuras de un ramo, de cualquier tutor, que empiezan en [desde, hasta).
     * Omite a los tutores que ya no tienen el ramo activo.
     */
    @Transactional(readOnly = true)
    public List<Casilla> buscarDisponibilidad(String ramoCodigo, Instant desde, Instant hasta) {
        validarRango(desde, hasta);
        Instant ahora = Instant.now();
        List<Casilla> libres = conEstado(casillas.findByRamoCodigoAndInicioGreaterThanEqualAndInicioLessThanOrderByInicio(
                ramoCodigo, desde, hasta), desde, hasta).stream()
                .filter(c -> c.libre() && c.casilla().getInicio().isAfter(ahora))
                .map(CasillaConEstado::casilla)
                .toList();
        // Una consulta a Usuarios por tutor distinto, no por casilla.
        Map<String, Boolean> conRamo = libres.stream().map(Casilla::getTutorId).distinct()
                .collect(Collectors.toMap(Function.identity(), t -> usuarios.tieneRamoActivo(t, ramoCodigo)));
        return libres.stream().filter(c -> conRamo.get(c.getTutorId())).toList();
    }

    // ------------------------------------------------------------------ Apoyo

    private List<CasillaConEstado> conEstado(List<Casilla> lista, Instant desde, Instant hasta) {
        if (lista.isEmpty()) {
            return List.of();
        }
        // Las casillas empiezan en [desde, hasta) y duran un bloque: basta con mirar ese rango ampliado.
        List<Sesion> activas = sesiones.deTutoresEnRango(
                lista.stream().map(Casilla::getTutorId).distinct().toList(), ACTIVAS, desde,
                hasta.plus(Bloques.DURACION));
        return lista.stream()
                .map(c -> new CasillaConEstado(c, activas.stream().noneMatch(c::ocupadaPor)))
                .toList();
    }

    private void pasarA(Sesion sesion, EstadoSesion destino) {
        // Se guarda sola al terminar la transacción (dirty checking de JPA).
        sesion.setEstado(destino);
        eventos.publishEvent(new SesionCambiada(sesion));
    }

    private void exigirRamoActivo(String tutorId, String ramoCodigo) {
        if (!usuarios.tieneRamoActivo(tutorId, ramoCodigo)) {
            throw new SesionException(Motivo.REGLA_INCUMPLIDA, "El tutor no tiene el ramo " + ramoCodigo + " activo");
        }
    }

    /** Ejecuta un guardado y traduce la violación de una restricción EXCLUDE al error indicado. */
    private static <T> T guardando(Supplier<T> guardar, Supplier<SesionException> siChoca) {
        try {
            return guardar.get();
        } catch (DataAccessException e) {
            if (e.getMostSpecificCause() instanceof SQLException sql
                    && VIOLACION_DE_EXCLUSION.equals(sql.getSQLState())) {
                throw siChoca.get();
            }
            throw e;
        }
    }

    private static void validarRango(Instant desde, Instant hasta) {
        if (desde == null || hasta == null || !hasta.isAfter(desde)) {
            throw new SesionException(Motivo.DATOS_INVALIDOS, "Faltan desde y hasta, o hasta no es posterior a desde");
        }
    }

    private static UUID uuid(String id, String que) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            // Un id mal formado no puede corresponder a ningún registro.
            throw noEncontrada(que, id);
        }
    }

    private static SesionException choque() {
        return new SesionException(Motivo.CHOQUE_DE_HORARIO,
                "El tutor o el alumno ya tiene una sesión activa en ese horario");
    }

    private static SesionException noEncontrada(String que, String id) {
        return new SesionException(Motivo.NO_ENCONTRADA, "No existe la " + que + " " + id);
    }
}
