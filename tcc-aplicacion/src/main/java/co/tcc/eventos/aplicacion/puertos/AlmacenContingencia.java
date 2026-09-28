package co.tcc.eventos.aplicacion.puertos;

import co.tcc.eventos.dominio.EventoGuia;

/** Almacén durable para cuando el broker no confirma. Debe tolerar varias instancias en paralelo. */
public interface AlmacenContingencia {

    /** Solo termina con éxito si el evento quedó guardado. Idempotente por idEvento. */
    void guardar(EventoGuia evento);

    /**
     * Entrega hasta {@code maximo} pendientes, en orden de llegada, a {@code publicador}
     * y elimina los que se publicaron; los que fallan quedan para el siguiente ciclo.
     *
     * @return cuántos se reenviaron
     */
    int reenviarPendientes(PublicadorEventos publicador, int maximo);
}
