package co.tcc.eventos.procesador.consumo;

import co.tcc.eventos.infraestructura.postgres.RelayBandejaSalida;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Vacía la bandeja de salida hacia guias.estados.cambiados. Intervalo corto porque es parte
 * de la latencia "escaneo → cliente notificado". Si otra instancia tiene el candado, esta no hace nada.
 */
public final class ServicioRelayBandejaSalida {

    public static final int TAMANO_LOTE = 500;

    private static final Logger log = LoggerFactory.getLogger(ServicioRelayBandejaSalida.class);

    private final RelayBandejaSalida relay;

    public ServicioRelayBandejaSalida(RelayBandejaSalida relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${procesador.intervalo-relay:250ms}")
    public void publicar() {
        try {
            int publicados;
            do {
                publicados = relay.publicarPendientes(TAMANO_LOTE);
                if (publicados > 0)
                    log.debug("Bandeja de salida: {} cambios publicados", publicados);
            } while (publicados == TAMANO_LOTE); // lote lleno: probablemente hay más, seguir sin esperar
        } catch (RuntimeException ex) {
            log.warn("Relay de bandeja de salida falló; se reintenta en el siguiente ciclo", ex);
        }
    }
}
