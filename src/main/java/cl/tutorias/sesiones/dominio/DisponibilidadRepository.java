package cl.tutorias.sesiones.dominio;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisponibilidadRepository extends JpaRepository<BloqueDisponibilidad, Long> {

    List<BloqueDisponibilidad> findByTutorIdOrderByDiaAscHoraIniAsc(String tutorId);
}
