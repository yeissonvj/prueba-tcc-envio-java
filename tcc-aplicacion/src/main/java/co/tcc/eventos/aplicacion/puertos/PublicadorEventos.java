package co.tcc.eventos.aplicacion.puertos;

import co.tcc.eventos.dominio.EventoGuia;

/**
 * Publica eventos para su procesamiento posterior.
 * Contrato: solo termina con éxito cuando el evento quedó guardado de forma durable.
 * Si no puede garantizarlo, lanza {@link PublicacionFallidaException}.
 */
@FunctionalInterface
public interface PublicadorEventos {

    void publicar(EventoGuia evento);
}
