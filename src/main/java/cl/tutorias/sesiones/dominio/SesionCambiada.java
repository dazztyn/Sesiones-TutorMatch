package cl.tutorias.sesiones.dominio;

/**
 * Evento interno: una sesión se creó o cambió de estado. Lo publica {@link SesionesService}
 * y lo escucha el adaptador Kafka, así el dominio no sabe nada de Kafka.
 */
public record SesionCambiada(Sesion sesion) {
}
