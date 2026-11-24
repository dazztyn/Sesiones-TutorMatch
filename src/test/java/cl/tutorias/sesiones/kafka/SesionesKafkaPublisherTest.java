package cl.tutorias.sesiones.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import cl.tutorias.sesiones.PruebaDeIntegracion;
import cl.tutorias.sesiones.dominio.EstadoSesion;
import cl.tutorias.sesiones.dominio.Sesion;
import cl.tutorias.sesiones.dominio.SesionesService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Los eventos llegan a Kafka con el formato del contrato, y solo si la transacción se confirma. */
class SesionesKafkaPublisherTest extends PruebaDeIntegracion {

    private static final String RAMO = "MAT1610";

    // Jueves 7 de marzo de 2030, 08:10 y 09:55 en Chile.
    private static final Instant JUEVES_0810 = bloque(3, 7, 8, 10);
    private static final Instant JUEVES_0955 = bloque(3, 7, 9, 55);

    @Autowired
    private SesionesService servicio;

    @Autowired
    private TransactionTemplate transaccion;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper json;

    @Test
    void losTresTopicosExistenAlArrancarConUnaParticion() throws Exception {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            Map<String, TopicDescription> topicos = admin
                    .describeTopics(List.of("sesion.creada", "sesion.finalizada", "sesion.cancelada"))
                    .allTopicNames().get();
            assertEquals(3, topicos.size());
            topicos.values().forEach(t -> assertEquals(1, t.partitions().size(), t.name()));
        }
    }

    @Test
    void publicaDespuesDelCommitYNadaSiHayRollback() {
        String tutor = usuario();
        String estudiante = usuario();
        servicio.marcarCasilla(tutor, RAMO, JUEVES_0810);
        servicio.marcarCasilla(tutor, RAMO, JUEVES_0955);

        // Una reserva cuya transacción se revierte no debe anunciarse.
        String revertida = transaccion.execute(estado -> {
            Sesion s = servicio.crear(tutor, estudiante, RAMO, JUEVES_0955, finDe(JUEVES_0955));
            estado.setRollbackOnly();
            return s.getId().toString();
        });

        Sesion sesion = servicio.crear(tutor, estudiante, RAMO, JUEVES_0810, finDe(JUEVES_0810));
        String id = sesion.getId().toString();
        servicio.cambiarEstado(id, estudiante, EstadoSesion.CANCELADA);

        Map<String, List<String>> porTopico = leerHasta("sesion.creada:" + id, "sesion.cancelada:" + id);
        List<String> creadas = porTopico.getOrDefault("sesion.creada:" + id, List.of());
        assertEquals(1, creadas.size());
        assertFalse(porTopico.containsKey("sesion.creada:" + revertida));

        JsonNode evento = json.readTree(creadas.get(0));
        assertEquals("sesion.creada", evento.get("type").asString());
        UUID.fromString(evento.get("eventId").asString());
        Instant.parse(evento.get("occurredAt").asString());
        JsonNode payload = evento.get("payload");
        assertEquals(id, payload.get("sesionId").asString());
        assertEquals(tutor, payload.get("tutorId").asString());
        assertEquals(estudiante, payload.get("estudianteId").asString());
        assertEquals(RAMO, payload.get("ramoCodigo").asString());
        assertEquals(JUEVES_0810, Instant.parse(payload.get("inicio").asString()));
        assertEquals(finDe(JUEVES_0810), Instant.parse(payload.get("fin").asString()));

        String cancelada = porTopico.get("sesion.cancelada:" + id).get(0);
        assertEquals("sesion.cancelada", json.readTree(cancelada).get("type").asString());
    }

    /**
     * Lee los tópicos de sesiones desde el principio hasta ver todos los eventos esperados, como
     * "tópico:clave" (o 30 s). Devuelve los valores agrupados por "tópico:clave".
     */
    private Map<String, List<String>> leerHasta(String... esperados) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Map<String, List<String>> leidos = new HashMap<>();
        try (var consumidor = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumidor.subscribe(List.of("sesion.creada", "sesion.cancelada"));
            Instant limite = Instant.now().plusSeconds(30);
            while (!leidos.keySet().containsAll(List.of(esperados)) && Instant.now().isBefore(limite)) {
                consumidor.poll(Duration.ofMillis(500)).forEach(r ->
                        leidos.computeIfAbsent(r.topic() + ":" + r.key(), k -> new ArrayList<>()).add(r.value()));
            }
        }
        return leidos;
    }
}
