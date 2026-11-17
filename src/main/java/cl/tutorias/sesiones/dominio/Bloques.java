package cl.tutorias.sesiones.dominio;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Bloques de clase de la universidad: de lunes a viernes, 90 minutos, con horario fijo en
 * hora de Chile. Los contratos usan UTC; aquí se convierte solo para validar.
 */
public final class Bloques {

    public static final Duration DURACION = Duration.ofMinutes(90);

    // America/Santiago considera el cambio de horario de verano.
    private static final ZoneId CHILE = ZoneId.of("America/Santiago");

    /** Inicio de los bloques A, B, C, D, E y F (entre C y D está el almuerzo). */
    private static final Set<LocalTime> INICIOS = Set.of(
            LocalTime.of(8, 10), LocalTime.of(9, 55), LocalTime.of(11, 40),
            LocalTime.of(14, 30), LocalTime.of(16, 15), LocalTime.of(18, 0));

    private Bloques() {
    }

    public static boolean esInicioDeBloque(Instant inicio) {
        ZonedDateTime local = inicio.atZone(CHILE);
        return local.getDayOfWeek().getValue() <= 5 && INICIOS.contains(local.toLocalTime());
    }
}
