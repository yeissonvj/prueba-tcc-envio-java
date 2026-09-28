package co.tcc.eventos.api.configuracion;

import co.tcc.eventos.api.salud.Sonda;
import co.tcc.eventos.api.salud.SondaKafka;
import co.tcc.eventos.api.salud.SondaPostgres;
import co.tcc.eventos.api.salud.SondaRedis;
import co.tcc.eventos.api.salud.VerificacionesSalud;
import co.tcc.eventos.api.seguridad.AutorizadorOrigen;
import co.tcc.eventos.api.seguridad.OpcionesSeguridad;
import co.tcc.eventos.api.trabajos.ServicioRelayContingencia;
import co.tcc.eventos.api.validacion.ValidadorEventoGuiaV1;
import co.tcc.eventos.aplicacion.casosuso.RecibirEvento;
import co.tcc.eventos.aplicacion.casosuso.ReenviarContingencia;
import co.tcc.eventos.aplicacion.decoradores.FiltroDuplicadosTolerante;
import co.tcc.eventos.aplicacion.decoradores.PublicadorConContingencia;
import co.tcc.eventos.aplicacion.puertos.AlmacenContingencia;
import co.tcc.eventos.aplicacion.puertos.FiltroDuplicados;
import co.tcc.eventos.aplicacion.puertos.PublicadorEventos;
import co.tcc.eventos.infraestructura.consultas.ConsultaGuias;
import co.tcc.eventos.infraestructura.consultas.ConsultaGuiasJdbc;
import co.tcc.eventos.infraestructura.kafka.OpcionesKafka;
import co.tcc.eventos.infraestructura.kafka.ProductorKafka;
import co.tcc.eventos.infraestructura.kafka.PublicadorConCircuito;
import co.tcc.eventos.infraestructura.kafka.PublicadorKafka;
import co.tcc.eventos.infraestructura.postgres.AlmacenContingenciaJdbc;
import co.tcc.eventos.infraestructura.redis.FiltroDuplicadosRedis;
import co.tcc.eventos.infraestructura.redis.OpcionesRedis;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Raíz de composición de la API: el único lugar donde se decide qué adaptador implementa cada puerto
 * y en qué orden se envuelven los decoradores. El orden queda a la vista:
 * <pre>
 *   RecibirEvento → Contingencia( Circuito( Kafka ) ) + Tolerante( Redis )
 *   Relay         → Circuito( Kafka ) directo (sin contingencia: si no, un fallo volvería a la tabla)
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ConfiguracionApi {

    @Bean
    @ConfigurationProperties("kafka")
    OpcionesKafka opcionesKafka() {
        return new OpcionesKafka();
    }

    @Bean
    @ConfigurationProperties("redis")
    OpcionesRedis opcionesRedis() {
        return new OpcionesRedis();
    }

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    // ---------- Publicación ----------

    @Bean(destroyMethod = "close")
    ProductorKafka productorKafka(OpcionesKafka kafka) {
        return new ProductorKafka(kafka);
    }

    @Bean
    PublicadorConCircuito publicadorKafkaConCircuito(ProductorKafka productor, OpcionesKafka kafka) {
        return new PublicadorConCircuito(new PublicadorKafka(productor, kafka), kafka);
    }

    @Bean
    AlmacenContingencia almacenContingencia(JdbcClient jdbc, TransactionTemplate transaccion) {
        return new AlmacenContingenciaJdbc(jdbc, transaccion);
    }

    /** Lo que usa la recepción: nunca 202 sin almacenamiento durable. */
    @Bean
    PublicadorEventos publicadorEventos(PublicadorConCircuito kafka, AlmacenContingencia contingencia) {
        return new PublicadorConContingencia(kafka, contingencia);
    }

    @Bean
    FiltroDuplicados filtroDuplicados(StringRedisTemplate redis, OpcionesRedis opciones) {
        return new FiltroDuplicadosTolerante(new FiltroDuplicadosRedis(redis, opciones)); // Redis caído ≠ 503
    }

    @Bean
    RecibirEvento recibirEvento(PublicadorEventos publicadorEventos, FiltroDuplicados filtroDuplicados) {
        return new RecibirEvento(publicadorEventos, filtroDuplicados);
    }

    @Bean
    @ConditionalOnProperty(name = "api.relay-contingencia-habilitado", havingValue = "true", matchIfMissing = true)
    ServicioRelayContingencia servicioRelayContingencia(AlmacenContingencia almacen, PublicadorConCircuito kafka) {
        return new ServicioRelayContingencia(new ReenviarContingencia(almacen, kafka));
    }

    // ---------- Consulta, validación y autorización ----------

    @Bean
    ConsultaGuias consultaGuias(JdbcClient jdbc) {
        return new ConsultaGuiasJdbc(jdbc);
    }

    @Bean
    ValidadorEventoGuiaV1 validadorEventoGuiaV1(Clock reloj) {
        return new ValidadorEventoGuiaV1(reloj);
    }

    @Bean
    AutorizadorOrigen autorizadorOrigen(OpcionesSeguridad opciones) {
        return new AutorizadorOrigen(opciones);
    }

    // ---------- Salud ----------

    @Bean(destroyMethod = "close")
    SondaKafka sondaKafka(OpcionesKafka kafka) {
        return new SondaKafka(kafka);
    }

    @Bean
    Sonda sondaContingencia(JdbcClient jdbc) {
        return new SondaPostgres(jdbc);
    }

    @Bean
    Sonda sondaFiltro(StringRedisTemplate redis) {
        return new SondaRedis(redis);
    }

    @Bean
    VerificacionesSalud verificacionesSalud(
            @Qualifier("sondaKafka") Sonda kafka,
            @Qualifier("sondaContingencia") Sonda contingencia,
            @Qualifier("sondaFiltro") Sonda filtro) {
        return new VerificacionesSalud(kafka, contingencia, filtro);
    }
}
