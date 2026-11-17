package cl.tutorias.sesiones;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Base de los tests de integración: todos comparten el mismo contexto y los mismos contenedores.
 *
 * <p>Los datos no se borran entre tests: cada test usa usuarios nuevos ({@link #usuario()}) y fechas
 * de 2030, así no se cruzan ni dependen de la hora actual. Enero de 2030 queda reservado para el
 * test de vencimiento, porque {@code vencer} cambia todas las sesiones anteriores a "ahora".
 */
@SpringBootTest(properties = {
        // Puerto libre al azar, para no chocar con un servicio levantado en local.
        "spring.grpc.server.port=0",
        "sesiones.vencimiento.intervalo=PT1H"})
@Import(ContenedoresDePrueba.class)
public abstract class PruebaDeIntegracion {

    private static final ZoneId CHILE = ZoneId.of("America/Santiago");

    /** Inicio de un bloque de clase en 2030, en hora de Chile. */
    protected static Instant bloque(int mes, int dia, int hora, int minuto) {
        return ZonedDateTime.of(2030, mes, dia, hora, minuto, 0, 0, CHILE).toInstant();
    }

    protected static Instant finDe(Instant inicio) {
        return inicio.plusSeconds(90 * 60);
    }

    protected static String usuario() {
        return UUID.randomUUID().toString();
    }
}
