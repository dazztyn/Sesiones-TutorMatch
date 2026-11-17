package cl.tutorias.sesiones.dominio;

/**
 * Puerto hacia el servicio de Usuarios ({@code Usuarios.ValidarTutorRamo}): si el usuario
 * existe y tiene ese ramo activo como tutor. El dominio no sabe si la respuesta viene de
 * gRPC o de un mock.
 */
public interface ValidadorTutorRamo {

    boolean tieneRamoActivo(String tutorId, String ramoCodigo);
}
