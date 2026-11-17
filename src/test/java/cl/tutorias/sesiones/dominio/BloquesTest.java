package cl.tutorias.sesiones.dominio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BloquesTest {

    @Test
    void enVeranoChileEstaEnUtcMenos3() {
        // Lunes 7 de enero de 2030, 08:10 en Chile.
        assertTrue(Bloques.esInicioDeBloque(Instant.parse("2030-01-07T11:10:00Z")));
        // 08:10 UTC son las 05:10 en Chile: no es un bloque.
        assertFalse(Bloques.esInicioDeBloque(Instant.parse("2030-01-07T08:10:00Z")));
    }

    @Test
    void enInviernoChileEstaEnUtcMenos4() {
        // Lunes 8 de julio de 2030, 08:10 en Chile.
        assertTrue(Bloques.esInicioDeBloque(Instant.parse("2030-07-08T12:10:00Z")));
        assertFalse(Bloques.esInicioDeBloque(Instant.parse("2030-07-08T11:10:00Z")));
    }

    @Test
    void aceptaLosSeisBloquesDelDia() {
        for (String hora : new String[] {"11:10", "12:55", "14:40", "17:30", "19:15", "21:00"}) {
            assertTrue(Bloques.esInicioDeBloque(Instant.parse("2030-01-07T" + hora + ":00Z")), hora);
        }
    }

    @Test
    void rechazaElFinDeSemana() {
        // Sábado 12 de enero de 2030, 08:10 en Chile.
        assertFalse(Bloques.esInicioDeBloque(Instant.parse("2030-01-12T11:10:00Z")));
    }

    @Test
    void rechazaUnMinutoDistinto() {
        assertFalse(Bloques.esInicioDeBloque(Instant.parse("2030-01-07T11:11:00Z")));
        assertFalse(Bloques.esInicioDeBloque(Instant.parse("2030-01-07T11:10:01Z")));
    }
}
