package cl.tutorias.sesiones.dominio;

/** Una casilla y si está libre (sin una sesión PENDIENTE o CONFIRMADA encima). */
public record CasillaConEstado(Casilla casilla, boolean libre) {
}
