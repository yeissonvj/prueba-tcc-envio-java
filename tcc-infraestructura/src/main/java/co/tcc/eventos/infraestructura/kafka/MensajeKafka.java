package co.tcc.eventos.infraestructura.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.record.TimestampType;

/**
 * Lo que un manejador necesita de un mensaje, sin depender de los tipos de Kafka:
 * así las políticas de reintento y DLQ se prueban sin broker.
 *
 * @param marca cuándo Kafka recibió el mensaje (base para medir latencias); puede ser null
 */
public record MensajeKafka(
        String topico,
        int particion,
        long offset,
        String clave,
        String valor,
        Map<String, String> encabezados,
        OffsetDateTime marca) {

    public MensajeKafka {
        encabezados = encabezados == null ? Map.of() : Map.copyOf(encabezados);
    }

    public MensajeKafka(String topico, int particion, long offset, String clave, String valor) {
        this(topico, particion, offset, clave, valor, Map.of(), null);
    }

    public String encabezado(String nombre) {
        return encabezados.get(nombre);
    }

    public static MensajeKafka de(ConsumerRecord<String, String> registro) {
        var encabezados = new HashMap<String, String>();
        for (var encabezado : registro.headers())
            if (encabezado.value() != null)
                encabezados.put(encabezado.key(), new String(encabezado.value(), StandardCharsets.UTF_8));

        var marca = registro.timestampType() == TimestampType.NO_TIMESTAMP_TYPE || registro.timestamp() < 0
                ? null
                : OffsetDateTime.ofInstant(Instant.ofEpochMilli(registro.timestamp()), ZoneOffset.UTC);

        return new MensajeKafka(registro.topic(), registro.partition(), registro.offset(),
                registro.key(), registro.value(), encabezados, marca);
    }
}
