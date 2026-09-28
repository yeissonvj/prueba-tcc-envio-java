package co.tcc.eventos.procesador.consumo;

public class OpcionesConsumidor {

    private String grupoConsumo = "procesador-estado";

    /** Hilos del contenedor: uno por partición asignada (hasta 24). Cada hilo es dueño de sus particiones. */
    private int hilos = 24;

    /**
     * Un error que no es de infraestructura (p. ej. un bug) se reintenta pocas veces y va a la DLQ,
     * para que no congele la partición para siempre.
     */
    private int intentosErrorInesperado = 3;

    /** Backoff exponencial de los reintentos bloqueantes: 200 ms, 400 ms, 800 ms... hasta 30 s. */
    private int esperaMinimaMs = 200;
    private int esperaMaximaMs = 30_000;

    public String getGrupoConsumo() { return grupoConsumo; }
    public void setGrupoConsumo(String grupo) { this.grupoConsumo = grupo; }
    public int getHilos() { return hilos; }
    public void setHilos(int hilos) { this.hilos = hilos; }
    public int getIntentosErrorInesperado() { return intentosErrorInesperado; }
    public void setIntentosErrorInesperado(int intentos) { this.intentosErrorInesperado = intentos; }
    public int getEsperaMinimaMs() { return esperaMinimaMs; }
    public void setEsperaMinimaMs(int ms) { this.esperaMinimaMs = ms; }
    public int getEsperaMaximaMs() { return esperaMaximaMs; }
    public void setEsperaMaximaMs(int ms) { this.esperaMaximaMs = ms; }
}
