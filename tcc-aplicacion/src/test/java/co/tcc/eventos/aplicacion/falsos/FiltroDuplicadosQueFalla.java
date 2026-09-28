package co.tcc.eventos.aplicacion.falsos;

import co.tcc.eventos.aplicacion.puertos.FiltroDuplicados;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.util.UUID;

public class FiltroDuplicadosQueFalla implements FiltroDuplicados {

    @Override
    public boolean yaRecibido(UUID idEvento) {
        throw new UncheckedIOException(new SocketTimeoutException("Redis no responde"));
    }

    @Override
    public void marcarRecibido(UUID idEvento) {
        throw new UncheckedIOException(new SocketTimeoutException("Redis no responde"));
    }
}
