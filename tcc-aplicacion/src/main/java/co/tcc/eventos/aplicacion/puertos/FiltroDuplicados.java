package co.tcc.eventos.aplicacion.puertos;

import java.util.UUID;

/**
 * Detecta rápido eventos ya recibidos. Es una optimización, no la garantía:
 * si falla o expira, el procesador sigue deduplicando.
 */
public interface FiltroDuplicados {

    boolean yaRecibido(UUID idEvento);

    void marcarRecibido(UUID idEvento);
}
