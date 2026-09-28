package co.tcc.eventos.procesador.consumo;

import co.tcc.eventos.infraestructura.kafka.MensajeKafka;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;

/**
 * Lee guias.eventos.recibidos. Cada hilo del contenedor es dueño de sus particiones: se conserva el orden
 * por guía (la guía es la clave) y las particiones avanzan en paralelo (ADR-0011).
 * El offset se confirma DESPUÉS del commit en la base: si el proceso cae, se relee y el inbox descarta.
 */
public final class ConsumidorEventosRecibidos {

    private final ManejadorMensajeRecibido manejador;

    public ConsumidorEventosRecibidos(ManejadorMensajeRecibido manejador) {
        this.manejador = manejador;
    }

    @KafkaListener(
            id = "procesador-estado",
            topics = "${kafka.topico-eventos-recibidos:guias.eventos.recibidos}",
            groupId = "${consumidor.grupo-consumo:procesador-estado}",
            concurrency = "${consumidor.hilos:24}")
    public void procesar(ConsumerRecord<String, String> registro, Acknowledgment confirmacion) {
        manejador.manejar(MensajeKafka.de(registro));
        confirmacion.acknowledge();
    }
}
