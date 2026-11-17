package cl.tutorias.sesiones.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CasillaRepository extends JpaRepository<Casilla, UUID> {

    /** Casillas de un tutor que empiezan en [desde, hasta). */
    List<Casilla> findByTutorIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicio(
            String tutorId, Instant desde, Instant hasta);

    /** Casillas de un ramo, de cualquier tutor, que empiezan en [desde, hasta). */
    List<Casilla> findByRamoCodigoAndInicioGreaterThanEqualAndInicioLessThanOrderByInicio(
            String ramoCodigo, Instant desde, Instant hasta);

    Optional<Casilla> findByTutorIdAndRamoCodigoAndInicio(String tutorId, String ramoCodigo, Instant inicio);
}
