package cl.tutorias.sesiones.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;

/** Bloque semanal en que un tutor puede atender. Por ahora solo se lee. */
@Entity
@Table(name = "disponibilidad")
public class BloqueDisponibilidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tutorId;
    private short dia;
    private LocalTime horaIni;
    private LocalTime horaFin;

    /** Constructor que exige JPA. */
    protected BloqueDisponibilidad() {
    }

    public String getTutorId() {
        return tutorId;
    }

    public short getDia() {
        return dia;
    }

    public LocalTime getHoraIni() {
        return horaIni;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }
}
