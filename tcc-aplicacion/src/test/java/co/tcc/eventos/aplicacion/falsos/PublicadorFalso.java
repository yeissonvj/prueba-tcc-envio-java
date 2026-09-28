package co.tcc.eventos.aplicacion.falsos;

import co.tcc.eventos.aplicacion.puertos.PublicacionFallidaException;
import co.tcc.eventos.aplicacion.puertos.PublicadorEventos;
import co.tcc.eventos.dominio.EventoGuia;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PublicadorFalso implements PublicadorEventos {

    public final List<EventoGuia> publicados = new CopyOnWriteArrayList<>();
    public volatile boolean fallar;

    @Override
    public void publicar(EventoGuia evento) {
        if (fallar)
            throw new PublicacionFallidaException("Broker no disponible");
        publicados.add(evento);
    }
}
