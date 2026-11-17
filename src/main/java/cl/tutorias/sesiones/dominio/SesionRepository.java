package cl.tutorias.sesiones.dominio;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SesionRepository extends JpaRepository<Sesion, UUID> {

    /** Un filtro de texto vacío no filtra; el estado y el rango siempre llegan con valor. */
    @Query("""
            select s from Sesion s
            where (:tutorId = '' or s.tutorId = :tutorId)
              and (:estudianteId = '' or s.estudianteId = :estudianteId)
              and s.estado in :estados
              and s.inicio >= :desde and s.inicio < :hasta
            order by s.inicio
            """)
    List<Sesion> buscar(String tutorId, String estudianteId, Collection<EstadoSesion> estados,
            Instant desde, Instant hasta);

    /** Sesiones en esos estados donde alguno de los tutores dicta, que se cruzan con [desde, hasta). */
    @Query("""
            select s from Sesion s
            where s.tutorId in :tutores and s.estado in :estados
              and s.inicio < :hasta and s.fin > :desde
            """)
    List<Sesion> deTutoresEnRango(Collection<String> tutores, Collection<EstadoSesion> estados,
            Instant desde, Instant hasta);

    /** Sesiones en esos estados de alguno de los usuarios, como tutor o como alumno, que se cruzan con [inicio, fin). */
    @Query("""
            select count(s) from Sesion s
            where s.estado in :estados
              and s.inicio < :fin and s.fin > :inicio
              and (s.tutorId in :usuarios or s.estudianteId in :usuarios)
            """)
    long contarCruces(Collection<String> usuarios, Collection<EstadoSesion> estados, Instant inicio, Instant fin);

    List<Sesion> findByEstadoAndFinLessThanEqual(EstadoSesion estado, Instant limite);

    List<Sesion> findByEstadoAndInicioLessThanEqual(EstadoSesion estado, Instant limite);
}
