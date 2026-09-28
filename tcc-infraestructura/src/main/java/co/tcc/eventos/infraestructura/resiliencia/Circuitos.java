package co.tcc.eventos.infraestructura.resiliencia;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mismos umbrales que Polly en la versión .NET: se abre si en la ventana (en segundos) falla al menos
 * la mitad de un mínimo de llamadas; abierto, falla al instante; luego deja pasar una llamada de prueba.
 */
public final class Circuitos {

    private static final Logger log = LoggerFactory.getLogger(Circuitos.class);

    private Circuitos() {
    }

    public static CircuitBreaker crear(
            String nombre,
            String descripcion,
            String consecuencia,
            Class<? extends Throwable> falla,
            int minimoLlamadas,
            int ventanaSegundos,
            int segundosAbierto,
            Clock reloj) {

        var configuracion = CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.TIME_BASED)
                .slidingWindowSize(ventanaSegundos)
                .minimumNumberOfCalls(minimoLlamadas)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(segundosAbierto))
                .permittedNumberOfCallsInHalfOpenState(1)
                .automaticTransitionFromOpenToHalfOpenEnabled(false)
                .recordExceptions(falla)
                .clock(reloj)
                .build();

        var circuito = CircuitBreaker.of(nombre, configuracion);
        circuito.getEventPublisher().onStateTransition(evento -> {
            switch (evento.getStateTransition().getToState()) {
                case OPEN -> log.error("{} ABIERTO por {} s: {}", descripcion, segundosAbierto, consecuencia);
                case HALF_OPEN -> log.warn("{} SEMIABIERTO: probando con el siguiente envío", descripcion);
                case CLOSED -> log.info("{} CERRADO: operación normal", descripcion);
                default -> log.info("{}: {}", descripcion, evento.getStateTransition());
            }
        });
        return circuito;
    }
}
