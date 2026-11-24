package cl.tutorias.sesiones.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Crea al arrancar los tópicos donde Sesiones publica sus eventos. Material se suscribe sin crearlos
 * y el broker del clúster puede tener la autocreación desactivada, así que los declara Sesiones.
 * Si un tópico ya existe, KafkaAdmin lo deja como está.
 */
@Configuration(proxyBeanMethods = false)
class TopicosKafka {

    /** Una partición conserva el orden de los eventos; réplica 1 porque hay un solo broker. */
    @Bean
    KafkaAdmin.NewTopics topicosDeSesiones() {
        return new KafkaAdmin.NewTopics(SesionesKafkaPublisher.TOPICOS.values().stream()
                .map(nombre -> TopicBuilder.name(nombre).partitions(1).replicas(1).build())
                .toArray(NewTopic[]::new));
    }
}
