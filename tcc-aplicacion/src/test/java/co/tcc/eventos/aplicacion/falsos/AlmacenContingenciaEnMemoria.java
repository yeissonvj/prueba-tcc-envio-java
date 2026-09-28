package co.tcc.eventos.aplicacion.falsos;

import co.tcc.eventos.aplicacion.puertos.AlmacenContingencia;
import co.tcc.eventos.aplicacion.puertos.PublicacionFallidaException;
import co.tcc.eventos.aplicacion.puertos.PublicadorEventos;
import co.tcc.eventos.dominio.EventoGuia;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

public class AlmacenContingenciaEnMemoria implements AlmacenContingencia {

    public final List<EventoGuia> pendientes = new ArrayList<>();
    public boolean fallar;

    @Override
    public synchronized void guardar(EventoGuia evento) {
        if (fallar)
            throw new UncheckedIOException(new SocketTimeoutException("PostgreSQL no responde"));
        if (pendientes.stream().noneMatch(e -> e.idEvento().equals(evento.idEvento())))
            pendientes.add(evento);
    }

    @Override
    public synchronized int reenviarPendientes(PublicadorEventos publicador, int maximo) {
        var publicados = new ArrayList<EventoGuia>();
        for (var evento : List.copyOf(pendientes.subList(0, Math.min(maximo, pendientes.size())))) {
            try {
                publicador.publicar(evento);
                publicados.add(evento);
            } catch (PublicacionFallidaException ignorada) {
                // Queda para el siguiente ciclo.
            }
        }
        pendientes.removeAll(publicados);
        return publicados.size();
    }
}
