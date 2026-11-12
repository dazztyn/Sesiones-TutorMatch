package cl.tutorias.sesiones.kafka;

import cl.tutorias.sesiones.dominio.EstadoSesion;
import cl.tutorias.sesiones.dominio.Sesion;
import cl.tutorias.sesiones.dominio.SesionCambiada;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adaptador Kafka: publica los cambios de una sesión para el servicio de Material.
 * Cada evento va al tópico que se llama igual que su {@code type}, con el id de la
 * sesión como clave para que los eventos de una misma sesión lleguen en orden.
 */
@Component
public class SesionesKafkaPublisher {

    /** Tópico según el estado al que llegó la sesión. Confirmar no publica nada. */
    private static final Map<EstadoSesion, String> TOPICOS = Map.of(
            EstadoSesion.PENDIENTE, "sesion.creada",
            EstadoSesion.FINALIZADA, "sesion.finalizada",
            EstadoSesion.CANCELADA, "sesion.cancelada");

    /** Sobre común a todos los mensajes Kafka de la plataforma. */
    record Evento(String eventId, String type, Instant occurredAt, Payload payload) {
    }

    record Payload(String sesionId, String tutorId, String estudianteId, String ramoCodigo,
            Instant inicio, Instant fin) {
    }

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    public SesionesKafkaPublisher(KafkaTemplate<String, String> kafka, JsonMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    // Se ejecuta después del commit: nunca se anuncia una sesión que no quedó guardada.
    // shortcut: si Kafka no responde el evento se pierde (solo queda el error en el log);
    // pasar a una tabla outbox si Material no puede tolerar eventos perdidos.
    @TransactionalEventListener
    public void publicar(SesionCambiada cambio) {
        Sesion s = cambio.sesion();
        String tipo = TOPICOS.get(s.getEstado());
        if (tipo == null) {
            return;
        }
        var evento = new Evento(UUID.randomUUID().toString(), tipo, Instant.now(),
                new Payload(s.getId().toString(), s.getTutorId(), s.getEstudianteId(),
                        s.getRamoCodigo(), s.getInicio(), s.getFin()));
        kafka.send(tipo, s.getId().toString(), json.writeValueAsString(evento));
    }
}
