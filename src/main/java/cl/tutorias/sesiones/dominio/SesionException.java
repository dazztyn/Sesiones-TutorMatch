package cl.tutorias.sesiones.dominio;

/** Error de negocio. El adaptador gRPC traduce cada motivo a un código de estado. */
public class SesionException extends RuntimeException {

    public enum Motivo {
        DATOS_INVALIDOS,
        NO_ENCONTRADA,
        CHOQUE_DE_HORARIO,
        TRANSICION_INVALIDA,
        SIN_PERMISO
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
