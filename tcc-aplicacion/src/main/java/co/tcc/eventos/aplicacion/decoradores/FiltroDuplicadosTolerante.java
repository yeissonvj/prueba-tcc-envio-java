package co.tcc.eventos.aplicacion.decoradores;

import co.tcc.eventos.aplicacion.puertos.FiltroDuplicados;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Patrón Decorador: envuelve cualquier {@link FiltroDuplicados} y, si falla, la recepción continúa.
 * El filtro es una optimización; la garantía de no duplicar está en el inbox del procesador.
 */
public final class FiltroDuplicadosTolerante implements FiltroDuplicados {

    private static final Logger log = LoggerFactory.getLogger(FiltroDuplicadosTolerante.class);

    private final FiltroDuplicados interno;

    public FiltroDuplicadosTolerante(FiltroDuplicados interno) {
        this.interno = interno;
    }

    @Override
    public boolean yaRecibido(UUID idEvento) {
        try {
            return interno.yaRecibido(idEvento);
        } catch (RuntimeException ex) {
            // Ante la duda, "no recibido": se publica de nuevo y el procesador deduplica.
            // Responder "recibido" sin estar seguros podría PERDER el evento.
            log.warn("Filtro de duplicados no disponible al consultar {}; se continúa sin filtro", idEvento, ex);
            return false;
        }
    }

    @Override
    public void marcarRecibido(UUID idEvento) {
        try {
            interno.marcarRecibido(idEvento);
        } catch (RuntimeException ex) {
            // El evento YA es durable en Kafka: no marcarlo solo cuesta un posible duplicado.
            log.warn("No se pudo marcar {} en el filtro de duplicados", idEvento, ex);
        }
    }
}
