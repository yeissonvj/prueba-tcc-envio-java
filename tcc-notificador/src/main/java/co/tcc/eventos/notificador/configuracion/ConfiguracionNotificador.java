package co.tcc.eventos.notificador.configuracion;

import co.tcc.eventos.aplicacion.casosuso.NotificarCambioEstado;
import co.tcc.eventos.dominio.notificaciones.CanalNotificacion;
import co.tcc.eventos.infraestructura.ciclovida.SenalApagado;
import co.tcc.eventos.infraestructura.kafka.OpcionesKafka;
import co.tcc.eventos.infraestructura.kafka.ProductorKafka;
import co.tcc.eventos.infraestructura.notificaciones.DirectorioContactosSimulado;
import co.tcc.eventos.infraestructura.notificaciones.OpcionesProveedores;
import co.tcc.eventos.infraestructura.notificaciones.ProveedorConCircuito;
import co.tcc.eventos.infraestructura.notificaciones.ProveedorSimulado;
import co.tcc.eventos.infraestructura.postgres.RegistroNotificacionesJdbc;
import co.tcc.eventos.notificador.consumo.ConsumidoresNotificador;
import co.tcc.eventos.notificador.enrutamiento.EnrutadorNotificacionesKafka;
import co.tcc.eventos.notificador.enrutamiento.ManejadorNotificacion;
import co.tcc.eventos.notificador.enrutamiento.OpcionesNotificador;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Raíz de composición del notificador. */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionNotificador {

    @Bean
    @ConfigurationProperties("kafka")
    OpcionesKafka opcionesKafka() {
        return new OpcionesKafka();
    }

    @Bean
    @ConfigurationProperties("notificador")
    OpcionesNotificador opcionesNotificadorConfiguradas() {
        return new OpcionesNotificador();
    }

    @Bean
    @ConfigurationProperties("proveedores")
    OpcionesProveedores opcionesProveedores() {
        return new OpcionesProveedores();
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
    ManejadorNotificacion manejadorNotificacion(
            JdbcClient jdbc, ProductorKafka productor, OpcionesNotificador opcionesNotificadorConfiguradas,
            OpcionesProveedores proveedores, SenalApagado apagado, Clock reloj) {
        var opciones = activas(opcionesNotificadorConfiguradas);

        // Cada proveedor envuelto en su propio circuito: si el SMS cae, el correo sigue.
        var notificar = new NotificarCambioEstado(
                new RegistroNotificacionesJdbc(jdbc),
                new DirectorioContactosSimulado(),
                List.of(
                        new ProveedorConCircuito(new ProveedorSimulado(CanalNotificacion.SMS, proveedores.getSms()), proveedores),
                        new ProveedorConCircuito(new ProveedorSimulado(CanalNotificacion.CORREO, proveedores.getCorreo()), proveedores)));

        return new ManejadorNotificacion(notificar, new EnrutadorNotificacionesKafka(productor, opciones, reloj), opciones, apagado, reloj);
    }

    @Bean
    ConsumidoresNotificador consumidoresNotificador(
            OpcionesKafka kafka, OpcionesNotificador opcionesNotificadorConfiguradas, ManejadorNotificacion manejador, Clock reloj) {
        var opciones = activas(opcionesNotificadorConfiguradas);

        // El grupo lo fija cada contenedor; aquí solo la conexión y las reglas de consumo durables.
        var fabrica = new ConcurrentKafkaListenerContainerFactory<String, String>();
        fabrica.setConsumerFactory(new DefaultKafkaConsumerFactory<>(kafka.propiedadesConsumidor(opciones.getGrupoConsumo())));
        fabrica.getContainerProperties().setAckMode(AckMode.MANUAL);
        // El manejador resuelve todos los errores; si algo escapa, se relee sin límite: nunca se salta un mensaje.
        fabrica.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS)));

        return new ConsumidoresNotificador(fabrica, kafka, opciones, manejador, reloj);
    }

    private static OpcionesNotificador activas(OpcionesNotificador configuradas) {
        var opciones = configuradas.conEtapasActivas();
        if (opciones.getReintentos().isEmpty())
            throw new IllegalStateException("Falta la configuración 'notificador.reintentos'.");
        return opciones;
    }
}
