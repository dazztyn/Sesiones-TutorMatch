package cl.tutorias.sesiones.dominio;

import cl.tutorias.sesiones.dominio.SesionException.Motivo;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reglas de negocio de las sesiones. No sabe nada de gRPC. */
@Service
@Transactional
public class SesionesService {

    /** SQLSTATE de Postgres cuando se viola una restricción EXCLUDE. */
    private static final String VIOLACION_DE_EXCLUSION = "23P01";

    private final SesionRepository sesiones;
    private final DisponibilidadRepository disponibilidad;
    private final ApplicationEventPublisher eventos;

    public SesionesService(SesionRepository sesiones, DisponibilidadRepository disponibilidad,
            ApplicationEventPublisher eventos) {
        this.sesiones = sesiones;
        this.disponibilidad = disponibilidad;
        this.eventos = eventos;
    }

    public Sesion crear(String tutorId, String estudianteId, String ramoCodigo, Instant inicio, Instant fin) {
        if (tutorId.isBlank() || estudianteId.isBlank() || ramoCodigo.isBlank()) {
            throw new SesionException(Motivo.DATOS_INVALIDOS, "Faltan el tutor, el estudiante o el ramo");
        }
        if (!fin.isAfter(inicio)) {
            throw new SesionException(Motivo.DATOS_INVALIDOS, "El fin debe ser posterior al inicio");
        }
        try {
            // El flush fuerza el INSERT ahora, para que el choque de horario se detecte aquí.
            Sesion sesion = sesiones.saveAndFlush(new Sesion(tutorId, estudianteId, ramoCodigo, inicio, fin));
            eventos.publishEvent(new SesionCambiada(sesion));
            return sesion;
        } catch (DataAccessException e) {
            if (e.getMostSpecificCause() instanceof SQLException sql
                    && VIOLACION_DE_EXCLUSION.equals(sql.getSQLState())) {
                throw new SesionException(Motivo.CHOQUE_DE_HORARIO, "El tutor ya tiene una sesión en ese horario");
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Sesion obtener(String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            // Un id mal formado no puede corresponder a ninguna sesión.
            throw noEncontrada(id);
        }
        return sesiones.findById(uuid).orElseThrow(() -> noEncontrada(id));
    }

    /** Un filtro vacío no filtra. */
    @Transactional(readOnly = true)
    public List<Sesion> listar(String tutorId, String estudianteId) {
        return sesiones.buscar(tutorId, estudianteId);
    }

    public Sesion cambiarEstado(String id, String usuarioId, EstadoSesion destino) {
        Sesion sesion = obtener(id);
        if (!sesion.participa(usuarioId)) {
            throw new SesionException(Motivo.SIN_PERMISO, "El usuario no participa en la sesión");
        }
        if (!sesion.getEstado().puedePasarA(destino)) {
            throw new SesionException(Motivo.TRANSICION_INVALIDA,
                    "Una sesión " + sesion.getEstado() + " no puede pasar a " + destino);
        }
        // Se guarda sola al terminar la transacción (dirty checking de JPA).
        sesion.setEstado(destino);
        eventos.publishEvent(new SesionCambiada(sesion));
        return sesion;
    }

    @Transactional(readOnly = true)
    public List<BloqueDisponibilidad> disponibilidad(String tutorId) {
        return disponibilidad.findByTutorIdOrderByDiaAscHoraIniAsc(tutorId);
    }

    private static SesionException noEncontrada(String id) {
        return new SesionException(Motivo.NO_ENCONTRADA, "No existe la sesión " + id);
    }
}
