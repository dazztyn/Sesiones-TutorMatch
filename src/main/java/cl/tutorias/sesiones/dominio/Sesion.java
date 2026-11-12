package cl.tutorias.sesiones.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Una tutoría reservada. La columna {@code horario} de la tabla no se mapea: la calcula
 * la base de datos a partir de {@code inicio} y {@code fin}.
 */
@Entity
@Table(name = "sesiones")
public class Sesion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String tutorId;
    private String estudianteId;
    private String ramoCodigo;
    private Instant inicio;
    private Instant fin;

    @Enumerated(EnumType.STRING)
    private EstadoSesion estado = EstadoSesion.PENDIENTE;

    // Postgres guarda microsegundos; se trunca para que lo devuelto al crear sea lo guardado.
    private Instant creadaEn = Instant.now().truncatedTo(ChronoUnit.MICROS);

    /** Constructor que exige JPA. */
    protected Sesion() {
    }

    public Sesion(String tutorId, String estudianteId, String ramoCodigo, Instant inicio, Instant fin) {
        this.tutorId = tutorId;
        this.estudianteId = estudianteId;
        this.ramoCodigo = ramoCodigo;
        this.inicio = inicio;
        this.fin = fin;
    }

    public boolean participa(String usuarioId) {
        return tutorId.equals(usuarioId) || estudianteId.equals(usuarioId);
    }

    public void setEstado(EstadoSesion estado) {
        this.estado = estado;
    }

    public UUID getId() {
        return id;
    }

    public String getTutorId() {
        return tutorId;
    }

    public String getEstudianteId() {
        return estudianteId;
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

    public EstadoSesion getEstado() {
        return estado;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }
}
