package co.tcc.eventos.api.salud;

import java.util.concurrent.Executors;

/** Reglas de "lista" de la API, sin conocer HTTP ni las tecnologías concretas. */
public final class VerificacionesSalud {

    private final Sonda kafka;
    private final Sonda contingencia;
    private final Sonda filtro;

    public VerificacionesSalud(Sonda kafka, Sonda contingencia, Sonda filtro) {
        this.kafka = kafka;
        this.contingencia = contingencia;
        this.filtro = filtro;
    }

    /**
     * La API está lista mientras pueda guardar de forma durable en ALGÚN lado.
     * Kafka caído con contingencia disponible = degradada pero lista (sigue respondiendo 202 con garantía).
     */
    public static EstadoSalud almacenamientoDurable(boolean kafkaDisponible, boolean contingenciaDisponible) {
        if (kafkaDisponible && contingenciaDisponible)
            return EstadoSalud.HEALTHY;
        if (kafkaDisponible || contingenciaDisponible)
            return EstadoSalud.DEGRADED;
        return EstadoSalud.UNHEALTHY;
    }

    /** El filtro es una optimización: si no está, la API funciona igual (degradada), nunca "no lista". */
    public static EstadoSalud filtroDuplicados(boolean redisDisponible) {
        return redisDisponible ? EstadoSalud.HEALTHY : EstadoSalud.DEGRADED;
    }

    /** Las tres sondas en paralelo: la respuesta tarda lo que la más lenta, no la suma. */
    public EstadoSalud lista() {
        try (var hilos = Executors.newVirtualThreadPerTaskExecutor()) {
            var k = hilos.submit(kafka::disponible);
            var c = hilos.submit(contingencia::disponible);
            var f = hilos.submit(filtro::disponible);
            return almacenamientoDurable(k.get(), c.get()).peor(filtroDuplicados(f.get()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return EstadoSalud.UNHEALTHY;
        } catch (Exception ex) {
            return EstadoSalud.UNHEALTHY;
        }
    }
}
