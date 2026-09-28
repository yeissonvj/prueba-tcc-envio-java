package co.tcc.eventos.procesador.configuracion;

import co.tcc.eventos.aplicacion.casosuso.ProcesarEvento;
import co.tcc.eventos.infraestructura.kafka.OpcionesKafka;
import co.tcc.eventos.infraestructura.kafka.ProductorKafka;
import co.tcc.eventos.infraestructura.postgres.RelayBandejaSalida;
import co.tcc.eventos.infraestructura.postgres.RepositorioGuiasJdbc;
import co.tcc.eventos.procesador.consumo.ConsumidorEventosRecibidos;
import co.tcc.eventos.procesador.consumo.DestinoDlqKafka;
import co.tcc.eventos.procesador.consumo.ManejadorMensajeRecibido;
import co.tcc.eventos.procesador.consumo.OpcionesConsumidor;
import co.tcc.eventos.infraestructura.ciclovida.SenalApagado;
import co.tcc.eventos.procesador.consumo.ServicioRelayBandejaSalida;
import java.time.Clock;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Raíz de composición del procesador de estado: el único lugar donde se decide qué adaptador
 * implementa cada puerto. El dominio y los casos de uso no conocen Spring.
 */
@Configuration(proxyBeanMethods = false)
@EnableKafka
@EnableScheduling
public class ConfiguracionProcesador {

    @Bean
    @ConfigurationProperties("kafka")
    OpcionesKafka opcionesKafka() {
        return new OpcionesKafka();
    }

    @Bean
    @ConfigurationProperties("consumidor")
    OpcionesConsumidor opcionesConsumidor() {
        return new OpcionesConsumidor();
    }

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "close")
    ProductorKafka productorKafka(OpcionesKafka kafka) {
        return new ProductorKafka(kafka);
    }

    @Bean
    SenalApagado senalApagado() {
        return new SenalApagado();
    }

    @Bean
    ManejadorMensajeRecibido manejadorMensajeRecibido(
            JdbcClient jdbc, TransactionTemplate transaccion, ProductorKafka productor, OpcionesKafka kafka,
            OpcionesConsumidor consumidor, SenalApagado apagado, Clock reloj) {
        var procesar = new ProcesarEvento(new RepositorioGuiasJdbc(jdbc, transaccion));
        var dlq = new DestinoDlqKafka(productor, kafka, reloj);
        return new ManejadorMensajeRecibido(procesar, dlq, consumidor, apagado, reloj);
    }

    @Bean
    ConsumidorEventosRecibidos consumidorEventosRecibidos(ManejadorMensajeRecibido manejador) {
        return new ConsumidorEventosRecibidos(manejador);
    }

    @Bean
    ServicioRelayBandejaSalida servicioRelayBandejaSalida(
            JdbcClient jdbc, TransactionTemplate transaccion, ProductorKafka productor, OpcionesKafka kafka) {
        return new ServicioRelayBandejaSalida(new RelayBandejaSalida(jdbc, transaccion, productor, kafka));
    }

    /**
     * Confirmación manual: el offset avanza solo cuando el manejador terminó (procesado o en DLQ).
     * El manejador resuelve todos los errores; si algo escapa (apagado o un error del propio contenedor),
     * el mensaje se vuelve a leer SIN límite de intentos: nunca se salta un evento.
     */
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            OpcionesKafka kafka, OpcionesConsumidor consumidor) {
        var fabrica = new ConcurrentKafkaListenerContainerFactory<String, String>();
        fabrica.setConsumerFactory(new DefaultKafkaConsumerFactory<>(kafka.propiedadesConsumidor(consumidor.getGrupoConsumo())));
        fabrica.getContainerProperties().setAckMode(AckMode.MANUAL);
        fabrica.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS)));
        return fabrica;
    }
}
