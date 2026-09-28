package co.tcc.eventos.aplicacion.casosuso;

import co.tcc.eventos.aplicacion.puertos.FiltroDuplicados;
import co.tcc.eventos.aplicacion.puertos.PublicadorEventos;
import co.tcc.eventos.dominio.EventoGuia;

/** Recibe un evento de un sistema origen y lo deja guardado de forma durable. */
public final class RecibirEvento {

    private final PublicadorEventos publicador;
    private final FiltroDuplicados filtro;

    public RecibirEvento(PublicadorEventos publicador, FiltroDuplicados filtro) {
        this.publicador = publicador;
        this.filtro = filtro;
    }

    public ResultadoRecepcion ejecutar(EventoGuia evento) {
        if (filtro.yaRecibido(evento.idEvento()))
            return ResultadoRecepcion.DUPLICADO;

        publicador.publicar(evento);

        // Se marca DESPUÉS de publicar: si la publicación falla,
        // un reintento del emisor no debe ser descartado como duplicado.
        filtro.marcarRecibido(evento.idEvento());

        return ResultadoRecepcion.ACEPTADO;
    }
}
