package cl.tutorias.sesiones.dominio;

import static cl.tutorias.sesiones.dominio.EstadoSesion.CANCELADA;
import static cl.tutorias.sesiones.dominio.EstadoSesion.CONFIRMADA;
import static cl.tutorias.sesiones.dominio.EstadoSesion.FINALIZADA;
import static cl.tutorias.sesiones.dominio.EstadoSesion.PENDIENTE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EstadoSesionTest {

    /** Estados a los que puede pasar cada uno; todo lo demás está prohibido. */
    private static Set<EstadoSesion> destinos(EstadoSesion origen) {
        Set<EstadoSesion> permitidos = EnumSet.noneOf(EstadoSesion.class);
        for (EstadoSesion destino : EstadoSesion.values()) {
            if (origen.puedePasarA(destino)) {
                permitidos.add(destino);
            }
        }
        return permitidos;
    }

    @Test
    void cicloDeVida() {
        assertEquals(EnumSet.of(CONFIRMADA, CANCELADA), destinos(PENDIENTE));
        assertEquals(EnumSet.of(FINALIZADA, CANCELADA), destinos(CONFIRMADA));
        assertEquals(EnumSet.noneOf(EstadoSesion.class), destinos(FINALIZADA));
        assertEquals(EnumSet.noneOf(EstadoSesion.class), destinos(CANCELADA));
    }
}
