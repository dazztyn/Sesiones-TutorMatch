package cl.tutorias.sesiones.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Casilla de disponibilidad: un bloque de clase en que un tutor puede atender un ramo.
 * La columna {@code horario} de la tabla no se mapea: la calcula la base de datos.
 */
@Entity
@Table(name = "disponibilidad")
public class Casilla {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String tutorId;
    private String ramoCodigo;
    private Instant inicio;
    private Instant fin;

    /** Constructor que exige JPA. */
    protected Casilla() {
    }

    public Casilla(String tutorId, String ramoCodigo, Instant inicio) {
        this.tutorId = tutorId;
        this.ramoCodigo = ramoCodigo;
        this.inicio = inicio;
        this.fin = inicio.plus(Bloques.DURACION);
    }

    /** Si una sesión activa del tutor se cruza con esta casilla. */
    public boolean ocupadaPor(Sesion s) {
        return s.getTutorId().equals(tutorId) && s.getInicio().isBefore(fin) && s.getFin().isAfter(inicio);
    }

    public UUID getId() {
        return id;
    }

    public String getTutorId() {
        return tutorId;
    }

    public String getRamoCodigo() {
        return ramoCodigo;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFin() {
        return fin;
    }
}
