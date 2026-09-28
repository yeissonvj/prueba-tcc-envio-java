package co.tcc.eventos.infraestructura.notificaciones;

public class OpcionesProveedorSimulado {

    /** Simula una caída total del proveedor (HTTP 503). */
    private boolean caido;

    /** Fracción de envíos que fallan al azar (0 a 1), como timeouts intermitentes. */
    private double tasaFallas;

    private int latenciaMs;

    public OpcionesProveedorSimulado() {
        this(30);
    }

    public OpcionesProveedorSimulado(int latenciaMs) {
        this.latenciaMs = latenciaMs;
    }

    public boolean isCaido() { return caido; }
    public void setCaido(boolean caido) { this.caido = caido; }
    public double getTasaFallas() { return tasaFallas; }
    public void setTasaFallas(double tasa) { this.tasaFallas = tasa; }
    public int getLatenciaMs() { return latenciaMs; }
    public void setLatenciaMs(int ms) { this.latenciaMs = ms; }
}
