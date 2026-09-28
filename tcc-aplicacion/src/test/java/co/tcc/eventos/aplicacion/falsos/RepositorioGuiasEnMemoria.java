package co.tcc.eventos.aplicacion.falsos;

import co.tcc.eventos.aplicacion.puertos.RepositorioGuias;
import co.tcc.eventos.dominio.EstadoGuia;
import co.tcc.eventos.dominio.EventoGuia;
import co.tcc.eventos.dominio.Guia;
import co.tcc.eventos.dominio.ResultadoAplicacion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class RepositorioGuiasEnMemoria implements RepositorioGuias {

    public record Registro(EventoGuia evento, ResultadoAplicacion resultado) {
    }

    /** Lo que el repositorio real dejaría en la bandeja de salida. */
    public record Cambio(EstadoGuia anterior, EstadoGuia nuevo, long version) {
    }

    public final Map<String, Guia> guias = new HashMap<>();
    public final List<Registro> historial = new ArrayList<>();
    public final List<Cambio> cambios = new ArrayList<>();

    @Override
    public boolean existeEvento(UUID idEvento) {
        return historial.stream().anyMatch(h -> h.evento().idEvento().equals(idEvento));
    }

    @Override
    public Optional<Guia> obtener(String numeroGuia) {
        return Optional.ofNullable(guias.get(numeroGuia));
    }

    @Override
    public void guardar(Guia guia, EventoGuia evento, ResultadoAplicacion resultado, EstadoGuia estadoAnterior) {
        historial.add(new Registro(evento, resultado));
        if (resultado == ResultadoAplicacion.APLICADO) {
            guias.put(guia.numeroGuia(), guia);
            cambios.add(new Cambio(estadoAnterior, guia.estadoActual(), guia.version()));
        }
    }
}
