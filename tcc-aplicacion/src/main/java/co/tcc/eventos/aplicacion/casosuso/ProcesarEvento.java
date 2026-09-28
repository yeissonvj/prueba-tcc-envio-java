package co.tcc.eventos.aplicacion.casosuso;

import co.tcc.eventos.aplicacion.puertos.RepositorioGuias;
import co.tcc.eventos.dominio.EstadoGuia;
import co.tcc.eventos.dominio.EventoGuia;
import co.tcc.eventos.dominio.Guia;
import co.tcc.eventos.dominio.ResultadoAplicacion;

/** Aplica un evento a su guía y guarda el resultado. Lo usa el procesador de estado. */
public final class ProcesarEvento {

    private final RepositorioGuias repositorio;

    public ProcesarEvento(RepositorioGuias repositorio) {
        this.repositorio = repositorio;
    }

    public ResultadoProcesamiento ejecutar(EventoGuia evento) {
        if (repositorio.existeEvento(evento.idEvento()))
            return ResultadoProcesamiento.DUPLICADO;

        var existente = repositorio.obtener(evento.numeroGuia());
        EstadoGuia estadoAnterior = existente.map(Guia::estadoActual).orElse(null);

        Guia guia;
        ResultadoAplicacion resultado;
        if (existente.isEmpty()) {
            guia = Guia.crear(evento);
            resultado = ResultadoAplicacion.APLICADO;
        } else {
            guia = existente.get();
            resultado = guia.aplicar(evento);
        }

        repositorio.guardar(guia, evento, resultado, estadoAnterior);

        return aResultadoProcesamiento(resultado);
    }

    // Conversión explícita y exhaustiva: no depende del orden de los valores de los dos enums.
    private static ResultadoProcesamiento aResultadoProcesamiento(ResultadoAplicacion resultado) {
        return switch (resultado) {
            case APLICADO -> ResultadoProcesamiento.APLICADO;
            case TARDIO -> ResultadoProcesamiento.TARDIO;
            case TRANSICION_INVALIDA -> ResultadoProcesamiento.TRANSICION_INVALIDA;
        };
    }
}
