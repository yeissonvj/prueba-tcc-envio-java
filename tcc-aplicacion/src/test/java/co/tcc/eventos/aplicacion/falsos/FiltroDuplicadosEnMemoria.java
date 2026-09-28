package co.tcc.eventos.aplicacion.falsos;

import co.tcc.eventos.aplicacion.puertos.FiltroDuplicados;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FiltroDuplicadosEnMemoria implements FiltroDuplicados {

    private final Set<UUID> recibidos = ConcurrentHashMap.newKeySet();

    @Override
    public boolean yaRecibido(UUID idEvento) {
        return recibidos.contains(idEvento);
    }

    @Override
    public void marcarRecibido(UUID idEvento) {
        recibidos.add(idEvento);
    }
}
