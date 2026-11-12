package cl.tutorias.sesiones.dominio;

/** Ciclo de vida: PENDIENTE → CONFIRMADA → FINALIZADA, o CANCELADA antes de finalizar. */
public enum EstadoSesion {
    PENDIENTE,
    CONFIRMADA,
    FINALIZADA,
    CANCELADA;

    public boolean puedePasarA(EstadoSesion destino) {
        return switch (destino) {
            case PENDIENTE -> false;
            case CONFIRMADA -> this == PENDIENTE;
            case CANCELADA -> this == PENDIENTE || this == CONFIRMADA;
            case FINALIZADA -> this == CONFIRMADA;
        };
    }
}
