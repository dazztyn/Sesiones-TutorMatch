package cl.tutorias.sesiones.dominio;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SesionRepository extends JpaRepository<Sesion, UUID> {

    /** Un filtro vacío no filtra. */
    @Query("""
            select s from Sesion s
            where (:tutorId = '' or s.tutorId = :tutorId)
              and (:estudianteId = '' or s.estudianteId = :estudianteId)
            order by s.inicio
            """)
    List<Sesion> buscar(String tutorId, String estudianteId);
}
