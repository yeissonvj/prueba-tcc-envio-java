package co.tcc.eventos.infraestructura.kafka;

import co.tcc.eventos.aplicacion.puertos.PublicacionFallidaException;
import co.tcc.eventos.aplicacion.puertos.PublicadorEventos;
import co.tcc.eventos.dominio.EventoGuia;
import co.tcc.eventos.infraestructura.resiliencia.Circuitos;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Clock;

/**
 * Patrón Decorador + Circuit Breaker: si el broker falla de forma sostenida, durante un tiempo
 * se falla al instante en vez de esperar el timeout de entrega en cada petición.
 */
public final class PublicadorConCircuito implements PublicadorEventos {

    private final PublicadorEventos interno;
    private final CircuitBreaker circuito;

    public PublicadorConCircuito(PublicadorEventos interno, OpcionesKafka opciones) {
        this(interno, opciones, Clock.systemUTC());
    }

    public PublicadorConCircuito(PublicadorEventos interno, OpcionesKafka opciones, Clock reloj) {
        this.interno = interno;
        this.circuito = Circuitos.crear(
                "kafka",
                "Circuito hacia Kafka",
                "los eventos van a contingencia",
                PublicacionFallidaException.class,
                opciones.getCircuitoMinimoEnvios(),
                opciones.getCircuitoVentanaSegundos(),
                opciones.getCircuitoSegundosAbierto(),
                reloj);
    }

    @Override
    public void publicar(EventoGuia evento) {
        try {
            circuito.executeRunnable(() -> interno.publicar(evento));
        } catch (CallNotPermittedException ex) {
            throw new PublicacionFallidaException("Circuito hacia Kafka abierto; no se intentó publicar.", ex);
        }
    }

    public CircuitBreaker.State estado() {
        return circuito.getState();
    }
}
