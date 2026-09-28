package co.tcc.eventos.procesador.falsos;

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
import java.util.function.Supplier;

/** Repositorio en memoria al que se le puede ordenar fallar las próximas N escrituras con una excepción dada. */
public class RepositorioGuiasProgramable implements RepositorioGuias {

    private final Map<String, Guia> guias = new HashMap<>();
    public final List<EventoGuia> guardados = new ArrayList<>();
    public int intentosDeGuardar;

    private int fallasPendientes;
    private Supplier<RuntimeException> falla;

    public void fallarProximas(int veces, Supplier<RuntimeException> falla) {
        this.fallasPendientes = veces;
        this.falla = falla;
    }

    @Override
    public boolean existeEvento(UUID idEvento) {
        return guardados.stream().anyMatch(e -> e.idEvento().equals(idEvento));
    }

    @Override
    public Optional<Guia> obtener(String numeroGuia) {
        return Optional.ofNullable(guias.get(numeroGuia));
    }

    @Override
    public void guardar(Guia guia, EventoGuia evento, ResultadoAplicacion resultado, EstadoGuia estadoAnterior) {
        intentosDeGuardar++;
        if (fallasPendientes > 0) {
            fallasPendientes--;
            throw falla.get();
        }
        guardados.add(evento);
        if (resultado == ResultadoAplicacion.APLICADO)
            guias.put(guia.numeroGuia(), guia);
    }
}
