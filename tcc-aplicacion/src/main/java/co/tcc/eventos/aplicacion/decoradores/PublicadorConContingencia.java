package co.tcc.eventos.aplicacion.decoradores;

import co.tcc.eventos.aplicacion.puertos.AlmacenContingencia;
import co.tcc.eventos.aplicacion.puertos.PublicacionFallidaException;
import co.tcc.eventos.aplicacion.puertos.PublicadorEventos;
import co.tcc.eventos.dominio.EventoGuia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Patrón Decorador: si el publicador principal no confirma, el evento se guarda en la contingencia.
 * Regla de oro: nunca 202 sin almacenamiento durable; si ninguno lo guarda, se propaga la falla (→ 503).
 */
public final class PublicadorConContingencia implements PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorConContingencia.class);

    private final PublicadorEventos principal;
    private final AlmacenContingencia contingencia;

    public PublicadorConContingencia(PublicadorEventos principal, AlmacenContingencia contingencia) {
        this.principal = principal;
        this.contingencia = contingencia;
    }

    @Override
    public void publicar(EventoGuia evento) {
        try {
            principal.publicar(evento);
            return;
        } catch (PublicacionFallidaException ex) {
            // Debug y no Warning: durante una caída serían miles por segundo.
            // La caída en sí la reporta el circuito al abrirse.
            log.debug("Publicador principal no disponible; {} va a contingencia", evento.idEvento(), ex);
        }

        try {
            contingencia.guardar(evento);
        } catch (RuntimeException ex) {
            throw new PublicacionFallidaException(
                    "Ni el publicador principal ni la contingencia guardaron el evento " + evento.idEvento() + ".", ex);
        }
    }
}
