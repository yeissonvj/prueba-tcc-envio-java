package co.tcc.eventos.procesador.consumo;

import co.tcc.eventos.aplicacion.casosuso.ProcesarEvento;
import co.tcc.eventos.aplicacion.casosuso.ResultadoProcesamiento;
import co.tcc.eventos.infraestructura.kafka.MensajeKafka;
import co.tcc.eventos.infraestructura.kafka.MensajeRechazado;
import co.tcc.eventos.infraestructura.observabilidad.Telemetria;
import co.tcc.eventos.infraestructura.resiliencia.ClasificadorErrores;
import co.tcc.eventos.infraestructura.ciclovida.SenalApagado;
import co.tcc.eventos.infraestructura.ciclovida.SenalApagado.ServicioDeteniendoseException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Política de procesamiento de un mensaje, sin conocer Kafka:
 * <ul>
 *   <li>ilegible (poison pill) → DLQ de inmediato; la partición sigue.</li>
 *   <li>error transitorio → reintento BLOQUEANTE sin límite (no se salta nada: se respeta el orden).</li>
 *   <li>error inesperado (p. ej. un bug) → pocos reintentos y DLQ, para no congelar la partición.</li>
 * </ul>
 * Solo retorna cuando el mensaje quedó procesado o en la DLQ: entonces es seguro avanzar el offset.
 */
public final class ManejadorMensajeRecibido {

    private static final Logger log = LoggerFactory.getLogger(ManejadorMensajeRecibido.class);

    private final ProcesarEvento procesarEvento;
    private final DestinoDlq dlq;
    private final OpcionesConsumidor opciones;
    private final SenalApagado apagado;
    private final Clock reloj;

    public ManejadorMensajeRecibido(
            ProcesarEvento procesarEvento, DestinoDlq dlq, OpcionesConsumidor opciones, SenalApagado apagado, Clock reloj) {
        this.procesarEvento = procesarEvento;
        this.dlq = dlq;
        this.opciones = opciones;
        this.apagado = apagado;
        this.reloj = reloj;
    }

    public void manejar(MensajeKafka mensaje) {
        var lectura = LectorMensajeRecibido.leer(mensaje.valor());
        if (lectura.evento() == null) {
            log.warn("Mensaje ilegible en {}[{}]@{} va a DLQ: {}",
                    mensaje.topico(), mensaje.particion(), mensaje.offset(), lectura.motivo());
            enviarADlq(new MensajeRechazado(mensaje, lectura.motivo()));
            return;
        }

        var evento = lectura.evento();
        var erroresInesperados = 0;
        for (var intento = 1; ; intento++) {
            try {
                var resultado = procesarEvento.ejecutar(evento);
                log.debug("Evento {} de la guía {}: {}", evento.idEvento(), evento.numeroGuia(), resultado);

                Telemetria.EVENTOS_PROCESADOS.add(1, Telemetria.etiqueta("resultado", Telemetria.texto(resultado)));
                if (resultado == ResultadoProcesamiento.APLICADO && mensaje.marca() != null)
                    Telemetria.LATENCIA_ESTADO.record(segundosDesde(mensaje.marca()));
                return;
            } catch (ServicioDeteniendoseException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                if (!ClasificadorErrores.esTransitorio(ex) && ++erroresInesperados >= opciones.getIntentosErrorInesperado()) {
                    log.error("Evento {} de la guía {} va a DLQ tras {} errores inesperados",
                            evento.idEvento(), evento.numeroGuia(), erroresInesperados, ex);
                    enviarADlq(new MensajeRechazado(mensaje,
                            "Error no transitorio: " + ex.getClass().getSimpleName() + ": " + ex.getMessage()));
                    return;
                }

                var espera = espera(intento);
                log.warn("Error procesando {} de la guía {} (intento {}); reintento bloqueante en {} ms",
                        evento.idEvento(), evento.numeroGuia(), intento, espera.toMillis(), ex);
                apagado.esperar(espera);
            }
        }
    }

    // Si la DLQ no confirma, tampoco se puede avanzar: se reintenta hasta que la guarde.
    private void enviarADlq(MensajeRechazado rechazado) {
        for (var intento = 1; ; intento++) {
            try {
                dlq.enviar(rechazado);
                Telemetria.MENSAJES_DLQ.add(1, Telemetria.etiqueta("origen", "procesador"));
                return;
            } catch (RuntimeException ex) {
                log.warn("No se pudo enviar a la DLQ (intento {})", intento, ex);
                apagado.esperar(espera(intento));
            }
        }
    }

    /**
     * Exponencial con tope y un 20 % de variación aleatoria (jitter): si muchas particiones fallan a la vez,
     * no reintentan todas en el mismo instante contra la base que se está recuperando.
     */
    private Duration espera(int intento) {
        var exponencial = opciones.getEsperaMinimaMs() * Math.pow(2, Math.min(intento - 1, 20));
        var conVariacion = exponencial * (1 + ThreadLocalRandom.current().nextDouble() * 0.2);
        return Duration.ofMillis((long) Math.min(opciones.getEsperaMaximaMs(), conVariacion));
    }

    private double segundosDesde(OffsetDateTime desde) {
        return Duration.between(desde.toInstant(), reloj.instant()).toNanos() / 1e9;
    }
}
