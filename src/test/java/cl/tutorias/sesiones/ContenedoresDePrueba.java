package cl.tutorias.sesiones;

import cl.tutorias.sesiones.dominio.SesionException;
import cl.tutorias.sesiones.dominio.SesionException.Motivo;
import cl.tutorias.sesiones.dominio.ValidadorTutorRamo;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Postgres y Kafka reales en contenedores, conectados al contexto de Spring con {@code @ServiceConnection},
 * y un Usuarios de prueba en lugar del mock.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContenedoresDePrueba {

    /** Ramo que el Usuarios de prueba responde como no activo. */
    public static final String RAMO_INACTIVO = "INACTIVO";

    /** Ramo para el que el Usuarios de prueba simula estar caído. */
    public static final String RAMO_USUARIOS_CAIDO = "CAIDO";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        // Sin autocreación, como puede estar el broker del clúster: si Sesiones no creara sus tópicos
        // al arrancar, los tests que publican eventos fallarían.
        return new KafkaContainer("apache/kafka:4.1.0").withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
    }

    @Bean
    @Primary
    ValidadorTutorRamo usuariosDePrueba() {
        return (tutorId, ramoCodigo) -> switch (ramoCodigo) {
            case RAMO_INACTIVO -> false;
            case RAMO_USUARIOS_CAIDO -> throw new SesionException(Motivo.USUARIOS_NO_DISPONIBLE, "Usuarios no responde");
            default -> true;
        };
    }
}
