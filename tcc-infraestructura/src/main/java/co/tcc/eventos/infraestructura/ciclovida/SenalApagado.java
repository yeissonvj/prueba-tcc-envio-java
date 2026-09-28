package co.tcc.eventos.infraestructura.ciclovida;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.context.SmartLifecycle;

/**
 * Avisa a los reintentos bloqueantes que el servicio se está deteniendo (equivalente al CancellationToken
 * de .NET). Se detiene ANTES que los contenedores de Kafka (fase mayor): así un reintento de minutos contra
 * una base caída se interrumpe de inmediato, sin marcar el mensaje, y la partición se libera ordenadamente.
 */
public final class SenalApagado implements SmartLifecycle {

    private final CountDownLatch apagado = new CountDownLatch(1);
    private volatile boolean corriendo;

    /** Espera {@code tiempo} o hasta que empiece el apagado, lo que ocurra primero. */
    public void esperar(Duration tiempo) {
        try {
            if (apagado.await(tiempo.toMillis(), TimeUnit.MILLISECONDS))
                throw new ServicioDeteniendoseException();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServicioDeteniendoseException();
        }
    }

    public boolean deteniendose() {
        return apagado.getCount() == 0;
    }

    /** Para las pruebas y para el apagado del contenedor. */
    public void apagar() {
        apagado.countDown();
    }

    @Override
    public void start() {
        corriendo = true;
    }

    @Override
    public void stop() {
        apagar();
        corriendo = false;
    }

    @Override
    public boolean isRunning() {
        return corriendo;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE; // se detiene primero (los contenedores de Kafka usan MAX_VALUE - 100)
    }

    /** El mensaje en curso NO se marca: el siguiente dueño de la partición lo relee (el inbox deduplica). */
    public static final class ServicioDeteniendoseException extends RuntimeException {
        public ServicioDeteniendoseException() {
            super("El servicio se está deteniendo; el mensaje en curso se volverá a leer.");
        }
    }
}
