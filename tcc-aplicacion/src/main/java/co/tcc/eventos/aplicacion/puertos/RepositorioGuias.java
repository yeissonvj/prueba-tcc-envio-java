package co.tcc.eventos.aplicacion.puertos;

import co.tcc.eventos.dominio.EstadoGuia;
import co.tcc.eventos.dominio.EventoGuia;
import co.tcc.eventos.dominio.Guia;
import co.tcc.eventos.dominio.ResultadoAplicacion;
import java.util.Optional;
import java.util.UUID;

/** Guarda y consulta guías y su historial de eventos. */
public interface RepositorioGuias {

    boolean existeEvento(UUID idEvento);

    Optional<Guia> obtener(String numeroGuia);

    /**
     * En una sola transacción: registra el evento en el historial y,
     * si fue aplicado, guarda el nuevo estado y deja el cambio listo para publicarse.
     *
     * @param estadoAnterior null cuando el evento creó la guía
     * @throws ConflictoConcurrenciaException si otra instancia escribió la guía o el evento
     *                                        entre la lectura y la escritura
     */
    void guardar(Guia guia, EventoGuia evento, ResultadoAplicacion resultado, EstadoGuia estadoAnterior);
}
