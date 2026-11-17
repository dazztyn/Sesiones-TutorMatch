package cl.tutorias.sesiones.dominio;

/** Error de negocio. El adaptador gRPC traduce cada motivo a un código de estado. */
public class SesionException extends RuntimeException {

    public enum Motivo {
        DATOS_INVALIDOS,
        NO_ENCONTRADA,
        CHOQUE_DE_HORARIO,
        TRANSICION_INVALIDA,
        SIN_PERMISO,
        /** El tutor no tiene el ramo activo, no hay casilla libre, o la casilla tiene una sesión activa. */
        REGLA_INCUMPLIDA
    }

    private final Motivo motivo;

    public SesionException(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
