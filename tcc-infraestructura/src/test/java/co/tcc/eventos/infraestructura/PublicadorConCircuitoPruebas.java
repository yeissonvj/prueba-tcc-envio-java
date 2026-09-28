package co.tcc.eventos.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tcc.eventos.aplicacion.Datos;
import co.tcc.eventos.aplicacion.falsos.PublicadorFalso;
import co.tcc.eventos.aplicacion.puertos.PublicacionFallidaException;
import co.tcc.eventos.aplicacion.puertos.PublicadorEventos;
import co.tcc.eventos.dominio.EventoGuia;
import co.tcc.eventos.infraestructura.kafka.OpcionesKafka;
import co.tcc.eventos.infraestructura.kafka.PublicadorConCircuito;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PublicadorConCircuitoPruebas {

    /** Reloj que solo avanza cuando la prueba lo pide. */
    static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-11-30T15:00:00Z");

        void avanzar(Duration tiempo) {
            ahora = ahora.plus(tiempo);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    /** Cuenta los intentos que realmente llegan al publicador. */
    static final class Contador implements PublicadorEventos {
        final PublicadorFalso interno = new PublicadorFalso();
        final AtomicInteger intentos = new AtomicInteger();

        @Override
        public void publicar(EventoGuia evento) {
            intentos.incrementAndGet();
            interno.publicar(evento);
        }
    }

    private final Contador kafka = new Contador();
    private final RelojManual reloj = new RelojManual();
    private final PublicadorConCircuito circuito = new PublicadorConCircuito(kafka, new OpcionesKafka(), reloj);

    @Test
    void tras_fallas_sostenidas_el_circuito_se_abre_y_ya_no_intenta_publicar() {
        abrir();

        assertThatThrownBy(() -> circuito.publicar(Datos.nuevoEvento()))
                .isInstanceOf(PublicacionFallidaException.class)
                .hasMessageContaining("abierto");
        assertThat(kafka.intentos).hasValue(10);
    }

    @Test
    void pasado_el_tiempo_abierto_prueba_de_nuevo_y_si_funciona_se_cierra() {
        abrir();
        kafka.interno.fallar = false;

        reloj.avanzar(Duration.ofSeconds(16));
        circuito.publicar(Datos.nuevoEvento());

        assertThat(circuito.estado()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(kafka.interno.publicados).hasSize(1);
    }

    private void abrir() {
        kafka.interno.fallar = true;
        for (var i = 0; i < 10; i++)
            assertThatThrownBy(() -> circuito.publicar(Datos.nuevoEvento())).isInstanceOf(PublicacionFallidaException.class);
        assertThat(circuito.estado()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
