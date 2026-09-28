package co.tcc.eventos.api.trabajos;

import co.tcc.eventos.aplicacion.casosuso.ReenviarContingencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Cada pocos segundos vacía la contingencia hacia Kafka. Corre en todas las instancias de la API;
 * el bloqueo SKIP LOCKED del almacén evita que dos instancias reenvíen la misma fila.
 */
public final class ServicioRelayContingencia {

    public static final int TAMANO_LOTE = 500;

    private static final Logger log = LoggerFactory.getLogger(ServicioRelayContingencia.class);

    private final ReenviarContingencia reenviar;

    public ServicioRelayContingencia(ReenviarContingencia reenviar) {
        this.reenviar = reenviar;
    }

    @Scheduled(fixedDelayString = "${api.intervalo-relay-contingencia:5s}", initialDelayString = "${api.intervalo-relay-contingencia:5s}")
    public void reenviar() {
        try {
            int reenviados;
            do {
                reenviados = reenviar.ejecutar(TAMANO_LOTE);
                if (reenviados > 0)
                    log.info("Relay: {} eventos reenviados desde contingencia a Kafka", reenviados);
            } while (reenviados == TAMANO_LOTE); // lote lleno: probablemente hay más, seguir sin esperar
        } catch (RuntimeException ex) {
            // PostgreSQL caído o similar: se reintenta en el próximo ciclo; el servicio no muere.
            log.warn("Relay de contingencia falló; se reintenta en el próximo ciclo", ex);
        }
    }
}
