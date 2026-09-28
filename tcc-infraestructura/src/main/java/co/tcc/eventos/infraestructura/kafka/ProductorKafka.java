package co.tcc.eventos.infraestructura.kafka;

import co.tcc.eventos.aplicacion.puertos.PublicacionFallidaException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Único lugar con la configuración de durabilidad del productor. Todo lo que publica en Kafka
 * (ingesta, outbox, DLQ, reintentos) pasa por aquí, así nadie publica con garantías distintas por accidente.
 * Es thread-safe y costoso: se registra como singleton.
 * La traza (traceparent) la agrega el agente de OpenTelemetry a los encabezados de cada mensaje.
 */
public final class ProductorKafka implements AutoCloseable {

    private static final int LINGER_MS = 5;

    private final Producer<String, String> productor;
    private final long esperaMaximaMs;

    public ProductorKafka(OpcionesKafka opciones) {
        var propiedades = opciones.propiedadesConexion();
        var entrega = opciones.getTiempoMaximoEntregaMs();
        propiedades.put(ProducerConfig.ACKS_CONFIG, "all");                              // todas las réplicas en sincronía (≥ 2)
        propiedades.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);                 // reintentos sin duplicar ni desordenar
        propiedades.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);        // máximo compatible con idempotencia
        propiedades.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, entrega);             // tope total por mensaje
        propiedades.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, entrega - LINGER_MS);  // Kafka exige delivery ≥ linger + request
        // Sin esto, send() puede bloquear 60 s esperando metadatos cuando Kafka está caído.
        propiedades.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, entrega);
        propiedades.put(ProducerConfig.LINGER_MS_CONFIG, LINGER_MS);                     // lotes de hasta 5 ms
        propiedades.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        propiedades.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        propiedades.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        this.productor = new KafkaProducer<>(propiedades);
        this.esperaMaximaMs = entrega + 1000L;
    }

    /** Solo termina con éxito si Kafka confirmó el mensaje con acks=all. */
    public void publicar(String topico, String clave, String valor, Map<String, String> encabezados) {
        esperar(enviar(topico, clave, valor, encabezados), topico, clave);
    }

    /**
     * Inicia el envío sin esperar. Para lotes: se inician en orden y se esperan después; el productor
     * idempotente conserva el orden por partición.
     */
    public Future<RecordMetadata> enviar(String topico, String clave, String valor, Map<String, String> encabezados) {
        var registro = new ProducerRecord<>(topico, clave, valor);
        encabezados.forEach((nombre, contenido) ->
                registro.headers().add(nombre, contenido.getBytes(StandardCharsets.UTF_8)));
        try {
            return productor.send(registro);
        } catch (KafkaException ex) {
            // Metadatos no disponibles (Kafka caído) o mensaje inválido: send falla antes de enviar.
            throw new PublicacionFallidaException("No se pudo publicar en " + topico + " con clave " + clave + ": " + ex.getMessage(), ex);
        }
    }

    /** Espera la confirmación de un envío iniciado con {@link #enviar}. */
    public void esperar(Future<RecordMetadata> envio, String topico, String clave) {
        try {
            envio.get(esperaMaximaMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
            throw new PublicacionFallidaException(
                    "Kafka no confirmó el mensaje de " + topico + " con clave " + clave + ": " + ex.getCause().getMessage(), ex.getCause());
        } catch (TimeoutException ex) {
            throw new PublicacionFallidaException("Kafka no confirmó a tiempo el mensaje de " + topico + " con clave " + clave + ".", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PublicacionFallidaException("Publicación interrumpida en " + topico + ".", ex);
        }
    }

    @Override
    public void close() {
        productor.close(Duration.ofSeconds(10));
    }
}
