package co.tcc.eventos.api.salud;

/** Mismos estados y textos que los health checks de la versión .NET. */
public enum EstadoSalud {
    HEALTHY("Healthy"),
    DEGRADED("Degraded"),
    UNHEALTHY("Unhealthy");

    private final String texto;

    EstadoSalud(String texto) {
        this.texto = texto;
    }

    public String texto() {
        return texto;
    }

    public EstadoSalud peor(EstadoSalud otro) {
        return ordinal() >= otro.ordinal() ? this : otro;
    }
}
