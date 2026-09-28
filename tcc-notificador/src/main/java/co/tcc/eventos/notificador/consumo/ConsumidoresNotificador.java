package co.tcc.eventos.notificador.consumo;

import co.tcc.eventos.infraestructura.kafka.MensajeKafka;
import co.tcc.eventos.infraestructura.kafka.OpcionesKafka;
import co.tcc.eventos.notificador.enrutamiento.EnrutadorNotificacionesKafka;
import co.tcc.eventos.notificador.enrutamiento.ManejadorNotificacion;
import co.tcc.eventos.notificador.enrutamiento.OpcionesNotificador;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

/**
 * Un consumidor para los cambios y uno por etapa de reintento, cada uno con su grupo (los mismos nombres
 * de tópicos y grupos que la versión .NET, para que ambas puedan convivir durante la migración).
 * Se crean aquí porque las etapas vienen de la configuración: una anotación no puede repetirse por cada una.
 */
public final class ConsumidoresNotificador implements SmartLifecycle {

    // Un reintento que aún no vence se devuelve a la cola por pedazos de este tamaño: la partición queda
    // pausada sin bloquear el hilo y el consumidor sigue haciendo poll (no sale del grupo).
    private static final Duration PAUSA_MAXIMA = Duration.ofSeconds(30);

    private final List<ConcurrentMessageListenerContainer<String, String>> contenedores = new ArrayList<>();
    private volatile boolean corriendo;

    public ConsumidoresNotificador(
            ConcurrentKafkaListenerContainerFactory<String, String> fabrica,
            OpcionesKafka kafka,
            OpcionesNotificador opciones,
            ManejadorNotificacion manejador,
            Clock reloj) {

        var cambios = fabrica.createContainer(kafka.getTopicoEstadosCambiados());
        cambios.getContainerProperties().setGroupId(opciones.getGrupoConsumo());
        cambios.setConcurrency(opciones.getHilos());
        cambios.setupMessageListener((AcknowledgingMessageListener<String, String>) (registro, confirmacion) -> {
            manejador.manejarCambio(MensajeKafka.de(registro));
            confirmacion.acknowledge();
        });
        cambios.setBeanName("notificador-cambios");
        contenedores.add(cambios);

        for (var etapa : opciones.getReintentos()) {
            var reintentos = fabrica.createContainer(etapa.getTopico());
            reintentos.getContainerProperties().setGroupId(opciones.getGrupoConsumo() + "-" + etapa.getTopico());
            reintentos.setConcurrency(1); // secuencial: la demora es fija, el primero en cola es el que vence antes
            reintentos.setupMessageListener((AcknowledgingMessageListener<String, String>) (registro, confirmacion) -> {
                var mensaje = MensajeKafka.de(registro);
                var vence = EnrutadorNotificacionesKafka.vencimiento(mensaje);
                if (vence != null) {
                    var restante = Duration.between(reloj.instant(), vence.toInstant());
                    if (restante.isPositive()) {
                        confirmacion.nack(restante.compareTo(PAUSA_MAXIMA) < 0 ? restante : PAUSA_MAXIMA);
                        return;
                    }
                }
                manejador.manejarReintento(mensaje);
                confirmacion.acknowledge();
            });
            reintentos.setBeanName("notificador-" + etapa.getTopico());
            contenedores.add(reintentos);
        }
    }

    @Override
    public void start() {
        contenedores.forEach(contenedor -> contenedor.start());
        corriendo = true;
    }

    @Override
    public void stop() {
        contenedores.forEach(ConcurrentMessageListenerContainer::stop);
        corriendo = false;
    }

    @Override
    public boolean isRunning() {
        return corriendo;
    }

    @Override
    public int getPhase() {
        return AbstractMessageListenerContainer.DEFAULT_PHASE;
    }
}
