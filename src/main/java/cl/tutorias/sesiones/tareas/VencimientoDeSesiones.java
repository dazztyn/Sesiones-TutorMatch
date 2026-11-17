package cl.tutorias.sesiones.tareas;

import cl.tutorias.sesiones.dominio.SesionesService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea programada: finaliza las sesiones confirmadas que ya terminaron y cancela las pendientes
 * que nadie confirmó a tiempo. Así el alumno siempre puede evaluar y no quedan reservas colgadas.
 */
@Component
public class VencimientoDeSesiones {

    private static final Logger log = LoggerFactory.getLogger(VencimientoDeSesiones.class);

    private final SesionesService servicio;

    public VencimientoDeSesiones(SesionesService servicio) {
        this.servicio = servicio;
    }

    // El intervalo se puede acortar con la propiedad sesiones.vencimiento.intervalo (ej. PT10S) para probar.
    @Scheduled(fixedDelayString = "${sesiones.vencimiento.intervalo:PT5M}")
    public void vencer() {
        int cambiadas = servicio.vencer(Instant.now());
        if (cambiadas > 0) {
            log.info("Vencimiento: {} sesiones cambiaron de estado", cambiadas);
        }
    }
}
